package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.vo.SessionSeatResponse;
import com.ticket.core.service.ShowService;
import com.ticket.user.config.NoLogin;
import org.springframework.web.bind.annotation.*;

@NoLogin
@RestController
@RequestMapping("/api/show")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
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

    /**
     * 获取场次座位图（含价格区域列表 + 网格化座位信息）
     */
    @GetMapping("/session/seats")
    public Result<SessionSeatResponse> getSessionSeats(@RequestParam Long sessionId) {
        return Result.success(showService.getSeatSection(sessionId));
    }
}
