package com.sakurain.gpuscheduler.dto.role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分配用户请求 DTO
 */
@Schema(description = "Assign users request")
@Data
public class AssignUsersRequest {

    @Schema(description = "Role id", example = "1")
    @NotNull(message = "角色ID不能为空")
    private Long roleId;

    @Schema(description = "User id list")
    @NotEmpty(message = "用户ID列表不能为空")
    private List<Long> userIds;

    /**
     * 过期时间（可选）
     */
    @Schema(description = "Optional expiry time")
    private LocalDateTime expiresAt;
}
