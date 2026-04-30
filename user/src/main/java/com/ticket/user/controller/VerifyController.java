package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.service.VerifyService;
import com.ticket.user.config.NoLogin;
import com.ticket.user.dto.VerifyQrRequest;
import com.ticket.user.dto.VerifyTicketRequest;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@NoLogin
@RestController
@RequestMapping("/api/verify")
public class VerifyController {

    private final VerifyService verifyService;

    public VerifyController(VerifyService verifyService) {
        this.verifyService = verifyService;
    }

    @PostMapping("/qr")
    public Result<?> verifyByQr(@Valid @RequestBody VerifyQrRequest req) {
        return Result.success(verifyService.verifyByQrCode(req.getQrCode()));
    }

    @PostMapping("/ticket")
    public Result<?> verifyByTicketNo(@Valid @RequestBody VerifyTicketRequest req) {
        return Result.success(verifyService.verifyByTicketNo(req.getTicketNo()));
    }
}
