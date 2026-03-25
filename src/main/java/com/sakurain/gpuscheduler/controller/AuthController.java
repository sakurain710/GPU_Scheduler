package com.sakurain.gpuscheduler.controller;

import com.sakurain.gpuscheduler.config.JwtConfig;
import com.sakurain.gpuscheduler.dto.Result;
import com.sakurain.gpuscheduler.dto.auth.LoginRequest;
import com.sakurain.gpuscheduler.dto.auth.LoginResponse;
import com.sakurain.gpuscheduler.dto.auth.LogoutRequest;
import com.sakurain.gpuscheduler.dto.auth.RefreshTokenRequest;
import com.sakurain.gpuscheduler.dto.user.UserResponse;
import com.sakurain.gpuscheduler.service.AuthService;
import com.sakurain.gpuscheduler.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器
 */
@Slf4j
@Tag(name = "Authentication", description = "Login, token refresh, logout, and current user info")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final JwtConfig jwtConfig;

    @Autowired
    public AuthController(AuthService authService, JwtUtil jwtUtil, JwtConfig jwtConfig) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
        this.jwtConfig = jwtConfig;
    }

    /**
     * 用户登录
     */
    @Operation(summary = "User login", description = "Returns access token and refresh token")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return Result.success(response);
    }

    /**
     * 刷新令牌
     */
    @Operation(summary = "Refresh access token", description = "Use refresh token to issue a new token pair")
    @PostMapping("/refresh")
    public Result<LoginResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse response = authService.refreshToken(request.getRefreshToken());
        return Result.success(response);
    }

    /**
     * 用户登出
     * 将当前访问令牌和刷新令牌加入黑名单
     */
    @Operation(
            summary = "User logout",
            description = "Blacklist access token and optional refresh token",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody(required = false) LogoutRequest request,
                               @Parameter(hidden = true) HttpServletRequest httpRequest) {
        String accessToken = jwtUtil.extractTokenFromHeader(
                httpRequest.getHeader(jwtConfig.getHeaderName()));
        String refreshToken = (request != null) ? request.getRefreshToken() : null;
        authService.logout(accessToken, refreshToken);
        return Result.success("登出成功", null);
    }

    /**
     * 获取当前登录用户信息
     */
    @Operation(
            summary = "Get current user",
            description = "Returns profile info of the current authenticated user",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/me")
    public Result<UserResponse> getCurrentUser() {
        UserResponse response = authService.getCurrentUser();
        return Result.success(response);
    }
}
