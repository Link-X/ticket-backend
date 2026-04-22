package com.ticket.user.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.result.Result;
import com.ticket.core.domain.dto.OrderCreateRequest;
import com.ticket.core.service.PurchaseLimitService;
import com.ticket.core.service.SeatInventoryService;
import com.ticket.core.service.ShowService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final ShowService showService;
    private final PurchaseLimitService purchaseLimitService;
    private final SeatInventoryService inventoryService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 座位锁 TTL（秒）：覆盖队列等待 + 订单处理时间 */
    private static final long SEAT_LOCK_TTL = 300L;

    @Value("${ticket.stream.key:ticket:order:stream}")
    private String streamKey;

    public OrderController(ShowService showService,
                           PurchaseLimitService purchaseLimitService,
                           SeatInventoryService inventoryService,
                           StringRedisTemplate redisTemplate,
                           ObjectMapper objectMapper) {
        this.showService = showService;
        this.purchaseLimitService = purchaseLimitService;
        this.inventoryService = inventoryService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@RequestBody SubmitRequest req) throws JsonProcessingException {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // 限购检查
        var session = showService.getSession(req.getSessionId());
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "场次不存在");
        }
        boolean allowed = purchaseLimitService.checkAndIncrement(
                req.getSessionId(), userId, session.getLimitPerUser());
        if (!allowed) {
            throw new BusinessException(ErrorCode.EXCEED_PURCHASE_LIMIT);
        }

        // 原子锁座：任一座位已被锁定则整批失败
        boolean locked = inventoryService.batchLockSeats(
                req.getSessionId(), req.getSeatIds(), String.valueOf(userId), SEAT_LOCK_TTL);
        if (!locked) {
            purchaseLimitService.decrement(req.getSessionId(), userId);
            throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
        }

        // 构建消息并写入 Redis Stream
        OrderCreateRequest orderReq = new OrderCreateRequest();
        orderReq.setUserId(userId);
        orderReq.setSessionId(req.getSessionId());
        orderReq.setSeatIds(req.getSeatIds());

        String json = objectMapper.writeValueAsString(orderReq);
        RecordId recordId = redisTemplate.opsForStream().add(
                StreamRecords.newRecord()
                        .ofMap(Map.of("data", json))
                        .withStreamKey(streamKey)
        );

        String requestId = recordId != null ? recordId.getValue() : "";
        return Result.success(Map.of("requestId", requestId, "status", "QUEUED"));
    }

    @GetMapping("/query/{requestId}")
    public Result<?> query(@PathVariable String requestId) {
        // 占位实现，后续可按 requestId 查询订单状态
        return Result.success(Map.of("requestId", requestId, "status", "PROCESSING"));
    }

    @Data
    public static class SubmitRequest {
        private Long sessionId;
        private List<Long> seatIds;
    }
}
