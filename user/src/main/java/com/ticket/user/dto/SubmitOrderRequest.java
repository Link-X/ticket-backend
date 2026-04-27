package com.ticket.user.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class SubmitOrderRequest {
    @NotNull(message = "场次ID不能为空")
    private Long sessionId;
    @NotEmpty(message = "座位列表不能为空")
    private List<Long> seatIds;
}
