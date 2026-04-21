package com.ticket.core.service;

import com.ticket.core.domain.entity.Show;
import com.ticket.core.domain.entity.ShowSession;
import com.ticket.core.mapper.ShowMapper;
import com.ticket.core.mapper.ShowSessionMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 演出服务
 */
@Service
public class ShowService {

    private final ShowMapper showMapper;
    private final ShowSessionMapper showSessionMapper;

    /**
     * 构造器注入
     */
    public ShowService(ShowMapper showMapper, ShowSessionMapper showSessionMapper) {
        this.showMapper = showMapper;
        this.showSessionMapper = showSessionMapper;
    }

    /**
     * 创建演出
     * 设置 status=1，调用 mapper.insert，返回 show
     */
    public Show createShow(Show show) {
        show.setStatus(1);
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

    /**
     * 创建场次
     * 设置 status=0，insert，返回 session
     */
    public ShowSession createSession(ShowSession showSession) {
        showSession.setStatus(0);
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
}
