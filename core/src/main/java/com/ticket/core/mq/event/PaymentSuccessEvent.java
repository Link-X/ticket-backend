package com.ticket.core.mq.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSuccessEvent implements Serializable {
    private Long orderId;
    private Long userId;
    private String orderNo;
    private String paymentNo;
}
