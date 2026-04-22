package com.ticket.admin.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.Show;
import com.ticket.core.service.ShowService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/show")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @PostMapping("/create")
    public Result<Show> createShow(@RequestBody Show show) {
        return Result.success(showService.createShow(show));
    }

    @PutMapping("/update")
    public Result<Show> updateShow(@RequestBody Show show) {
        return Result.success(showService.updateShow(show));
    }

    @GetMapping("/{id}")
    public Result<Show> getShow(@PathVariable Long id) {
        return Result.success(showService.getShow(id));
    }

    @GetMapping("/list")
    public Result<?> listShows(@RequestParam(required = false) Integer status) {
        return Result.success(showService.listShows(status));
    }
}
