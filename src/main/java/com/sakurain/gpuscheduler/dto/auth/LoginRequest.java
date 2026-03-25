package com.sakurain.gpuscheduler.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求 DTO
 */
@Schema(description = "Login request")
@Data
public class LoginRequest {

    @Schema(description = "Username", example = "alice")
    @NotBlank(message = "用户名不能为空")
    private String username;

    @Schema(description = "Password", example = "P@ssw0rd")
    @NotBlank(message = "密码不能为空")
    private String password;
}
