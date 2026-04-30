package com.ticket.core.mapper;

import com.ticket.core.domain.entity.ShowSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
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

    /**
     * 带条件分页查询场次
     */
    List<ShowSession> selectByCondition(@Param("showId") Long showId,
                                        @Param("status") Integer status,
                                        @Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime,
                                        @Param("offset") int offset,
                                        @Param("size") int size);

    /**
     * 带条件统计场次总数
     */
    int countByCondition(@Param("showId") Long showId,
                         @Param("status") Integer status,
                         @Param("startTime") LocalDateTime startTime,
                         @Param("endTime") LocalDateTime endTime);
}
