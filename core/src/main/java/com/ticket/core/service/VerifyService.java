package com.ticket.core.service;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.core.domain.entity.Ticket;
import com.ticket.core.mapper.TicketMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 票券核销服务 — 负责验证和核销票券
 */
@Service
public class VerifyService {

    private final TicketMapper ticketMapper;

    public VerifyService(TicketMapper ticketMapper) {
        this.ticketMapper = ticketMapper;
    }

    /**
     * 通过二维码核销票券
     *
     * @param qrCode 二维码
     * @return 核销后的票券
     */
    public Ticket verifyByQrCode(String qrCode) {
        Ticket ticket = ticketMapper.selectByQrCode(qrCode);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        return doVerify(ticket);
    }

    /**
     * 通过票号核销票券
     *
     * @param ticketNo 票号
     * @return 核销后的票券
     */
    public Ticket verifyByTicketNo(String ticketNo) {
        Ticket ticket = ticketMapper.selectByTicketNo(ticketNo);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        return doVerify(ticket);
    }

    /**
     * 执行核销逻辑
     *
     * @param ticket 待核销的票券
     * @return 核销后的票券
     */
    private Ticket doVerify(Ticket ticket) {
        if (ticket.getStatus() == 1) {
            throw new BusinessException(ErrorCode.TICKET_ALREADY_USED);
        }
        if (ticket.getStatus() == 2) {
            throw new BusinessException(ErrorCode.ORDER_EXPIRED);
        }

        // 原子 UPDATE WHERE status=0，受影响行为 0 说明已被并发核销
        int affected = ticketMapper.updateStatusAndVerifyTime(ticket.getId(), 1, LocalDateTime.now());
        if (affected == 0) {
            throw new BusinessException(ErrorCode.TICKET_ALREADY_USED);
        }

        return ticketMapper.selectById(ticket.getId());
    }
}
