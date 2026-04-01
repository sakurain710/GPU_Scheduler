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
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final TokenBlacklistService tokenBlacklistService;

    @Autowired
    public AuthService(AuthenticationManager authenticationManager,
                       JwtUtil jwtUtil,
                       UserMapper userMapper,
                       RoleMapper roleMapper,
                       TokenBlacklistService tokenBlacklistService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    public LoginResponse login(LoginRequest request) {
        log.info("用户尝试登录: username={}", request.getUsername());
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            Long userId = userDetails.getUserId();
            String username = userDetails.getUsername();
            List<String> roles = userDetails.getRoleCodes();

            String accessToken = jwtUtil.generateAccessToken(userId, username, roles);
            String refreshToken = jwtUtil.generateRefreshToken(userId, username);

            log.info("用户登录成功: userId={}, username={}, roles={}", userId, username, roles);

            return LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(86400L)
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

    public LoginResponse refreshToken(String refreshToken) {
        log.info("尝试刷新令牌");
        try {
            if (!jwtUtil.validateToken(refreshToken)) {
                log.warn("令牌刷新失败 - 无效的刷新令牌");
                throw new InvalidTokenException("无效的刷新令牌");
            }
            if (!jwtUtil.isRefreshToken(refreshToken)) {
                log.warn("令牌刷新失败 - 令牌类型不是 refresh");
                throw new InvalidTokenException("令牌类型错误");
            }
            if (tokenBlacklistService.isBlacklisted(refreshToken)) {
                log.warn("令牌刷新失败 - 刷新令牌已被吊销");
                throw new InvalidTokenException("刷新令牌已被吊销");
            }

            Long userId = jwtUtil.getUserIdFromToken(refreshToken);
            String username = jwtUtil.getUsernameFromToken(refreshToken);

            User user = userMapper.selectById(userId);
            if (user == null) {
                log.warn("令牌刷新失败 - 用户不存在: userId={}", userId);
                throw new ResourceNotFoundException("用户不存在");
            }
            if (user.getStatus() != 1) {
                log.warn("令牌刷新失败 - 用户已被禁用: userId={}, status={}", userId, user.getStatus());
                throw new UserDisabledException("用户已被禁用");
            }

            List<Role> roles = roleMapper.selectByUserId(userId);
            List<String> roleCodes = roles.stream().map(Role::getCode).collect(Collectors.toList());

            String newAccessToken = jwtUtil.generateAccessToken(userId, username, roleCodes);
            String newRefreshToken = jwtUtil.generateRefreshToken(userId, username);
            Date refreshTokenExpiration = jwtUtil.getExpirationDateFromToken(refreshToken);

            tokenBlacklistService.blacklistToken(refreshToken, refreshTokenExpiration);
            tokenBlacklistService.revokeAccessTokensIssuedBefore(userId, new Date(), refreshTokenExpiration);

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

    public void logout(String accessToken, String refreshToken) {
        if (!StringUtils.hasText(accessToken) && !StringUtils.hasText(refreshToken)) {
            throw new InvalidTokenException("缺少登出令牌");
        }

        if (StringUtils.hasText(accessToken) && jwtUtil.validateToken(accessToken)) {
            if (tokenBlacklistService.isBlacklisted(accessToken)) {
                throw new InvalidTokenException("令牌已失效或已登出");
            }

            Long userId = jwtUtil.getUserIdFromToken(accessToken);
            Date expiration = jwtUtil.getExpirationDateFromToken(accessToken);
            tokenBlacklistService.blacklistToken(accessToken, expiration);

            if (jwtUtil.isRefreshToken(accessToken)) {
                tokenBlacklistService.revokeAccessTokensIssuedBefore(userId, new Date(), expiration);
            } else if (!jwtUtil.isAccessToken(accessToken)) {
                throw new InvalidTokenException("令牌类型错误");
            }
        }

        if (StringUtils.hasText(refreshToken) && jwtUtil.validateToken(refreshToken)) {
            if (!jwtUtil.isRefreshToken(refreshToken)) {
                throw new InvalidTokenException("刷新令牌类型错误");
            }
            if (tokenBlacklistService.isBlacklisted(refreshToken)) {
                throw new InvalidTokenException("令牌已失效或已登出");
            }

            Long userId = jwtUtil.getUserIdFromToken(refreshToken);
            Date refreshExpiration = jwtUtil.getExpirationDateFromToken(refreshToken);
            tokenBlacklistService.blacklistToken(refreshToken, refreshExpiration);
            tokenBlacklistService.revokeAccessTokensIssuedBefore(userId, new Date(), refreshExpiration);
        }

        SecurityContextHolder.clearContext();
        log.info("用户登出成功，令牌已加入黑名单");
    }

    public UserResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InvalidTokenException("用户未登录");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof CustomUserDetails userDetails)) {
            log.warn("鉴权主体类型非法: principalType={}", principal == null ? "null" : principal.getClass().getName());
            throw new InvalidTokenException("令牌无效或已失效");
        }

        Long userId = userDetails.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("用户不存在");
        }

        List<Role> roles = roleMapper.selectByUserId(userId);
        List<String> roleCodes = roles.stream().map(Role::getCode).collect(Collectors.toList());

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
