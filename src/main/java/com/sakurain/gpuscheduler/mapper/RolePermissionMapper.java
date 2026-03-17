package com.sakurain.gpuscheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakurain.gpuscheduler.entity.RolePermission;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 角色-权限关联Mapper接口
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {

    /**
     * 根据角色ID查询所有关联记录
     */
    @Select("SELECT * FROM role_permission WHERE role_id = #{roleId}")
    List<RolePermission> selectByRoleId(@Param("roleId") Long roleId);

    /**
     * 根据权限ID查询所有关联记录
     */
    @Select("SELECT * FROM role_permission WHERE permission_id = #{permissionId}")
    List<RolePermission> selectByPermissionId(@Param("permissionId") Long permissionId);

    /**
     * 批量插入角色-权限关联
     */
    @Insert("<script>" +
            "INSERT INTO role_permission (role_id, permission_id, granted_by) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.roleId}, #{item.permissionId}, #{item.grantedBy})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<RolePermission> list);

    /**
     * 删除角色下的所有权限关联
     */
    @Delete("DELETE FROM role_permission WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 删除指定角色的指定权限关联
     */
    @Delete("DELETE FROM role_permission WHERE role_id = #{roleId} AND permission_id = #{permissionId}")
    int deleteByRoleIdAndPermissionId(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}
