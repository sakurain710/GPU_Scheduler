package com.sakurain.gpuscheduler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 权限实体类 - 资源上可执行的操作
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("permission")
public class Permission {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 权限唯一编码，例如 "user:list:view"
     */
    @TableField("code")
    private String code;

    /**
     * 权限名称
     */
    @TableField("name")
    private String name;

    /**
     * 关联资源ID
     */
    @TableField("resource_id")
    private Long resourceId;

    /**
     * 操作类型：view / create / edit / delete / export
     */
    @TableField("action")
    private String action;

    /**
     * 权限描述
     */
    @TableField("description")
    private String description;

    /**
     * 状态：1=启用 0=禁用
     */
    @TableField("status")
    private Integer status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
