package com.ticket.user.controller;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.result.Result;
import com.ticket.core.domain.dto.OrderCreateRequest;
import com.ticket.core.domain.dto.OrderStatusResponse;
import com.ticket.core.domain.entity.Order;
import com.ticket.common.annotation.LimitType;
import com.ticket.common.annotation.RateLimit;
import com.ticket.core.service.OrderService;
import com.ticket.core.service.PurchaseLimitService;
import com.ticket.core.service.SeatInventoryService;
import com.ticket.core.service.ShowService;
import lombok.extern.slf4j.Slf4j;
import com.ticket.user.dto.SubmitOrderRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final ShowService showService;
    private final PurchaseLimitService purchaseLimitService;
    private final SeatInventoryService inventoryService;
    private final OrderService orderService;

    private static final long SEAT_LOCK_TTL = 300L;

    public OrderController(ShowService showService,
                           PurchaseLimitService purchaseLimitService,
                           SeatInventoryService inventoryService,
                           OrderService orderService) {
        this.showService = showService;
        this.purchaseLimitService = purchaseLimitService;
        this.inventoryService = inventoryService;
        this.orderService = orderService;
    }

    @RateLimit(type = LimitType.BLACKLIST)
    @RateLimit(type = LimitType.IP,     limit = 30,  window = 60, message = "IP 请求过于频繁，请稍后再试")
    @RateLimit(type = LimitType.USER,   limit = 5,   window = 60, message = "操作太频繁，请稍后再试")
    @RateLimit(type = LimitType.GLOBAL, limit = 50,  window = 1,  message = "系统繁忙，请稍后重试")
    @PostMapping("/submit")
    public Result<OrderStatusResponse> submit(@Valid @RequestBody SubmitOrderRequest req) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        var session = showService.getSession(req.getSessionId());
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "场次不存在");
        }
        int seatCount = req.getSeatIds().size();
        boolean allowed = purchaseLimitService.checkAndIncrement(
                req.getSessionId(), userId, session.getLimitPerUser(), seatCount);
        if (!allowed) {
            throw new BusinessException(ErrorCode.EXCEED_PURCHASE_LIMIT);
        }

        boolean locked = inventoryService.batchLockSeats(
                req.getSessionId(), req.getSeatIds(), String.valueOf(userId), SEAT_LOCK_TTL);
        if (!locked) {
            purchaseLimitService.decrement(req.getSessionId(), userId, seatCount);
            throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
        }

        OrderCreateRequest orderReq = new OrderCreateRequest();
        orderReq.setUserId(userId);
        orderReq.setSessionId(req.getSessionId());
        orderReq.setSeatIds(req.getSeatIds());

        Order order = orderService.createOrder(orderReq);
        return Result.success(orderService.buildStatusResponse(order));
    }

    @PostMapping("/cancel")
    public Result<Void> cancel(@RequestParam String orderNo) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Order order = orderService.getByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (order.getStatus() == 0) {
            // 未支付：直接同步取消，立即释放座位
            orderService.cancelOrder(order.getId());
        } else if (order.getStatus() == 1) {
            // 已支付：发起退款，异步通过 MQ 处理
            orderService.initiateRefund(order.getId());
        } else {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前订单状态不可取消");
        }
        return Result.success(null);
    }

    @GetMapping("/orderDetails")
    public Result<OrderStatusResponse> detail(@RequestParam String orderNo) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Order order = orderService.getByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return Result.success(orderService.buildStatusResponse(order));
    }

    @GetMapping("/list")
    public Result<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<OrderStatusResponse> orders = orderService.getUserOrders(userId, page, size, startTime, endTime);
        int total = orderService.countUserOrders(userId, startTime, endTime);
        return Result.success(Map.of("total", total, "list", orders));
    }
}
