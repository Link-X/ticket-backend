package com.ticket.core.domain.entity;

import lombok.Data;

@Data
public class RoomSeat {
    private Long id;
    private Long roomId;
    private Integer rowNo;
    private Integer colNo;
    private Integer type;
    private String areaId;
    private String seatName;
    private Long pairSeatId;
}
