package com.ticket.user.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class PaymentRequest {
    @NotNull(message = "订单号不能为空")
    private String orderNo;
    private String channel;
}
