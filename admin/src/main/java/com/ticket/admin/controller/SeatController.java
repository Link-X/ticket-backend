package com.ticket.admin.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.Seat;
import com.ticket.core.domain.entity.ShowSession;
import com.ticket.core.mapper.SeatMapper;
import com.ticket.core.service.SeatInventoryService;
import com.ticket.core.service.ShowService;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/admin/seat")
public class SeatController {

    private final SeatMapper seatMapper;
    private final SeatInventoryService inventoryService;
    private final ShowService showService;

    public SeatController(SeatMapper seatMapper,
                          SeatInventoryService inventoryService,
                          ShowService showService) {
        this.seatMapper = seatMapper;
        this.inventoryService = inventoryService;
        this.showService = showService;
    }

    /**
     * 批量创建座位
     */
    @PostMapping("/batch")
    public Result<?> batchCreateSeats(@RequestBody BatchCreateRequest req) {
        List<Seat> seats = req.getSeats();
        seats.forEach(s -> s.setSessionId(req.getSessionId()));
        seatMapper.batchInsert(seats);
        return Result.success(seats);
    }

    /**
     * 查询场次座位列表
     */
    @GetMapping("/list")
    public Result<?> listSeats(@RequestParam Long sessionId) {
        return Result.success(seatMapper.selectBySessionId(sessionId));
    }

    /**
     * 预热指定场次座位库存到 Redis，并将场次状态置为开售(status=1)
     */
    @PostMapping("/warmup/{sessionId}")
    public Result<?> warmupSeats(@PathVariable Long sessionId) {
        List<Seat> seats = seatMapper.selectBySessionId(sessionId);
        if (seats == null || seats.isEmpty()) {
            return Result.fail(400, "该场次暂无座位数据");
        }
        inventoryService.warmup(sessionId, seats);

        ShowSession session = showService.getSession(sessionId);
        if (session != null) {
            session.setStatus(1);
            showService.updateSession(session);
        }
        return Result.success("预热完成，共 " + seats.size() + " 个座位");
    }

    @Data
    public static class BatchCreateRequest {
        private Long sessionId;
        private List<Seat> seats;
    }
}
