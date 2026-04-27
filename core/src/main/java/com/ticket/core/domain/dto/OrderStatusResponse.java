package com.ticket.core.domain.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单状态响应 DTO
 */
@Data
public class OrderStatusResponse {
    private String orderNo;
    private Integer status;
    private BigDecimal totalAmount;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime expireTime;
    private List<String> seatInfos;

    private String showName;
    private String showVenue;
    private String sessionName;
    private LocalDateTime sessionStartTime;
}
