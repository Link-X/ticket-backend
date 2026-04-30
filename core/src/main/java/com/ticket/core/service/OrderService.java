package com.ticket.core.service;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.util.SnowflakeIdGenerator;
import com.ticket.core.domain.dto.OrderCreateRequest;
import com.ticket.core.domain.dto.OrderStatusResponse;
import com.ticket.core.domain.entity.Order;
import com.ticket.core.domain.entity.OrderItem;
import com.ticket.core.domain.entity.Seat;
import com.ticket.core.domain.entity.Show;
import com.ticket.core.domain.entity.ShowSession;
import com.ticket.core.domain.entity.Ticket;
import com.ticket.core.mapper.OrderItemMapper;
import com.ticket.core.mapper.OrderMapper;
import com.ticket.core.mapper.SeatMapper;
import com.ticket.core.mapper.ShowMapper;
import com.ticket.core.mapper.ShowSessionMapper;
import com.ticket.core.mapper.TicketMapper;
import com.ticket.core.mq.producer.OrderTimeoutProducer;
import com.ticket.core.mq.producer.RefundProducer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订单服务 — 负责创建订单、取消订单及查询订单信息
 */
@Service
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final SeatInventoryService inventoryService;
    private final PurchaseLimitService purchaseLimitService;
    private final SeatMapper seatMapper;
    private final ShowMapper showMapper;
    private final ShowSessionMapper showSessionMapper;
    private final OrderTimeoutProducer orderTimeoutProducer;
    private final RefundProducer refundProducer;
    private final TicketMapper ticketMapper;
    private final SnowflakeIdGenerator snowflake = new SnowflakeIdGenerator(1, 1);

    public OrderService(OrderMapper orderMapper,
                        OrderItemMapper orderItemMapper,
                        SeatInventoryService inventoryService,
                        PurchaseLimitService purchaseLimitService,
                        SeatMapper seatMapper,
                        ShowMapper showMapper,
                        ShowSessionMapper showSessionMapper,
                        OrderTimeoutProducer orderTimeoutProducer,
                        RefundProducer refundProducer,
                        TicketMapper ticketMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.inventoryService = inventoryService;
        this.purchaseLimitService = purchaseLimitService;
        this.seatMapper = seatMapper;
        this.showMapper = showMapper;
        this.showSessionMapper = showSessionMapper;
        this.orderTimeoutProducer = orderTimeoutProducer;
        this.refundProducer = refundProducer;
        this.ticketMapper = ticketMapper;
    }

    /**
     * 创建订单
     *
     * @param request 订单创建请求（userId、sessionId、seatIds）
     * @return 创建成功的 Order 对象
     */
    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(OrderCreateRequest request) {
        Long sessionId = request.getSessionId();
        Long userId = request.getUserId();
        List<Long> seatIds = request.getSeatIds();

        // 1. 超卖兜底：检查每个座位是否已存在有效订单
        for (Long seatId : seatIds) {
            int count = orderItemMapper.countBySeatIdAndValidOrder(seatId);
            if (count > 0) {
                for (Long id : seatIds) {
                    inventoryService.releaseSeat(sessionId, id);
                }
                purchaseLimitService.decrement(sessionId, userId, seatIds.size());
                throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
            }
        }

        // 2. 从 DB 加载座位信息，校验情侣连座完整性
        List<Seat> seatList = seatMapper.selectByIds(seatIds);
        if (seatList.size() != seatIds.size()) {
            for (Long id : seatIds) inventoryService.releaseSeat(sessionId, id);
            purchaseLimitService.decrement(sessionId, userId, seatIds.size());
            throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
        }

        Set<Long> seatIdSet = new HashSet<>(seatIds);
        for (Seat seat : seatList) {
            // type=2（情侣左）或 type=3（情侣右）必须与配对座位同时出现在本次下单中
            if (seat.getType() == 2 || seat.getType() == 3) {
                if (seat.getPairSeatId() == null || !seatIdSet.contains(seat.getPairSeatId())) {
                    for (Long id : seatIds) inventoryService.releaseSeat(sessionId, id);
                    purchaseLimitService.decrement(sessionId, userId, seatIds.size());
                    throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
                }
            }
        }

        // 3. 计算总价（从 Redis 区域价格缓存取，Redis miss 则释放并抛出异常）
        Map<Long, Seat> seatMap = seatList.stream()
                .collect(Collectors.toMap(Seat::getId, s -> s));

        List<OrderItem> items = new ArrayList<>();
        for (Long seatId : seatIds) {
            Seat seat = seatMap.get(seatId);
            String priceStr = inventoryService.getAreaPrice(sessionId, seat.getAreaId());
            if (priceStr == null) {
                for (Long id : seatIds) inventoryService.releaseSeat(sessionId, id);
                purchaseLimitService.decrement(sessionId, userId, seatIds.size());
                throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
            }
            BigDecimal price = new BigDecimal(priceStr);
            OrderItem item = new OrderItem();
            item.setOrderId(0L);
            item.setSeatId(seatId);
            item.setPrice(price);
            item.setSeatInfo(seat.getSeatName() != null ? seat.getSeatName()
                    : "row:" + seat.getRowNo() + ",col:" + seat.getColNo());
            items.add(item);
        }

        // 4. 计算总金额并创建订单
        BigDecimal totalAmount = items.stream()
                .map(OrderItem::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order();
        order.setOrderNo(String.valueOf(snowflake.nextId()));
        order.setUserId(userId);
        order.setSessionId(sessionId);
        order.setTotalAmount(totalAmount);
        order.setStatus(0);
        order.setExpireTime(LocalDateTime.now().plusMinutes(5));
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        orderMapper.insert(order);

        // 5. 批量插入 OrderItem
        for (OrderItem item : items) {
            item.setOrderId(order.getId());
        }
        orderItemMapper.batchInsert(items);

        // 事务提交后再执行 Redis 消费座位 + 发超时 MQ
        // 确保 DB 回滚时 Redis 不会被修改，避免座位被误标为"已售"
        Long orderId = order.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 6. 消费座位：从可售集合移除 + 删除锁
                for (Long seatId : seatIds) {
                    inventoryService.consumeSeat(sessionId, seatId, String.valueOf(userId));
                }
                orderTimeoutProducer.sendTimeoutMessage(orderId);
            }
        });

        return order;
    }

    /**
     * 取消订单
     *
     * @param orderId 订单 ID
     */
    public void cancelOrder(Long orderId) {
        // 1. 查询订单
        Order order = orderMapper.selectById(orderId);

        // 2. 订单不存在或状态非待支付，直接返回
        if (order == null || order.getStatus() != 0) {
            return;
        }

        // 3. 更新订单状态为已取消（2）；利用 AND status=0 的条件实现乐观锁
        //    若受影响行数为 0，说明其他线程已先完成取消，直接返回避免重复操作
        int affected = orderMapper.updateStatus(orderId, 2);
        if (affected == 0) {
            return;  // 已被其他线程取消
        }

        // 4. 查询订单项并释放座位锁
        List<OrderItem> items = orderItemMapper.selectByOrderId(orderId);
        for (OrderItem item : items) {
            inventoryService.releaseSeat(order.getSessionId(), item.getSeatId());
        }

        // 5. 回滚限购计数（按实际座位数释放）
        purchaseLimitService.decrement(order.getSessionId(), order.getUserId(), items.size());
    }

    /**
     * 发起退款（已支付订单取消）：同步更新状态为退款中，异步通过 MQ 完成后续退款操作
     *
     * @param orderId 订单 ID
     */
    public void initiateRefund(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || order.getStatus() != 1) {
            return;
        }
        // 乐观锁：只有 status=1 才更新为 3（退款中），防止并发重复发起
        int affected = orderMapper.updateStatusFrom(orderId, 1, 3);
        if (affected == 0) {
            return;
        }
        // 立即将座位恢复到 Redis 可售集合，座位图实时生效，无需等待 MQ 异步处理
        List<OrderItem> items = orderItemMapper.selectByOrderId(orderId);
        for (OrderItem item : items) {
            inventoryService.releaseSeat(order.getSessionId(), item.getSeatId());
        }
        refundProducer.sendRefund(orderId);
    }

    /**
     * 根据订单号查询订单
     *
     * @param orderNo 订单号
     * @return Order 对象，不存在返回 null
     */
    public Order getByOrderNo(String orderNo) {
        return orderMapper.selectByOrderNo(orderNo);
    }

    public Order getById(Long orderId) {
        return orderMapper.selectById(orderId);
    }

    /**
     * 查询订单的所有订单项
     *
     * @param orderId 订单 ID
     * @return 订单项列表
     */
    public List<OrderItem> getOrderItems(Long orderId) {
        return orderItemMapper.selectByOrderId(orderId);
    }

    /**
     * 查询用户订单列表（分页）
     *
     * @param userId 用户 ID
     * @param page   页码（从 1 开始）
     * @param size   每页条数
     * @return 订单响应列表
     */
    public List<OrderStatusResponse> getUserOrders(Long userId, int page, int size,
                                                   LocalDateTime startTime, LocalDateTime endTime) {
        int offset = (page - 1) * size;
        List<Order> orders = orderMapper.selectByUserId(userId, offset, size, startTime, endTime);
        return orders.stream().map(this::buildStatusResponse).collect(Collectors.toList());
    }

    /**
     * 统计用户订单总数
     */
    public int countUserOrders(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        return orderMapper.countByUserId(userId, startTime, endTime);
    }

    public OrderStatusResponse buildStatusResponse(Order order) {
        List<OrderItem> items = orderItemMapper.selectByOrderId(order.getId());
        ShowSession session = showSessionMapper.selectById(order.getSessionId());
        Show show = session != null ? showMapper.selectById(session.getShowId()) : null;

        OrderStatusResponse resp = new OrderStatusResponse();
        resp.setOrderId(order.getId());
        resp.setOrderNo(order.getOrderNo());
        resp.setStatus(order.getStatus());
        resp.setTotalAmount(order.getTotalAmount());
        resp.setCreateTime(order.getCreateTime());
        resp.setPayTime(order.getPayTime());
        resp.setExpireTime(order.getExpireTime());
        resp.setSeatInfos(items.stream().map(OrderItem::getSeatInfo).collect(Collectors.toList()));
        if (show != null) {
            resp.setShowName(show.getName());
            resp.setShowVenue(show.getVenue());
        }
        if (session != null) {
            resp.setSessionName(session.getName());
            resp.setSessionStartTime(session.getStartTime());
        }

        List<OrderStatusResponse.TicketInfo> tickets = ticketMapper.selectByOrderId(order.getId())
                .stream()
                .map(t -> {
                    OrderStatusResponse.TicketInfo info = new OrderStatusResponse.TicketInfo();
                    info.setTicketNo(t.getTicketNo());
                    info.setQrCode(t.getQrCode());
                    info.setStatus(t.getStatus());
                    info.setVerifyTime(t.getVerifyTime());
                    return info;
                })
                .collect(Collectors.toList());
        resp.setTickets(tickets);

        return resp;
    }
}
