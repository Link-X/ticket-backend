package com.ticket.core.domain.vo;

import lombok.Data;
import java.util.List;

@Data
public class SeatSectionVO {
    private Integer rowCount;
    private Integer columnCount;
    private List<SeatRowVO> seatRows;
}
