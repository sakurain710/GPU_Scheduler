package com.sakurain.gpuscheduler.dto.role;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 分配权限请求 DTO
 */
@Data
public class AssignPermissionsRequest {

    @NotNull(message = "角色ID不能为空")
    private Long roleId;

    @NotEmpty(message = "权限ID列表不能为空")
    private List<Long> permissionIds;
}
