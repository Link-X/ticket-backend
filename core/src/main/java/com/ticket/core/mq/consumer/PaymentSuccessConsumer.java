package com.ticket.core.mq.consumer;

import com.ticket.core.mq.config.RabbitMQConfig;
import com.ticket.core.mq.event.PaymentSuccessEvent;
import com.ticket.core.service.TicketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentSuccessConsumer {

    private final TicketService ticketService;

    public PaymentSuccessConsumer(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @RabbitListener(queues = RabbitMQConfig.TICKET_GENERATE_QUEUE)
    public void generateTickets(PaymentSuccessEvent event) {
        log.info("生成票券，orderNo={}", event.getOrderNo());
        try {
            ticketService.generateTicketsForOrder(event.getOrderId(), event.getUserId());
        } catch (Exception e) {
            log.error("票券生成失败，orderNo={}", event.getOrderNo(), e);
            throw e;
        }
    }

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void sendNotification(PaymentSuccessEvent event) {
        // 预留：对接短信/推送服务
        log.info("预留通知，orderNo={}，userId={}", event.getOrderNo(), event.getUserId());
    }
}
