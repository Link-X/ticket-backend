package com.ticket.payment.gateway;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 支付网关工厂 — 根据渠道名称返回对应网关实现，默认使用 mock
 */
@Component
public class PaymentGatewayFactory {

    private final Map<String, PaymentGateway> gateways;

    public PaymentGatewayFactory(Map<String, PaymentGateway> gateways) {
        this.gateways = gateways;
    }

    public PaymentGateway getGateway(String channel) {
        return gateways.getOrDefault(channel, gateways.get("mock"));
    }
}
