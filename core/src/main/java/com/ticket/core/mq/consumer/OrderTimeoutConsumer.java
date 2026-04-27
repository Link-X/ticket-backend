package com.ticket.core.mq.consumer;

import com.ticket.core.mq.config.RabbitMQConfig;
import com.ticket.core.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderTimeoutConsumer {

    private final OrderService orderService;

    public OrderTimeoutConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_CANCEL_QUEUE)
    public void handleOrderTimeout(Long orderId) {
        log.info("收到订单超时消息，orderId={}", orderId);
        try {
            orderService.cancelOrder(orderId);
        } catch (Exception e) {
            log.error("订单超时取消失败，orderId={}", orderId, e);
            throw e; // 重新抛出，触发 RabbitMQ 重试
        }
    }
}
