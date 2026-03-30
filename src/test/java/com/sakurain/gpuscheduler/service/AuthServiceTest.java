package com.sakurain.gpuscheduler.service;

import com.sakurain.gpuscheduler.dto.auth.LoginResponse;
import com.sakurain.gpuscheduler.entity.Role;
import com.sakurain.gpuscheduler.entity.User;
import com.sakurain.gpuscheduler.mapper.RoleMapper;
import com.sakurain.gpuscheduler.mapper.UserMapper;
import com.sakurain.gpuscheduler.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private TokenBlacklistService tokenBlacklistService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                authenticationManager,
                jwtUtil,
                userMapper,
                roleMapper,
                tokenBlacklistService
        );
    }

    @Test
    void refreshToken_shouldRevokeOldRefreshAndOldAccessTokens() {
        String oldRefreshToken = "old.refresh.token";
        Date expiration = new Date(System.currentTimeMillis() + 60_000);

        when(jwtUtil.validateToken(oldRefreshToken)).thenReturn(true);
        when(jwtUtil.isRefreshToken(oldRefreshToken)).thenReturn(true);
        when(tokenBlacklistService.isBlacklisted(oldRefreshToken)).thenReturn(false);
        when(jwtUtil.getUserIdFromToken(oldRefreshToken)).thenReturn(1L);
        when(jwtUtil.getUsernameFromToken(oldRefreshToken)).thenReturn("alice");
        when(userMapper.selectById(1L)).thenReturn(User.builder().id(1L).status(1).build());
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(Role.builder().code("ROLE_USER").build()));
        when(jwtUtil.generateAccessToken(1L, "alice", List.of("ROLE_USER"))).thenReturn("new-access");
        when(jwtUtil.generateRefreshToken(1L, "alice")).thenReturn("new-refresh");
        when(jwtUtil.getExpirationDateFromToken(oldRefreshToken)).thenReturn(expiration);

        LoginResponse response = authService.refreshToken(oldRefreshToken);

        assertEquals("new-access", response.getAccessToken());
        assertEquals("new-refresh", response.getRefreshToken());
        verify(tokenBlacklistService).blacklistToken(oldRefreshToken, expiration);
        verify(tokenBlacklistService).revokeAccessTokensIssuedBefore(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(Date.class), org.mockito.ArgumentMatchers.eq(expiration));
    }
}
