package com.ticket.user.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class VerifyQrRequest {
    @NotBlank
    private String qrCode;
}
