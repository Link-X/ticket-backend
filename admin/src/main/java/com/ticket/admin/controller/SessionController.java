package com.ticket.admin.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.ShowSession;
import com.ticket.core.service.ShowService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/session")
public class SessionController {

    private final ShowService showService;

    public SessionController(ShowService showService) {
        this.showService = showService;
    }

    @PostMapping("/create")
    public Result<ShowSession> createSession(@RequestBody ShowSession session) {
        return Result.success(showService.createSession(session));
    }

    @PutMapping("/update")
    public Result<ShowSession> updateSession(@RequestBody ShowSession session) {
        return Result.success(showService.updateSession(session));
    }

    @GetMapping("/{id}")
    public Result<ShowSession> getSession(@PathVariable Long id) {
        return Result.success(showService.getSession(id));
    }

    @GetMapping("/list")
    public Result<?> listSessions(@RequestParam Long showId) {
        return Result.success(showService.listSessions(showId));
    }

    /**
     * 发布场次开售（需先完成座位预热）
     */
    @PutMapping("/{sessionId}/publish")
    public Result<?> publishSession(@PathVariable Long sessionId) {
        showService.publishSession(sessionId);
        return Result.success("场次已发布开售");
    }
}
