package com.sakurain.gpuscheduler.dto.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建角色请求 DTO
 */
@Data
public class CreateRoleRequest {

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 50, message = "角色编码长度不能超过50")
    private String code;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称长度不能超过100")
    private String name;

    private String description;

    private Integer roleType = 1;

    private Integer status = 1;

    private Integer sortOrder = 0;

    private Long parentRoleId;

    private List<Long> permissionIds;
}
