package com.sakurain.gpuscheduler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user")
public class User {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 登录名（唯一）
     */
    @TableField("username")
    private String username;

    /**
     * 加密密码（bcrypt / argon2）
     */
    @TableField("password")
    private String password;

    /**
     * 显示昵称
     */
    @TableField("nickname")
    private String nickname;

    /**
     * 邮箱地址
     */
    @TableField("email")
    private String email;

    /**
     * 手机号
     */
    @TableField("mobile")
    private String mobile;

    /**
     * 头像URL
     */
    @TableField("avatar")
    private String avatar;

    /**
     * 性别：0=未知 1=男 2=女
     */
    @TableField("gender")
    private Integer gender;

    /**
     * 用户类型：1=普通用户 2=管理员 3=超级管理员
     */
    @TableField("user_type")
    private Integer userType;

    /**
     * 状态：1=正常 0=禁用 2=锁定
     */
    @TableField("status")
    private Integer status;

    /**
     * 最后登录IP
     */
    @TableField("login_ip")
    private String loginIp;

    /**
     * 最后登录时间
     */
    @TableField("login_at")
    private LocalDateTime loginAt;

    /**
     * 最后密码重置时间
     */
    @TableField("pwd_reset_at")
    private LocalDateTime pwdResetAt;

    /**
     * 备注
     */
    @TableField("remark")
    private String remark;

    /**
     * 创建者用户ID
     */
    @TableField("created_by")
    private Long createdBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 软删除时间戳
     */
    @TableLogic
    @TableField("deleted_at")
    private LocalDateTime deletedAt;
}
