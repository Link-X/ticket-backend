package com.ticket.core.domain.entity;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 订单项实体类
 */
@Data
public class OrderItem {
    private Long id;
    private Long orderId;
    private Long seatId;
    private BigDecimal price;
    private String seatInfo;
}
