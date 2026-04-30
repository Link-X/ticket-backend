package com.ticket.core.domain.entity;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class RoomArea {
    private Long id;
    private Long roomId;
    private String areaId;
    private BigDecimal defaultPrice;
    private BigDecimal defaultOriginPrice;
}
