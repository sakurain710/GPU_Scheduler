package com.sakurain.gpuscheduler.dto.role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建角色请求 DTO
 */
@Schema(description = "Create role request")
@Data
public class CreateRoleRequest {

    @Schema(description = "Role code", example = "ADMIN")
    @NotBlank(message = "角色编码不能为空")
    @Size(max = 50, message = "角色编码长度不能超过50")
    private String code;

    @Schema(description = "Role name", example = "Administrator")
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称长度不能超过100")
    private String name;

    @Schema(description = "Role description")
    private String description;

    @Schema(description = "Role type")
    private Integer roleType = 1;

    @Schema(description = "Status: 0=disabled,1=enabled")
    private Integer status = 1;

    @Schema(description = "Sort order")
    private Integer sortOrder = 0;

    @Schema(description = "Parent role id")
    private Long parentRoleId;

    @Schema(description = "Permission id list")
    private List<Long> permissionIds;
}
