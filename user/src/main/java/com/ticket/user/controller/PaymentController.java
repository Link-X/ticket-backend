package com.ticket.user.controller;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.result.Result;
import com.ticket.common.util.SnowflakeIdGenerator;
import com.ticket.core.domain.entity.Order;
import com.ticket.core.domain.entity.Payment;
import com.ticket.core.mapper.OrderMapper;
import com.ticket.core.mapper.PaymentMapper;
import com.ticket.core.service.TicketService;
import lombok.Data;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final TicketService ticketService;

    // workerId=2 区别于 OrderService 的 (1,1)，避免 ID 重复
    private final SnowflakeIdGenerator snowflake = new SnowflakeIdGenerator(1, 2);

    public PaymentController(OrderMapper orderMapper,
                             PaymentMapper paymentMapper,
                             TicketService ticketService) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.ticketService = ticketService;
    }

    @PostMapping("/create")
    public Result<Map<String, Object>> createPayment(@RequestBody PaymentRequest req) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Order order = orderMapper.selectById(req.getOrderId());
        if (order == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "订单不存在");
        }
        if (order.getStatus() != 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "订单状态不允许支付");
        }

        // 创建支付记录（Mock 网关始终成功）
        Payment payment = new Payment();
        payment.setId(snowflake.nextId());
        payment.setOrderId(order.getId());
        payment.setPaymentNo(String.valueOf(snowflake.nextId()));
        payment.setChannel(req.getChannel() != null ? req.getChannel() : "mock");
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(1);
        payment.setTradeNo("MOCK-" + System.currentTimeMillis());
        payment.setCallbackTime(LocalDateTime.now());
        paymentMapper.insert(payment);

        // 更新订单为已支付
        orderMapper.updateStatusAndPayTime(order.getId(), 1, LocalDateTime.now());

        // 生成票券
        var tickets = ticketService.generateTicketsForOrder(order.getId(), userId);

        return Result.success(Map.of(
                "status", "PAID",
                "paymentNo", payment.getPaymentNo(),
                "tickets", tickets
        ));
    }

    @Data
    public static class PaymentRequest {
        private Long orderId;
        private String channel;
    }
}
