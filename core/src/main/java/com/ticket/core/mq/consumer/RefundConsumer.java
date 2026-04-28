package com.ticket.core.mq.consumer;

import com.ticket.core.domain.entity.OrderItem;
import com.ticket.core.mapper.OrderItemMapper;
import com.ticket.core.mapper.OrderMapper;
import com.ticket.core.mapper.PaymentMapper;
import com.ticket.core.mapper.SeatMapper;
import com.ticket.core.mapper.TicketMapper;
import com.ticket.core.mq.config.RabbitMQConfig;
import com.ticket.core.mq.event.RefundEvent;
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
    public void handleRefund(RefundEvent event) {
        log.info("处理退款，orderNo={}", event.getOrderNo());
        try {
            List<OrderItem> items = orderItemMapper.selectByOrderId(event.getOrderId());
            List<Long> seatIds = items.stream().map(OrderItem::getSeatId).collect(Collectors.toList());

            // 1. mock 退款：更新支付记录状态为已退款(2)
            paymentMapper.updateStatusByOrderId(event.getOrderId(), 2);

            // 2. Redis 座位恢复可售
            for (Long seatId : seatIds) {
                inventoryService.releaseSeat(event.getSessionId(), seatId);
            }

            // 3. MySQL 座位状态恢复为可用(0)
            if (!seatIds.isEmpty()) {
                seatMapper.batchUpdateStatus(seatIds, 0);
            }

            // 4. 回滚限购计数
            purchaseLimitService.decrement(event.getSessionId(), event.getUserId());

            // 5. 作废票券
            ticketMapper.invalidateByOrderId(event.getOrderId());

            // 6. 订单状态 退款中(3) → 已退款(4)
            orderMapper.updateStatusFrom(event.getOrderId(), 3, 4);

            log.info("退款完成，orderNo={}", event.getOrderNo());
        } catch (Exception e) {
            log.error("退款处理失败，orderNo={}，原因：{}", event.getOrderNo(), e.getMessage(), e);
            throw e; // 触发 RabbitMQ 重试
        }
    }
}
