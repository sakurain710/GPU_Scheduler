package com.sakurain.gpuscheduler.dto.role;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 更新角色请求 DTO
 */
@Data
public class UpdateRoleRequest {

    @Size(max = 100, message = "角色名称长度不能超过100")
    private String name;

    private String description;

    private Integer roleType;

    private Integer status;

    private Integer sortOrder;

    private Long parentRoleId;

    private List<Long> permissionIds;
}
