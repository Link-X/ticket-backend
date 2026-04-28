package com.ticket.user.controller;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.result.Result;
import com.ticket.common.util.SnowflakeIdGenerator;
import com.ticket.core.domain.entity.Order;
import com.ticket.core.domain.entity.Payment;
import com.ticket.core.mapper.OrderMapper;
import com.ticket.core.mapper.PaymentMapper;
import com.ticket.core.mq.producer.PaymentSuccessProducer;
import com.ticket.user.dto.PaymentRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final PaymentSuccessProducer paymentSuccessProducer;

    private final SnowflakeIdGenerator snowflake = new SnowflakeIdGenerator(1, 2);

    public PaymentController(OrderMapper orderMapper,
                             PaymentMapper paymentMapper,
                             PaymentSuccessProducer paymentSuccessProducer) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.paymentSuccessProducer = paymentSuccessProducer;
    }

    @PostMapping("/create")
    public Result<Map<String, Object>> createPayment(@Valid @RequestBody PaymentRequest req) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Order order = orderMapper.selectByOrderNo(req.getOrderNo());
        if (order == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (order.getStatus() != 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "订单状态不允许支付");
        }

        Payment payment = new Payment();
        payment.setId(snowflake.nextId());
        payment.setOrderId(order.getId());
        payment.setPaymentNo(String.valueOf(snowflake.nextId()));
        payment.setChannel(req.getChannel() != null ? req.getChannel() : "mock");
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(1);
        payment.setTradeNo("MOCK-" + System.currentTimeMillis());
        LocalDateTime now = LocalDateTime.now();
        payment.setCallbackTime(now);
        payment.setCreateTime(now);
        payment.setUpdateTime(now);
        paymentMapper.insert(payment);

        orderMapper.updateStatusAndPayTime(order.getId(), 1, LocalDateTime.now());

        paymentSuccessProducer.sendPaymentSuccess(
                order.getId(), userId, order.getOrderNo(), payment.getPaymentNo());

        return Result.success(Map.of(
                "status", "PAID",
                "paymentNo", payment.getPaymentNo()
        ));
    }
}
