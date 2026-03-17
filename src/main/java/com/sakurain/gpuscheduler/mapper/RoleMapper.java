package com.sakurain.gpuscheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakurain.gpuscheduler.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色Mapper接口
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    /**
     * 根据用户ID查询该用户拥有的所有角色
     */
    @Select("SELECT r.* FROM role r " +
            "INNER JOIN user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} " +
            "AND r.status = 1 " +
            "AND (ur.expires_at IS NULL OR ur.expires_at > NOW()) " +
            "ORDER BY r.sort_order")
    List<Role> selectByUserId(@Param("userId") Long userId);

    /**
     * 根据父角色ID查询子角色列表
     */
    @Select("SELECT * FROM role WHERE parent_role_id = #{parentRoleId} AND status = 1 ORDER BY sort_order")
    List<Role> selectByParentRoleId(@Param("parentRoleId") Long parentRoleId);

    /**
     * 查询所有根角色（顶级角色）
     */
    @Select("SELECT * FROM role WHERE parent_role_id IS NULL AND status = 1 ORDER BY sort_order")
    List<Role> selectRootRoles();

    /**
     * 根据角色类型查询角色列表
     */
    @Select("SELECT * FROM role WHERE role_type = #{roleType} AND status = 1 ORDER BY sort_order")
    List<Role> selectByRoleType(@Param("roleType") Integer roleType);

    /**
     * 根据角色编码查询角色
     */
    @Select("SELECT * FROM role WHERE code = #{code} AND status = 1")
    Role selectByCode(@Param("code") String code);

    /**
     * 查询角色及其所有父角色（递归查询角色层级）
     */
    @Select("WITH RECURSIVE role_hierarchy AS (" +
            "  SELECT * FROM role WHERE id = #{roleId} " +
            "  UNION ALL " +
            "  SELECT r.* FROM role r " +
            "  INNER JOIN role_hierarchy rh ON r.id = rh.parent_role_id" +
            ") SELECT * FROM role_hierarchy WHERE status = 1")
    List<Role> selectRoleHierarchy(@Param("roleId") Long roleId);
}
