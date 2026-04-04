package com.sakurain.gpuscheduler.dto.role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 更新角色请求 DTO
 */
@Schema(description = "Update role request")
@Data
public class UpdateRoleRequest {

    @Schema(description = "Role name")
    @Size(max = 100, message = "角色名称长度不能超过100")
    private String name;

    private String description;

    @Schema(description = "Role type: 1=System, 2=Custom, 3=Temporary", example = "2")
    @Min(value = 1, message = "roleType最小值为1")
    @Max(value = 3, message = "roleType最大值为3")
    private Integer roleType;

    private Integer status;

    private Integer sortOrder;

    private Long parentRoleId;

    private List<Long> permissionIds;
}
