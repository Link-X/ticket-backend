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
    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 订单状态
     */
    private Integer status;

    /**
     * 订单总额
     */
    private BigDecimal totalAmount;

    /**
     * 订单过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 座位信息列表
     */
    private List<String> seatInfos;
}
