package com.sakurain.gpuscheduler.dto.role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 分配权限请求 DTO
 */
@Schema(description = "Assign permissions request")
@Data
public class AssignPermissionsRequest {

    @Schema(description = "Role id", example = "1")
    @NotNull(message = "角色ID不能为空")
    private Long roleId;

    @Schema(description = "Permission id list")
    @NotEmpty(message = "权限ID列表不能为空")
    private List<Long> permissionIds;
}
