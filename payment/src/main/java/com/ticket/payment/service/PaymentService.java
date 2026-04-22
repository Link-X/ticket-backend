package com.ticket.payment.service;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.util.SnowflakeIdGenerator;
import com.ticket.core.domain.entity.Order;
import com.ticket.core.domain.entity.Payment;
import com.ticket.core.mapper.OrderMapper;
import com.ticket.core.mapper.PaymentMapper;
import com.ticket.payment.gateway.PaymentGateway;
import com.ticket.payment.gateway.PaymentGatewayFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 支付服务 — 创建支付记录、调用网关、更新订单状态
 */
@Slf4j
@Service
public class PaymentService {

    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final PaymentGatewayFactory gatewayFactory;

    private final SnowflakeIdGenerator snowflake = new SnowflakeIdGenerator(1, 3);

    public PaymentService(OrderMapper orderMapper,
                          PaymentMapper paymentMapper,
                          PaymentGatewayFactory gatewayFactory) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.gatewayFactory = gatewayFactory;
    }

    /**
     * 处理支付
     *
     * @param orderId 订单 ID
     * @param channel 支付渠道（如 "mock"、"alipay"）
     * @return 支付记录
     */
    public Payment processPayment(Long orderId, String channel) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "订单不存在");
        }
        if (order.getStatus() != 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "订单状态不允许支付");
        }

        String paymentNo = String.valueOf(snowflake.nextId());

        Payment payment = new Payment();
        payment.setId(snowflake.nextId());
        payment.setOrderId(orderId);
        payment.setPaymentNo(paymentNo);
        payment.setChannel(channel != null ? channel : "mock");
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(0); // 支付中

        paymentMapper.insert(payment);

        // 调用支付网关
        PaymentGateway gateway = gatewayFactory.getGateway(payment.getChannel());
        boolean success = gateway.pay(paymentNo, order.getTotalAmount());

        if (success) {
            payment.setStatus(1);
            payment.setTradeNo("TRADE-" + System.currentTimeMillis());
            payment.setCallbackTime(LocalDateTime.now());
            paymentMapper.updateStatus(payment.getId(), 1);
            orderMapper.updateStatusAndPayTime(orderId, 1, LocalDateTime.now());
            log.info("支付成功 orderId={} paymentNo={}", orderId, paymentNo);
        } else {
            payment.setStatus(2);
            paymentMapper.updateStatus(payment.getId(), 2);
            log.warn("支付失败 orderId={} paymentNo={}", orderId, paymentNo);
        }

        return payment;
    }
}
