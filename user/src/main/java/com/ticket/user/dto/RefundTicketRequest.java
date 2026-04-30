package com.ticket.user.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class RefundTicketRequest {
    @NotBlank
    private String orderNo;
    @NotBlank
    private String ticketNo;
}
