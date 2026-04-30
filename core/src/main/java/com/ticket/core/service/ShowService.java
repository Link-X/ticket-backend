package com.ticket.core.service;

import com.ticket.core.domain.entity.Seat;
import com.ticket.core.domain.entity.SeatArea;
import com.ticket.core.domain.entity.Show;
import com.ticket.core.domain.entity.ShowSession;
import com.ticket.core.domain.vo.AreaPriceVO;
import com.ticket.core.domain.vo.SeatColVO;
import com.ticket.core.domain.vo.SeatRowVO;
import com.ticket.core.domain.vo.SeatSectionVO;
import com.ticket.core.domain.vo.SessionSeatResponse;
import com.ticket.core.mapper.SeatAreaMapper;
import com.ticket.core.mapper.SeatMapper;
import com.ticket.core.mapper.ShowMapper;
import com.ticket.core.mapper.ShowSessionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 演出服务
 */
@Service
public class ShowService {

    private final ShowMapper showMapper;
    private final ShowSessionMapper showSessionMapper;
    private final SeatMapper seatMapper;
    private final SeatAreaMapper seatAreaMapper;
    private final SeatInventoryService inventoryService;

    /**
     * 构造器注入
     */
    public ShowService(ShowMapper showMapper,
                       ShowSessionMapper showSessionMapper,
                       SeatMapper seatMapper,
                       SeatAreaMapper seatAreaMapper,
                       SeatInventoryService inventoryService) {
        this.showMapper = showMapper;
        this.showSessionMapper = showSessionMapper;
        this.seatMapper = seatMapper;
        this.seatAreaMapper = seatAreaMapper;
        this.inventoryService = inventoryService;
    }

    /**
     * 创建演出
     * 设置 status=1，调用 mapper.insert，返回 show
     */
    public Show createShow(Show show) {
        LocalDateTime now = LocalDateTime.now();
        show.setStatus(1);
        show.setCreateTime(now);
        show.setUpdateTime(now);
        showMapper.insert(show);
        return show;
    }

    /**
     * 更新演出
     * mapper.update，返回 selectById 查询结果
     */
    public Show updateShow(Show show) {
        showMapper.update(show);
        return showMapper.selectById(show.getId());
    }

    /**
     * 获取演出
     */
    public Show getShow(Long id) {
        return showMapper.selectById(id);
    }

    /**
     * 列出演出
     * status 非空调 selectByStatus，否则 selectAll
     */
    public List<Show> listShows(Integer status) {
        if (status != null) {
            return showMapper.selectByStatus(status);
        }
        return showMapper.selectAll();
    }

    public List<Show> listShowsPaged(String name, String category, String venue, int page, int size) {
        int offset = (page - 1) * size;
        return showMapper.selectByCondition(name, category, venue, 1, offset, size);
    }

    public int countShows(String name, String category, String venue) {
        return showMapper.countByCondition(name, category, venue, 1);
    }

    /**
     * 创建场次
     * 设置 status=0，insert，返回 session
     */
    public ShowSession createSession(ShowSession showSession) {
        LocalDateTime now = LocalDateTime.now();
        showSession.setStatus(0);
        showSession.setCreateTime(now);
        showSession.setUpdateTime(now);
        showSessionMapper.insert(showSession);
        return showSession;
    }

    /**
     * 更新场次
     * mapper.update，返回 selectById 查询结果
     */
    public ShowSession updateSession(ShowSession showSession) {
        showSessionMapper.update(showSession);
        return showSessionMapper.selectById(showSession.getId());
    }

    /**
     * 获取场次
     */
    public ShowSession getSession(Long id) {
        return showSessionMapper.selectById(id);
    }

    /**
     * 列出场次
     */
    public List<ShowSession> listSessions(Long showId) {
        return showSessionMapper.selectByShowId(showId);
    }

    public List<ShowSession> listSessionsPaged(Long showId, Integer status,
                                               LocalDateTime startTime, LocalDateTime endTime,
                                               int page, int size) {
        int offset = (page - 1) * size;
        return showSessionMapper.selectByCondition(showId, status, startTime, endTime, offset, size);
    }

    public int countSessions(Long showId, Integer status, LocalDateTime startTime, LocalDateTime endTime) {
        return showSessionMapper.countByCondition(showId, status, startTime, endTime);
    }

    /**
     * 发布场次：将状态置为开售（status=1）
     */
    public void publishSession(Long sessionId) {
        ShowSession session = showSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new com.ticket.common.exception.BusinessException(
                    com.ticket.common.exception.ErrorCode.PARAM_ERROR, "场次不存在");
        }
        session.setStatus(1);
        showSessionMapper.update(session);
    }

    /**
     * 查询场次的完整座位图和价格区域列表
     * - areaPriceList：该场次所有价格区域
     * - seatSection：按 rowCount×colCount 网格构建，空位以 type=0 占位
     * - 座位 status 从 Redis 实时读取
     */
    public SessionSeatResponse getSeatSection(Long sessionId) {
        ShowSession session = showSessionMapper.selectById(sessionId);
        List<SeatArea> areas = seatAreaMapper.selectBySessionId(sessionId);
        List<Seat> seats = seatMapper.selectBySessionId(sessionId);

        int rowCount = session.getRowCount() != null ? session.getRowCount() : 0;
        int colCount = session.getColCount() != null ? session.getColCount() : 0;

        // 构建座位坐标索引 (rowNo, colNo) -> Seat
        Map<String, Seat> seatGrid = new HashMap<>();
        List<Long> seatIds = new ArrayList<>();
        for (Seat seat : seats) {
            seatGrid.put(seat.getRowNo() + ":" + seat.getColNo(), seat);
            seatIds.add(seat.getId());
        }

        // 批量查询 Redis 实时状态
        Map<Long, Integer> statusMap = seatIds.isEmpty()
                ? new HashMap<>()
                : inventoryService.batchGetSeatStatus(sessionId, seatIds);

        // 构建价格区域列表
        List<AreaPriceVO> areaPriceList = new ArrayList<>();
        for (SeatArea area : areas) {
            AreaPriceVO vo = new AreaPriceVO();
            vo.setAreaId(area.getAreaId());
            vo.setPrice(area.getPrice().toPlainString());
            vo.setOriginPrice(area.getOriginPrice().toPlainString());
            areaPriceList.add(vo);
        }

        // 构建网格行列
        List<SeatRowVO> seatRows = new ArrayList<>();
        for (int row = 1; row <= rowCount; row++) {
            List<SeatColVO> columns = new ArrayList<>();
            for (int col = 1; col <= colCount; col++) {
                Seat seat = seatGrid.get(row + ":" + col);
                SeatColVO colVO = new SeatColVO();
                if (seat == null) {
                    // 空位占位
                    colVO.setColId("");
                    colVO.setColNum("");
                    colVO.setSeatName(null);
                    colVO.setType(0);
                    colVO.setAreaId(null);
                    colVO.setStatus(null);
                } else {
                    colVO.setColId(String.valueOf(seat.getId()));
                    colVO.setColNum(String.valueOf(seat.getColNo()));
                    colVO.setSeatName(seat.getSeatName());
                    colVO.setType(seat.getType());
                    colVO.setAreaId(seat.getAreaId());
                    colVO.setStatus(statusMap.getOrDefault(seat.getId(), 0));
                }
                columns.add(colVO);
            }
            SeatRowVO rowVO = new SeatRowVO();
            rowVO.setRowsId(String.valueOf(row));
            rowVO.setRowsNum(String.valueOf(row));
            rowVO.setColumns(columns);
            seatRows.add(rowVO);
        }

        SeatSectionVO seatSection = new SeatSectionVO();
        seatSection.setRowCount(rowCount);
        seatSection.setColumnCount(colCount);
        seatSection.setSeatRows(seatRows);

        SessionSeatResponse response = new SessionSeatResponse();
        response.setAreaPriceList(areaPriceList);
        response.setSeatSection(seatSection);
        return response;
    }
}
