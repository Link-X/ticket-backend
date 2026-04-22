package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.service.VerifyService;
import com.ticket.user.config.NoLogin;
import org.springframework.web.bind.annotation.*;

@NoLogin
@RestController
@RequestMapping("/api/verify")
public class VerifyController {

    private final VerifyService verifyService;

    public VerifyController(VerifyService verifyService) {
        this.verifyService = verifyService;
    }

    @GetMapping("/qr/{qrCode}")
    public Result<?> verifyByQr(@PathVariable String qrCode) {
        return Result.success(verifyService.verifyByQrCode(qrCode));
    }

    @GetMapping("/ticket/{ticketNo}")
    public Result<?> verifyByTicketNo(@PathVariable String ticketNo) {
        return Result.success(verifyService.verifyByTicketNo(ticketNo));
    }
}
