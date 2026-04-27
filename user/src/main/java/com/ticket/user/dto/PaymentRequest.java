package com.ticket.user.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class PaymentRequest {
    @NotNull(message = "订单ID不能为空")
    private Long orderId;
    private String channel;
}
