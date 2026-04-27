package com.ticket.core.domain.entity;

import lombok.Data;
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
    /** 座位类型: 1=普通, 2=情侣左, 3=情侣右 */
    private Integer type;
    /** 对应 seat_area.area_id */
    private String areaId;
    private String seatName;
    /** 情侣连座配对座位ID，type=2/3时非空 */
    private Long pairSeatId;
    /** 0=可售, 1=已锁, 2=已售 */
    private Integer status;
    private LocalDateTime createTime;
}
