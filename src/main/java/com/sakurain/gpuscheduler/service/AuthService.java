package com.sakurain.gpuscheduler.service;

import com.sakurain.gpuscheduler.dto.auth.LoginRequest;
import com.sakurain.gpuscheduler.dto.auth.LoginResponse;
import com.sakurain.gpuscheduler.dto.user.UserResponse;
import com.sakurain.gpuscheduler.entity.Role;
import com.sakurain.gpuscheduler.entity.User;
import com.sakurain.gpuscheduler.exception.InvalidTokenException;
import com.sakurain.gpuscheduler.exception.ResourceNotFoundException;
import com.sakurain.gpuscheduler.exception.UserDisabledException;
import com.sakurain.gpuscheduler.mapper.RoleMapper;
import com.sakurain.gpuscheduler.mapper.UserMapper;
import com.sakurain.gpuscheduler.security.CustomUserDetails;
import com.sakurain.gpuscheduler.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 认证服务
 */
@Slf4j
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    @Autowired
    public AuthService(AuthenticationManager authenticationManager,
                       JwtUtil jwtUtil,
                       UserMapper userMapper,
                       RoleMapper roleMapper) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
    }

    /**
     * 用户登录
     */
    public LoginResponse login(LoginRequest request) {
        log.info("用户尝试登录: username={}", request.getUsername());

        try {
            // 执行认证
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // 获取用户详情
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            Long userId = userDetails.getUserId();
            String username = userDetails.getUsername();
            List<String> roles = userDetails.getRoleCodes();

            // 生成 JWT tokens
            String accessToken = jwtUtil.generateAccessToken(userId, username, roles);
            String refreshToken = jwtUtil.generateRefreshToken(userId, username);

            log.info("用户登录成功: userId={}, username={}, roles={}", userId, username, roles);

            return LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(86400L) // 24小时
                    .userId(userId)
                    .username(username)
                    .roles(roles)
                    .build();
        } catch (BadCredentialsException ex) {
            log.warn("用户登录失败 - 用户名或密码错误: username={}", request.getUsername());
            throw ex;
        } catch (Exception ex) {
            log.error("用户登录失败 - 系统异常: username={}", request.getUsername(), ex);
            throw ex;
        }
    }

    /**
     * 刷新令牌
     */
    public LoginResponse refreshToken(String refreshToken) {
        log.info("尝试刷新令牌");

        try {
            // 验证 refresh token
            if (!jwtUtil.validateToken(refreshToken)) {
                log.warn("令牌刷新失败 - 无效的刷新令牌");
                throw new InvalidTokenException("无效的刷新令牌");
            }

            // 从 token 中获取用户信息
            Long userId = jwtUtil.getUserIdFromToken(refreshToken);
            String username = jwtUtil.getUsernameFromToken(refreshToken);

            // 验证用户是否存在且状态正常
            User user = userMapper.selectById(userId);
            if (user == null) {
                log.warn("令牌刷新失败 - 用户不存在: userId={}", userId);
                throw new ResourceNotFoundException("用户不存在");
            }
            if (user.getStatus() != 1) {
                log.warn("令牌刷新失败 - 用户已被禁用: userId={}, status={}", userId, user.getStatus());
                throw new UserDisabledException("用户已被禁用");
            }

            // 查询用户角色
            List<Role> roles = roleMapper.selectByUserId(userId);
            List<String> roleCodes = roles.stream()
                    .map(Role::getCode)
                    .collect(Collectors.toList());

            // 生成新的 tokens
            String newAccessToken = jwtUtil.generateAccessToken(userId, username, roleCodes);
            String newRefreshToken = jwtUtil.generateRefreshToken(userId, username);

            log.info("令牌刷新成功: userId={}, username={}", userId, username);

            return LoginResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .tokenType("Bearer")
                    .expiresIn(86400L)
                    .userId(userId)
                    .username(username)
                    .roles(roleCodes)
                    .build();
        } catch (InvalidTokenException | ResourceNotFoundException | UserDisabledException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("令牌刷新失败 - 系统异常", ex);
            throw new InvalidTokenException("令牌刷新失败", ex);
        }
    }

    /**
     * 获取当前登录用户信息
     */
    public UserResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("获取当前用户失败 - 用户未登录");
            throw new InvalidTokenException("用户未登录");
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long userId = userDetails.getUserId();

        log.debug("获取当前用户信息: userId={}", userId);

        // 查询用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.error("获取当前用户失败 - 用户不存在: userId={}", userId);
            throw new ResourceNotFoundException("用户不存在");
        }

        // 查询用户角色
        List<Role> roles = roleMapper.selectByUserId(userId);
        List<String> roleCodes = roles.stream()
                .map(Role::getCode)
                .collect(Collectors.toList());

        log.debug("当前用户信息获取成功: userId={}, username={}", userId, user.getUsername());

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .status(user.getStatus())
                .roles(roleCodes)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
