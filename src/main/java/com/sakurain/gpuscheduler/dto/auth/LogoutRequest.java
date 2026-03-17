package com.sakurain.gpuscheduler.dto.auth;

import lombok.Data;

/**
 * 登出请求 DTO
 */
@Data
public class LogoutRequest {

    /**
     * 刷新令牌（可选）；提供时一并加入黑名单
     */
    private String refreshToken;
}
