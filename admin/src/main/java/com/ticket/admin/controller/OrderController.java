package com.ticket.admin.controller;

import com.ticket.common.result.Result;
import com.ticket.core.mapper.OrderMapper;
import com.ticket.core.service.OrderService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/order")
public class OrderController {

    private final OrderMapper orderMapper;
    private final OrderService orderService;

    public OrderController(OrderMapper orderMapper, OrderService orderService) {
        this.orderMapper = orderMapper;
        this.orderService = orderService;
    }

    @GetMapping("/{id}")
    public Result<?> getOrder(@PathVariable Long id) {
        return Result.success(orderMapper.selectById(id));
    }

    @GetMapping("/query")
    public Result<?> queryByOrderNo(@RequestParam String orderNo) {
        return Result.success(orderService.getByOrderNo(orderNo));
    }

    @GetMapping("/{id}/items")
    public Result<?> getOrderItems(@PathVariable Long id) {
        return Result.success(orderService.getOrderItems(id));
    }
}
