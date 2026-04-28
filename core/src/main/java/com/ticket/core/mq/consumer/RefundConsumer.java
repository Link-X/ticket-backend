package com.ticket.core.mq.consumer;

import com.ticket.core.domain.entity.Order;
import com.ticket.core.domain.entity.OrderItem;
import com.ticket.core.mapper.OrderItemMapper;
import com.ticket.core.mapper.OrderMapper;
import com.ticket.core.mapper.PaymentMapper;
import com.ticket.core.mapper.SeatMapper;
import com.ticket.core.mapper.TicketMapper;
import com.ticket.core.mq.config.RabbitMQConfig;
import com.ticket.core.service.PurchaseLimitService;
import com.ticket.core.service.SeatInventoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RefundConsumer {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final PaymentMapper paymentMapper;
    private final SeatMapper seatMapper;
    private final TicketMapper ticketMapper;
    private final SeatInventoryService inventoryService;
    private final PurchaseLimitService purchaseLimitService;

    public RefundConsumer(OrderMapper orderMapper,
                          OrderItemMapper orderItemMapper,
                          PaymentMapper paymentMapper,
                          SeatMapper seatMapper,
                          TicketMapper ticketMapper,
                          SeatInventoryService inventoryService,
                          PurchaseLimitService purchaseLimitService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.paymentMapper = paymentMapper;
        this.seatMapper = seatMapper;
        this.ticketMapper = ticketMapper;
        this.inventoryService = inventoryService;
        this.purchaseLimitService = purchaseLimitService;
    }

    @RabbitListener(queues = RabbitMQConfig.REFUND_QUEUE)
    public void handleRefund(Long orderId) {
        log.info("处理退款，orderId={}", orderId);
        try {
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                log.warn("退款订单不存在，orderId={}", orderId);
                return;
            }

            List<OrderItem> items = orderItemMapper.selectByOrderId(orderId);
            List<Long> seatIds = items.stream().map(OrderItem::getSeatId).collect(Collectors.toList());

            // 1. mock 退款：更新支付记录状态为已退款(2)
            paymentMapper.updateStatusByOrderId(orderId, 2);

            // 2. Redis 座位恢复可售（initiateRefund 已同步执行，此处幂等兜底）
            for (Long seatId : seatIds) {
                inventoryService.releaseSeat(order.getSessionId(), seatId);
            }

            // 3. MySQL 座位状态恢复为可用(0)
            if (!seatIds.isEmpty()) {
                seatMapper.batchUpdateStatus(seatIds, 0);
            }

            // 4. 回滚限购计数
            purchaseLimitService.decrement(order.getSessionId(), order.getUserId(), seatIds.size());

            // 5. 作废票券
            ticketMapper.invalidateByOrderId(orderId);

            // 6. 订单状态 退款中(3) → 已退款(4)
            orderMapper.updateStatusFrom(orderId, 3, 4);

            log.info("退款完成，orderId={}", orderId);
        } catch (Exception e) {
            log.error("退款处理失败，orderId={}，原因：{}", orderId, e.getMessage(), e);
            throw e;
        }
    }
}
