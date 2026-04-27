package com.ticket.core.mq.producer;

import com.ticket.core.mq.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderTimeoutProducer {

    private final RabbitTemplate rabbitTemplate;

    public OrderTimeoutProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendTimeoutMessage(Long orderId) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_TIMEOUT_EXCHANGE,
                RabbitMQConfig.ORDER_TIMEOUT_ROUTING_KEY,
                orderId
        );
        log.debug("发送订单超时消息，orderId={}", orderId);
    }
}
