package com.ticket.core.domain.vo;

import lombok.Data;
import java.util.List;

@Data
public class SessionSeatResponse {
    private List<AreaPriceVO> areaPriceList;
    private SeatSectionVO seatSection;
}
