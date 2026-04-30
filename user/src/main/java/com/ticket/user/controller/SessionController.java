package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.vo.SessionSeatResponse;
import com.ticket.core.service.ShowService;
import com.ticket.user.config.NoLogin;
import com.ticket.user.dto.SessionDetailRequest;
import com.ticket.user.dto.SessionListRequest;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

@NoLogin
@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final ShowService showService;

    public SessionController(ShowService showService) {
        this.showService = showService;
    }

    @PostMapping("/list")
    public Result<?> listSessions(@Valid @RequestBody SessionListRequest req) {
        var list = showService.listSessionsPaged(
                req.getShowId(), req.getStatus(), req.getStartTime(), req.getEndTime(),
                req.getPage(), req.getSize());
        var total = showService.countSessions(
                req.getShowId(), req.getStatus(), req.getStartTime(), req.getEndTime());
        return Result.success(Map.of("total", total, "list", list));
    }

    @PostMapping("/detail")
    public Result<SessionSeatResponse> getSessionSeats(@Valid @RequestBody SessionDetailRequest req) {
        return Result.success(showService.getSeatSection(req.getSessionId()));
    }
}
