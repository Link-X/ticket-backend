package com.ticket.admin.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.Seat;
import com.ticket.core.domain.entity.SeatArea;
import com.ticket.core.mapper.SeatMapper;
import com.ticket.core.service.SeatAreaService;
import com.ticket.core.service.SeatInventoryService;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/seat")
public class SeatController {

    private final SeatMapper seatMapper;
    private final SeatInventoryService inventoryService;
    private final SeatAreaService seatAreaService;

    public SeatController(SeatMapper seatMapper,
                          SeatInventoryService inventoryService,
                          SeatAreaService seatAreaService) {
        this.seatMapper = seatMapper;
        this.inventoryService = inventoryService;
        this.seatAreaService = seatAreaService;
    }

    /**
     * 批量创建座位
     * seats 中每个元素需提供: rowNo, colNo, type, areaId, seatName, pairSeatId(情侣座才填)
     */
    @PostMapping("/batch")
    public Result<?> batchCreateSeats(@RequestBody BatchCreateRequest req) {
        List<Seat> seats = req.getSeats();
        LocalDateTime now = LocalDateTime.now();
        seats.forEach(s -> {
            s.setSessionId(req.getSessionId());
            s.setStatus(0);
            s.setCreateTime(now);
        });
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
     * 保存/覆盖场次价格区域
     */
    @PostMapping("/area/save")
    public Result<?> saveAreas(@RequestBody SaveAreasRequest req) {
        List<SeatArea> areas = req.getAreas();
        areas.forEach(a -> a.setSessionId(req.getSessionId()));
        seatAreaService.saveAreas(req.getSessionId(), areas);
        return Result.success("价格区域保存成功");
    }

    /**
     * 查询场次价格区域列表
     */
    @GetMapping("/area/list")
    public Result<?> listAreas(@RequestParam Long sessionId) {
        return Result.success(seatAreaService.getAreasBySession(sessionId));
    }

    /**
     * 预热：将座位库存和区域价格写入 Redis（不开售，需另调 /session/{id}/publish）
     */
    @PostMapping("/warmup/{sessionId}")
    public Result<?> warmupSeats(@PathVariable Long sessionId) {
        List<Seat> seats = seatMapper.selectBySessionId(sessionId);
        if (seats == null || seats.isEmpty()) {
            return Result.fail(400, "该场次暂无座位数据");
        }
        List<SeatArea> areas = seatAreaService.getAreasBySession(sessionId);
        if (areas == null || areas.isEmpty()) {
            return Result.fail(400, "该场次暂无价格区域数据，请先调用 /area/save");
        }
        inventoryService.warmup(sessionId, seats, areas);
        return Result.success("预热完成，共 " + seats.size() + " 个座位，" + areas.size() + " 个价格区域");
    }

    @Data
    public static class BatchCreateRequest {
        private Long sessionId;
        private List<Seat> seats;
    }

    @Data
    public static class SaveAreasRequest {
        private Long sessionId;
        private List<SeatArea> areas;
    }
}
