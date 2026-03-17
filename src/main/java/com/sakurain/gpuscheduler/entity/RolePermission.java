package com.sakurain.gpuscheduler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 角色-权限关联实体类（多对多）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("role_permission")
public class RolePermission {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 角色ID
     */
    @TableField("role_id")
    private Long roleId;

    /**
     * 权限ID
     */
    @TableField("permission_id")
    private Long permissionId;

    /**
     * 授权操作者用户ID
     */
    @TableField("granted_by")
    private Long grantedBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
