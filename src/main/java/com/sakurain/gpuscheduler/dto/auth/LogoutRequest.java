package com.sakurain.gpuscheduler.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登出请求 DTO
 */
@Schema(description = "Logout request")
@Data
public class LogoutRequest {

    /**
     * 刷新令牌（可选）；提供时一并加入黑名单
     */
    @Schema(description = "Optional refresh token to blacklist")
    private String refreshToken;
}
