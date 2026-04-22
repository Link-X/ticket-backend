package com.ticket.admin.controller;

import com.ticket.common.result.Result;
import com.ticket.core.service.SeatInventoryService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/monitor")
public class MonitorController {

    private final SeatInventoryService inventoryService;

    public MonitorController(SeatInventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/dashboard")
    public Result<?> getDashboard(@RequestParam Long sessionId) {
        Long availableCount = inventoryService.getAvailableCount(sessionId);
        return Result.success(Map.of(
                "sessionId", sessionId,
                "availableCount", availableCount != null ? availableCount : 0L
        ));
    }
}
