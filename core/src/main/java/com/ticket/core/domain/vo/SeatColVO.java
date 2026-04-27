package com.ticket.core.domain.vo;

import lombok.Data;

@Data
public class SeatColVO {
    /** 座位数据库ID，空位时为空字符串 */
    private String colId;
    /** 列号，空位时为空字符串 */
    private String colNum;
    /** 座位名称，如 "1排01座"；空位时为 null */
    private String seatName;
    /**
     * 座位类型: 0=空位(占位), 1=普通, 2=情侣左, 3=情侣右
     * type=0 仅用于前端网格占位，不可购买，无 status
     */
    private Integer type;
    /** 价格区域ID；空位时为 null */
    private String areaId;
    /**
     * 座位实时状态（type=0 时为 null）
     * 0=可售, 1=已锁, 2=已售
     */
    private Integer status;
}
