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

    /**
     * 带条件分页查询（name/category/venue 模糊匹配）
     */
    List<Show> selectByCondition(@org.apache.ibatis.annotations.Param("name") String name,
                                 @org.apache.ibatis.annotations.Param("category") String category,
                                 @org.apache.ibatis.annotations.Param("venue") String venue,
                                 @org.apache.ibatis.annotations.Param("status") Integer status,
                                 @org.apache.ibatis.annotations.Param("offset") int offset,
                                 @org.apache.ibatis.annotations.Param("size") int size);

    /**
     * 带条件统计总数
     */
    int countByCondition(@org.apache.ibatis.annotations.Param("name") String name,
                         @org.apache.ibatis.annotations.Param("category") String category,
                         @org.apache.ibatis.annotations.Param("venue") String venue,
                         @org.apache.ibatis.annotations.Param("status") Integer status);
}
