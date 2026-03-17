package com.sakurain.gpuscheduler.dto.role;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分配用户请求 DTO
 */
@Data
public class AssignUsersRequest {

    @NotNull(message = "角色ID不能为空")
    private Long roleId;

    @NotEmpty(message = "用户ID列表不能为空")
    private List<Long> userIds;

    /**
     * 过期时间（可选）
     */
    private LocalDateTime expiresAt;
}
