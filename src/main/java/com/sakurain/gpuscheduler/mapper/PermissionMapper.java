package com.sakurain.gpuscheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakurain.gpuscheduler.entity.Permission;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限Mapper接口
 */
public interface PermissionMapper extends BaseMapper<Permission> {

    /**
     * 根据资源ID查询权限列表
     */
    @Select("SELECT * FROM permission WHERE resource_id = #{resourceId} AND status = 1")
    List<Permission> selectByResourceId(@Param("resourceId") Long resourceId);

    /**
     * 根据角色ID查询该角色拥有的所有权限
     */
    @Select("SELECT p.* FROM permission p " +
            "INNER JOIN role_permission rp ON p.id = rp.permission_id " +
            "WHERE rp.role_id = #{roleId} AND p.status = 1")
    List<Permission> selectByRoleId(@Param("roleId") Long roleId);

    /**
     * 根据角色ID列表查询所有权限（去重）
     */
    List<Permission> selectByRoleIds(@Param("roleIds") List<Long> roleIds);

    /**
     * 根据用户ID查询该用户拥有的所有权限（通过用户->角色->权限）
     */
    @Select("SELECT DISTINCT p.* FROM permission p " +
            "INNER JOIN role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN user_role ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} " +
            "AND p.status = 1 " +
            "AND (ur.expires_at IS NULL OR ur.expires_at > NOW())")
    List<Permission> selectByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID与资源类型查询权限（通过用户->角色->权限->资源）
     */
    @Select("SELECT DISTINCT p.* FROM permission p " +
            "INNER JOIN role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN user_role ur ON rp.role_id = ur.role_id " +
            "INNER JOIN resource r ON p.resource_id = r.id " +
            "WHERE ur.user_id = #{userId} " +
            "AND r.type = #{resourceType} " +
            "AND p.status = 1 " +
            "AND r.status = 1 " +
            "AND (ur.expires_at IS NULL OR ur.expires_at > NOW())")
    List<Permission> selectByUserIdAndResourceType(@Param("userId") Long userId,
                                                   @Param("resourceType") Integer resourceType);
}
