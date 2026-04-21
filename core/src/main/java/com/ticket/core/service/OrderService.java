package com.ticket.core.service;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.util.SnowflakeIdGenerator;
import com.ticket.core.domain.dto.OrderCreateRequest;
import com.ticket.core.domain.entity.Order;
import com.ticket.core.domain.entity.OrderItem;
import com.ticket.core.mapper.OrderItemMapper;
import com.ticket.core.mapper.OrderMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 订单服务 — 负责创建订单、取消订单及查询订单信息
 */
@Service
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final SeatInventoryService inventoryService;
    private final PurchaseLimitService purchaseLimitService;
    /** 雪花算法 ID 生成器，workerId=1，dataCenterId=1 */
    private final SnowflakeIdGenerator snowflake = new SnowflakeIdGenerator(1, 1);

    public OrderService(OrderMapper orderMapper,
                        OrderItemMapper orderItemMapper,
                        SeatInventoryService inventoryService,
                        PurchaseLimitService purchaseLimitService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.inventoryService = inventoryService;
        this.purchaseLimitService = purchaseLimitService;
    }

    /**
     * 创建订单
     *
     * @param request 订单创建请求（userId、sessionId、seatIds）
     * @return 创建成功的 Order 对象
     */
    public Order createOrder(OrderCreateRequest request) {
        Long sessionId = request.getSessionId();
        Long userId = request.getUserId();
        List<Long> seatIds = request.getSeatIds();

        // 1. 超卖兜底：检查每个座位是否已存在有效订单
        for (Long seatId : seatIds) {
            int count = orderItemMapper.countBySeatIdAndValidOrder(seatId);
            if (count > 0) {
                //释放当前创建订单选择位置的 Redis 锁
                for (Long id : seatIds) {
                    inventoryService.releaseSeat(sessionId, id);
                }
                // 回滚限购计数
                purchaseLimitService.decrement(sessionId, userId);
                throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
            }
        }

        // 2. 计算总价，构建 OrderItem 列表
        List<OrderItem> items = new ArrayList<>();
        for (Long seatId : seatIds) {
            Map<Object, Object> info = inventoryService.getSeatInfo(seatId);
            // 防御：Redis 中座位信息不存在或 price 字段为空时，释放已锁座位并回滚限购
            if (info == null || info.isEmpty() || info.get("price") == null) {
                for (Long id : seatIds) {
                    inventoryService.releaseSeat(sessionId, id);
                }
                purchaseLimitService.decrement(sessionId, request.getUserId());
                throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
            }
            BigDecimal price = new BigDecimal((String) info.get("price"));
            OrderItem item = new OrderItem();
            item.setOrderId(0L); // 暂占位，后续更新
            item.setSeatId(seatId);
            item.setPrice(price);
            item.setSeatInfo("row:" + info.get("row")
                    + ",col:" + info.get("col")
                    + ",type:" + info.get("type"));
            items.add(item);
        }

        // 3. 计算总金额并创建 Order
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

        // 4. 批量插入 OrderItem（先更新 orderId）
        for (OrderItem item : items) {
            item.setOrderId(order.getId());
        }
        orderItemMapper.batchInsert(items);

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

        // 5. 回滚限购计数
        purchaseLimitService.decrement(order.getSessionId(), order.getUserId());
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

    /**
     * 查询订单的所有订单项
     *
     * @param orderId 订单 ID
     * @return 订单项列表
     */
    public List<OrderItem> getOrderItems(Long orderId) {
        return orderItemMapper.selectByOrderId(orderId);
    }
}
