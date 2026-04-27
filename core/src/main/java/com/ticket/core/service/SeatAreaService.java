package com.ticket.core.service;

import com.ticket.core.domain.entity.SeatArea;
import com.ticket.core.mapper.SeatAreaMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SeatAreaService {

    private final SeatAreaMapper seatAreaMapper;

    public SeatAreaService(SeatAreaMapper seatAreaMapper) {
        this.seatAreaMapper = seatAreaMapper;
    }

    /**
     * 保存场次价格区域（覆盖写：先删旧的再批量插入）
     */
    @Transactional
    public void saveAreas(Long sessionId, List<SeatArea> areas) {
        seatAreaMapper.deleteBySessionId(sessionId);
        areas.forEach(a -> a.setSessionId(sessionId));
        if (!areas.isEmpty()) {
            seatAreaMapper.batchInsert(areas);
        }
    }

    /**
     * 查询场次价格区域列表
     */
    public List<SeatArea> getAreasBySession(Long sessionId) {
        return seatAreaMapper.selectBySessionId(sessionId);
    }
}
