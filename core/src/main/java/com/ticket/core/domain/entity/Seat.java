package com.ticket.core.domain.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 座位实体类
 */
@Data
public class Seat {
    private Long id;
    private Long sessionId;
    private Integer rowNo;
    private Integer colNo;
    private String seatType;
    private BigDecimal price;
    private Integer status;
    private LocalDateTime createTime;
}
