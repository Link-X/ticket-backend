package com.ticket.user.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class SessionDetailRequest {

    @NotNull(message = "场次ID不能为空")
    private Long sessionId;
}
