package com.ticket.core.mq.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    // 订单超时相关
    public static final String ORDER_TIMEOUT_EXCHANGE    = "order.timeout.exchange";
    public static final String ORDER_TIMEOUT_QUEUE       = "order.timeout.queue";
    public static final String ORDER_DEAD_EXCHANGE       = "order.dead.exchange";
    public static final String ORDER_CANCEL_QUEUE        = "order.cancel.queue";
    public static final String ORDER_TIMEOUT_ROUTING_KEY = "order.timeout";
    public static final String ORDER_CANCEL_ROUTING_KEY  = "order.cancel";

    // 支付成功相关
    public static final String PAYMENT_SUCCESS_EXCHANGE  = "payment.success.exchange";
    public static final String TICKET_GENERATE_QUEUE     = "ticket.generate.queue";
    public static final String INVENTORY_SYNC_QUEUE      = "inventory.sync.queue";
    public static final String NOTIFICATION_QUEUE        = "notification.queue";

    /** 订单超时投递交换机 */
    @Bean
    public DirectExchange orderTimeoutExchange() {
        return new DirectExchange(ORDER_TIMEOUT_EXCHANGE, true, false);
    }

    /** 死信交换机：接收超时的订单消息 */
    @Bean
    public DirectExchange orderDeadExchange() {
        return new DirectExchange(ORDER_DEAD_EXCHANGE, true, false);
    }

    /** 订单超时队列：5 分钟 TTL，到期后转发死信交换机 */
    @Bean
    public Queue orderTimeoutQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", 300_000);                   // 5 分钟
        args.put("x-dead-letter-exchange", ORDER_DEAD_EXCHANGE);
        args.put("x-dead-letter-routing-key", ORDER_CANCEL_ROUTING_KEY);
        return QueueBuilder.durable(ORDER_TIMEOUT_QUEUE).withArguments(args).build();
    }

    /** 订单取消队列：消费死信，执行实际取消逻辑 */
    @Bean
    public Queue orderCancelQueue() {
        return QueueBuilder.durable(ORDER_CANCEL_QUEUE).build();
    }

    @Bean
    public Binding orderTimeoutBinding() {
        return BindingBuilder.bind(orderTimeoutQueue())
                .to(orderTimeoutExchange())
                .with(ORDER_TIMEOUT_ROUTING_KEY);
    }

    @Bean
    public Binding orderCancelBinding() {
        return BindingBuilder.bind(orderCancelQueue())
                .to(orderDeadExchange())
                .with(ORDER_CANCEL_ROUTING_KEY);
    }

    /** 支付成功 Fanout 交换机 */
    @Bean
    public FanoutExchange paymentSuccessExchange() {
        return new FanoutExchange(PAYMENT_SUCCESS_EXCHANGE, true, false);
    }

    @Bean
    public Queue ticketGenerateQueue() {
        return QueueBuilder.durable(TICKET_GENERATE_QUEUE).build();
    }

    @Bean
    public Queue inventorySyncQueue() {
        return QueueBuilder.durable(INVENTORY_SYNC_QUEUE).build();
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE).build();
    }

    @Bean
    public Binding ticketGenerateBinding() {
        return BindingBuilder.bind(ticketGenerateQueue()).to(paymentSuccessExchange());
    }

    @Bean
    public Binding inventorySyncBinding() {
        return BindingBuilder.bind(inventorySyncQueue()).to(paymentSuccessExchange());
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue()).to(paymentSuccessExchange());
    }
}
