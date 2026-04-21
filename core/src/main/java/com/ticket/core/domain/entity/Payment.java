package com.ticket.core.domain.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付实体类
 */
@Data
public class Payment {
    private Long id;
    private Long orderId;
    private String paymentNo;
    private String channel;
    private BigDecimal amount;
    private Integer status;
    private String tradeNo;
    private LocalDateTime callbackTime;
    // 审计字段
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
