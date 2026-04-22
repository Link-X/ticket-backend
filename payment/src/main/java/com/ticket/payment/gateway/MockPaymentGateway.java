package com.ticket.payment.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Mock 支付网关 — 始终返回成功，用于开发/测试环境
 */
@Slf4j
@Component("mock")
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public boolean pay(String paymentNo, BigDecimal amount) {
        log.info("MockPaymentGateway: 支付成功 paymentNo={} amount={}", paymentNo, amount);
        return true;
    }
}
