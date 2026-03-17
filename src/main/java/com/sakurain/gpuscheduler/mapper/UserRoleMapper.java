package com.sakurain.gpuscheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakurain.gpuscheduler.entity.UserRole;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户-角色关联Mapper接口
 */
public interface UserRoleMapper extends BaseMapper<UserRole> {

    /**
     * 根据用户ID查询所有角色关联（包括已过期的）
     */
    @Select("SELECT * FROM user_role WHERE user_id = #{userId}")
    List<UserRole> selectByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID查询有效的角色关联（未过期）
     */
    @Select("SELECT * FROM user_role WHERE user_id = #{userId} " +
            "AND (expires_at IS NULL OR expires_at > NOW())")
    List<UserRole> selectValidByUserId(@Param("userId") Long userId);

    /**
     * 根据角色ID查询所有用户关联
     */
    @Select("SELECT * FROM user_role WHERE role_id = #{roleId}")
    List<UserRole> selectByRoleId(@Param("roleId") Long roleId);

    /**
     * 批量插入用户-角色关联
     */
    int batchInsert(@Param("list") List<UserRole> list);

    /**
     * 删除用户下的所有角色关联
     */
    @Delete("DELETE FROM user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 删除指定用户的指定角色关联
     */
    @Delete("DELETE FROM user_role WHERE user_id = #{userId} AND role_id = #{roleId}")
    int deleteByUserIdAndRoleId(@Param("userId") Long userId, @Param("roleId") Long roleId);

    /**
     * 查询已过期的用户角色关联
     */
    @Select("SELECT * FROM user_role WHERE expires_at IS NOT NULL AND expires_at <= NOW()")
    List<UserRole> selectExpiredUserRoles();
}
