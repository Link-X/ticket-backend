package com.ticket.core.mq.producer;

import com.ticket.core.mq.config.RabbitMQConfig;
import com.ticket.core.mq.event.PaymentSuccessEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentSuccessProducer {

    private final RabbitTemplate rabbitTemplate;

    public PaymentSuccessProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendPaymentSuccess(Long orderId, Long userId, String orderNo, String paymentNo) {
        PaymentSuccessEvent event = new PaymentSuccessEvent(orderId, userId, orderNo, paymentNo);
        rabbitTemplate.convertAndSend(RabbitMQConfig.PAYMENT_SUCCESS_EXCHANGE, "", event);
        log.debug("发送支付成功事件，orderNo={}", orderNo);
    }
}
