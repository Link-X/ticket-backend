package com.ticket.core.mq.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundEvent implements Serializable {
    private Long orderId;
    private Long userId;
    private Long sessionId;
    private String orderNo;
}
