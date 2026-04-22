package com.ticket.core.domain.entity;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 座位区域实体类
 */
@Data
public class SeatArea {
    private Long id;
    private Long sessionId;
    /** 区域标识，如 "0"、"1"，场次内唯一 */
    private String areaId;
    private BigDecimal price;
    private BigDecimal originPrice;
}
