package com.sakurain.gpuscheduler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 角色实体类 - 权限的命名集合，支持层级结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("role")
public class Role {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 角色唯一编码，例如 "ROLE_ADMIN"
     */
    @TableField("code")
    private String code;

    /**
     * 角色名称
     */
    @TableField("name")
    private String name;

    /**
     * 父角色ID（层级RBAC）
     */
    @TableField("parent_role_id")
    private Long parentRoleId;

    /**
     * 角色类型：1=系统角色 2=自定义角色 3=临时角色
     */
    @TableField("role_type")
    private Integer roleType;

    /**
     * 显示排序
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 角色描述
     */
    @TableField("description")
    private String description;

    /**
     * 状态：1=启用 0=禁用
     */
    @TableField("status")
    private Integer status;

    /**
     * 创建者用户ID
     */
    @TableField("created_by")
    private Long createdBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
