package com.ticket.core.domain.vo;

import com.ticket.core.domain.entity.ShowSession;
import lombok.Data;
import java.util.List;

@Data
public class SessionSeatResponse {
    private ShowSession session;
    private List<AreaPriceVO> areaPriceList;
    private SeatSectionVO seatSection;
}
