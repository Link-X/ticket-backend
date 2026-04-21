package com.ticket.core.mapper;

import com.ticket.core.domain.entity.Show;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 演出 Mapper 接口
 */
@Mapper
public interface ShowMapper {

    /**
     * 插入演出（自动生成主键）
     */
    int insert(Show show);

    /**
     * 更新演出信息
     */
    int update(Show show);

    /**
     * 根据 ID 查询演出
     */
    Show selectById(Long id);

    /**
     * 查询所有演出
     */
    List<Show> selectAll();

    /**
     * 根据状态查询演出列表
     */
    List<Show> selectByStatus(Integer status);
}
