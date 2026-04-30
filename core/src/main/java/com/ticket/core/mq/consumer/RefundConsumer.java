package com.ticket.core.mq.consumer;

import com.ticket.core.mapper.OrderItemMapper;
import com.ticket.core.mapper.OrderMapper;
import com.ticket.core.mapper.PaymentMapper;
import com.ticket.core.mapper.SeatMapper;
import com.ticket.core.mapper.TicketMapper;
import com.ticket.core.mq.config.RabbitMQConfig;
import com.ticket.core.mq.event.RefundEvent;
import com.ticket.core.service.PurchaseLimitService;
import com.ticket.core.service.SeatInventoryService;
import com.ticket.core.domain.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

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
        Long orderId = event.getOrderId();
        List<Long> refundSeatIds = event.getRefundSeatIds();
        log.info("处理退款，orderId={}，退款座位数={}", orderId, refundSeatIds.size());
        try {
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                log.warn("退款订单不存在，orderId={}", orderId);
                return;
            }

            // 1. 更新支付记录状态为已退款(2)
            paymentMapper.updateStatusByOrderId(orderId, 2);

            // 2. Redis 座位恢复可售（initiateRefund 已同步执行，此处幂等兜底）
            for (Long seatId : refundSeatIds) {
                inventoryService.releaseSeat(order.getSessionId(), seatId);
            }

            // 3. MySQL 退款座位状态恢复为可售(0)
            if (!refundSeatIds.isEmpty()) {
                seatMapper.batchUpdateStatus(refundSeatIds, 0);
            }

            // 4. 回滚限购计数（只按退款座位数回滚）
            purchaseLimitService.decrement(order.getSessionId(), order.getUserId(), refundSeatIds.size());

            // 5. 作废退款座位对应的票券（已核销票券保持原状）
            ticketMapper.invalidateBySeatIds(orderId, refundSeatIds);

            // 6. 判断全退(4)还是部分退款(5)
            int totalItems = orderItemMapper.selectByOrderId(orderId).size();
            int finalStatus = (refundSeatIds.size() == totalItems) ? 4 : 5;
            orderMapper.updateStatusFrom(orderId, 3, finalStatus);

            log.info("退款完成，orderId={}，退款座位={}，最终状态={}", orderId, refundSeatIds, finalStatus);
        } catch (Exception e) {
            log.error("退款处理失败，orderId={}，原因：{}", orderId, e.getMessage(), e);
            throw e;
        }
    }
}
