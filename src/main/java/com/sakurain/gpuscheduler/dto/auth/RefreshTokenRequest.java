package com.sakurain.gpuscheduler.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新令牌请求 DTO
 */
@Schema(description = "Refresh token request")
@Data
public class RefreshTokenRequest {

    @Schema(description = "Refresh token")
    @NotBlank(message = "刷新令牌不能为空")
    private String refreshToken;
}
