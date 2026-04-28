package com.ticket.core.mq.producer;

import com.ticket.core.mq.config.RabbitMQConfig;
import com.ticket.core.mq.event.RefundEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RefundProducer {

    private final RabbitTemplate rabbitTemplate;

    public RefundProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendRefund(Long orderId, Long userId, Long sessionId, String orderNo) {
        RefundEvent event = new RefundEvent(orderId, userId, sessionId, orderNo);
        rabbitTemplate.convertAndSend(RabbitMQConfig.REFUND_EXCHANGE, RabbitMQConfig.REFUND_ROUTING_KEY, event);
        log.debug("发送退款消息，orderNo={}", orderNo);
    }
}
