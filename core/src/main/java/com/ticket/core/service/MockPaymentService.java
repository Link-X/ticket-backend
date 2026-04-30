package com.ticket.core.service;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.util.SnowflakeIdGenerator;
import com.ticket.core.domain.entity.Order;
import com.ticket.core.domain.entity.Payment;
import com.ticket.core.mapper.OrderMapper;
import com.ticket.core.mapper.PaymentMapper;
import com.ticket.core.mq.producer.PaymentSuccessProducer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Service
public class MockPaymentService {

    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final PaymentSuccessProducer paymentSuccessProducer;

    private final SnowflakeIdGenerator snowflake = new SnowflakeIdGenerator(1, 2);

    public MockPaymentService(OrderMapper orderMapper,
                              PaymentMapper paymentMapper,
                              PaymentSuccessProducer paymentSuccessProducer) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.paymentSuccessProducer = paymentSuccessProducer;
    }

    @Transactional(rollbackFor = Exception.class)
    public Payment pay(String orderNo, Long userId, String channel) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (order.getStatus() != 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "订单状态不允许支付");
        }

        LocalDateTime now = LocalDateTime.now();
        Payment payment = new Payment();
        payment.setId(snowflake.nextId());
        payment.setOrderId(order.getId());
        payment.setPaymentNo(String.valueOf(snowflake.nextId()));
        payment.setChannel(channel != null ? channel : "mock");
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(1);
        payment.setTradeNo("MOCK-" + System.currentTimeMillis());
        payment.setCallbackTime(now);
        payment.setCreateTime(now);
        payment.setUpdateTime(now);

        paymentMapper.insert(payment);
        orderMapper.updateStatusAndPayTime(order.getId(), 1, now);

        // 事务提交后再发 MQ，避免 DB 回滚时消息已发出
        Long orderId = order.getId();
        String paymentNo = payment.getPaymentNo();
        String orderNoFinal = order.getOrderNo();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                paymentSuccessProducer.sendPaymentSuccess(orderId, userId, orderNoFinal, paymentNo);
            }
        });

        return payment;
    }
}
