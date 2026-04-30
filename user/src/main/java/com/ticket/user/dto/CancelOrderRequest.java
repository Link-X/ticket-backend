package com.ticket.user.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class CancelOrderRequest {
    @NotBlank
    private String orderNo;
}
