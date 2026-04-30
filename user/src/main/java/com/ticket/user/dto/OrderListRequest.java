package com.ticket.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderListRequest {
    private int page = 1;
    private int size = 10;
    /** 订单状态：0-待支付 1-已支付 2-已取消 3-退款中 4-已退款，不传则查全部 */
    private Integer status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
