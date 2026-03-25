package com.sakurain.gpuscheduler.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 登录响应 DTO
 */
@Schema(description = "Login response")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * 访问令牌
     */
    @Schema(description = "Access token")
    private String accessToken;

    /**
     * 刷新令牌
     */
    @Schema(description = "Refresh token")
    private String refreshToken;

    /**
     * 令牌类型
     */
    @Schema(description = "Token type", example = "Bearer")
    private String tokenType;

    /**
     * 过期时间（秒）
     */
    @Schema(description = "Access token expires in seconds", example = "86400")
    private Long expiresIn;

    /**
     * 用户ID
     */
    @Schema(description = "User id", example = "1")
    private Long userId;

    /**
     * 用户名
     */
    @Schema(description = "Username", example = "alice")
    private String username;

    /**
     * 角色列表
     */
    @Schema(description = "Role list")
    private List<String> roles;
}
