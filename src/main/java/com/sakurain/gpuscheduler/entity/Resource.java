package com.sakurain.gpuscheduler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 资源实体类 - 系统受保护的资源（菜单、API、按钮、数据范围）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("resource")
public class Resource {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 资源唯一编码，例如 "user:list"
     */
    @TableField("code")
    private String code;

    /**
     * 资源名称
     */
    @TableField("name")
    private String name;

    /**
     * 资源类型：1=菜单 2=API 3=按钮 4=数据
     */
    @TableField("type")
    private Integer type;

    /**
     * 父资源ID（树形结构）
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * URL路径或菜单路由
     */
    @TableField("path")
    private String path;

    /**
     * 显示排序
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 资源描述
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
