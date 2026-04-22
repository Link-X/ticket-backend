package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.service.SeatInventoryService;
import com.ticket.core.service.ShowService;
import com.ticket.user.config.NoLogin;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@NoLogin
@RestController
@RequestMapping("/api/show")
public class ShowController {

    private final ShowService showService;
    private final SeatInventoryService inventoryService;

    public ShowController(ShowService showService, SeatInventoryService inventoryService) {
        this.showService = showService;
        this.inventoryService = inventoryService;
    }

    @GetMapping("/list")
    public Result<?> listShows() {
        return Result.success(showService.listShows(1));
    }

    @GetMapping("/{id}")
    public Result<?> getShow(@PathVariable Long id) {
        return Result.success(showService.getShow(id));
    }

    @GetMapping("/{id}/sessions")
    public Result<?> listSessions(@PathVariable Long id) {
        return Result.success(showService.listSessions(id));
    }

    @GetMapping("/session/{sessionId}/seats")
    public Result<?> getAvailableSeats(@PathVariable Long sessionId) {
        Set<String> seatIds = inventoryService.getAvailableSeatIds(sessionId);
        Long count = inventoryService.getAvailableCount(sessionId);
        return Result.success(Map.of("seatIds", seatIds, "count", count));
    }

    @GetMapping("/session/{sessionId}/seat/{seatId}")
    public Result<?> getSeatInfo(@PathVariable Long sessionId, @PathVariable Long seatId) {
        return Result.success(inventoryService.getSeatInfo(seatId));
    }
}
