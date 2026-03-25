package com.sakurain.gpuscheduler.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import lombok.Data;

/**
 * 更新用户请求 DTO
 */
@Schema(description = "Update user request")
@Data
public class UpdateUserRequest {

    @Schema(description = "Email", example = "alice@example.com")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(description = "Status: 0=disabled,1=enabled", example = "1")
    private Integer status;
}
