package com.ticket.core.mq.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundEvent {
    private Long orderId;
    /** 需要退款的座位 ID 列表（部分退款时只包含未核销票券对应的座位） */
    private List<Long> refundSeatIds;
}
