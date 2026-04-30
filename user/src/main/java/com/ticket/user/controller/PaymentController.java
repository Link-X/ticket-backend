package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.Payment;
import com.ticket.core.service.MockPaymentService;
import com.ticket.user.dto.PaymentRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final MockPaymentService mockPaymentService;

    public PaymentController(MockPaymentService mockPaymentService) {
        this.mockPaymentService = mockPaymentService;
    }

    @PostMapping("/create")
    public Result<Map<String, Object>> createPayment(@Valid @RequestBody PaymentRequest req) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Payment payment = mockPaymentService.pay(req.getOrderNo(), userId, req.getChannel());
        return Result.success(Map.of(
                "status", "PAID",
                "paymentNo", payment.getPaymentNo()
        ));
    }
}
