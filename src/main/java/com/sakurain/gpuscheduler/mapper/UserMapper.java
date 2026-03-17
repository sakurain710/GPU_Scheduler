package com.sakurain.gpuscheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakurain.gpuscheduler.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户Mapper接口
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据用户名查询用户（排除软删除）
     */
    @Select("SELECT * FROM user WHERE username = #{username} AND deleted_at IS NULL")
    User selectByUsername(@Param("username") String username);

    /**
     * 根据邮箱查询用户（排除软删除）
     */
    @Select("SELECT * FROM user WHERE email = #{email} AND deleted_at IS NULL")
    User selectByEmail(@Param("email") String email);

    /**
     * 根据角色ID查询该角色下的所有用户
     */
    @Select("SELECT u.* FROM user u " +
            "INNER JOIN user_role ur ON u.id = ur.user_id " +
            "WHERE ur.role_id = #{roleId} " +
            "AND u.deleted_at IS NULL " +
            "AND (ur.expires_at IS NULL OR ur.expires_at > NOW())")
    List<User> selectByRoleId(@Param("roleId") Long roleId);

    /**
     * 分页查询用户列表（支持按用户名、邮箱、状态过滤）
     */
    @Select("<script>" +
            "SELECT u.* FROM user u WHERE u.deleted_at IS NULL" +
            "<if test='username != null and username != \"\"'>" +
            " AND u.username LIKE CONCAT('%', #{username}, '%')" +
            "</if>" +
            "<if test='email != null and email != \"\"'>" +
            " AND u.email LIKE CONCAT('%', #{email}, '%')" +
            "</if>" +
            "<if test='status != null'>" +
            " AND u.status = #{status}" +
            "</if>" +
            " ORDER BY u.created_at DESC" +
            "</script>")
    IPage<User> selectPageWithFilter(Page<User> page,
                                     @Param("username") String username,
                                     @Param("email") String email,
                                     @Param("status") Integer status);
}
