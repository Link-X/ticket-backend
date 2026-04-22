package com.ticket.payment.gateway;

import java.math.BigDecimal;

/**
 * 支付网关接口
 */
public interface PaymentGateway {

    /**
     * 发起支付
     *
     * @param paymentNo 支付单号
     * @param amount    支付金额
     * @return 支付成功返回 true
     */
    boolean pay(String paymentNo, BigDecimal amount);
}
