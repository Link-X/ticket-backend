package com.ticket.core.domain.vo;

import lombok.Data;
import java.util.List;

@Data
public class SeatRowVO {
    private String rowsId;
    private String rowsNum;
    private List<SeatColVO> columns;
}
