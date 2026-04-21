package com.ticket.core.mapper;

import com.ticket.core.domain.entity.ShowSession;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 演出场次 Mapper 接口
 */
@Mapper
public interface ShowSessionMapper {

    /**
     * 插入场次（自动生成主键）
     */
    int insert(ShowSession showSession);

    /**
     * 更新场次信息
     */
    int update(ShowSession showSession);

    /**
     * 根据 ID 查询场次
     */
    ShowSession selectById(Long id);

    /**
     * 根据演出 ID 查询场次列表
     */
    List<ShowSession> selectByShowId(Long showId);
}
