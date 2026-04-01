package com.sakurain.gpuscheduler.service;

import com.sakurain.gpuscheduler.dto.auth.LoginResponse;
import com.sakurain.gpuscheduler.entity.Role;
import com.sakurain.gpuscheduler.entity.User;
import com.sakurain.gpuscheduler.exception.InvalidTokenException;
import com.sakurain.gpuscheduler.mapper.RoleMapper;
import com.sakurain.gpuscheduler.mapper.UserMapper;
import com.sakurain.gpuscheduler.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

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

    @Test
    void refreshToken_shouldRevokeBeforeIssuingNewAccessToken() {
        String oldRefreshToken = "old.refresh.token";
        Date expiration = new Date(System.currentTimeMillis() + 60_000);

        when(jwtUtil.validateToken(oldRefreshToken)).thenReturn(true);
        when(jwtUtil.isRefreshToken(oldRefreshToken)).thenReturn(true);
        when(tokenBlacklistService.isBlacklisted(oldRefreshToken)).thenReturn(false);
        when(jwtUtil.getUserIdFromToken(oldRefreshToken)).thenReturn(1L);
        when(jwtUtil.getUsernameFromToken(oldRefreshToken)).thenReturn("alice");
        when(userMapper.selectById(1L)).thenReturn(User.builder().id(1L).status(1).build());
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(Role.builder().code("ROLE_USER").build()));
        when(jwtUtil.getExpirationDateFromToken(oldRefreshToken)).thenReturn(expiration);
        when(jwtUtil.generateAccessToken(1L, "alice", List.of("ROLE_USER"))).thenReturn("new-access");
        when(jwtUtil.generateRefreshToken(1L, "alice")).thenReturn("new-refresh");

        authService.refreshToken(oldRefreshToken);

        InOrder inOrder = inOrder(tokenBlacklistService, jwtUtil);
        inOrder.verify(tokenBlacklistService).revokeAccessTokensIssuedBefore(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(Date.class),
                org.mockito.ArgumentMatchers.eq(expiration)
        );
        inOrder.verify(jwtUtil).generateAccessToken(1L, "alice", List.of("ROLE_USER"));
    }

    @Test
    void logout_shouldRejectAlreadyBlacklistedToken() {
        String blacklistedAccess = "access.token";
        when(jwtUtil.validateToken(blacklistedAccess)).thenReturn(true);
        when(tokenBlacklistService.isBlacklisted(blacklistedAccess)).thenReturn(true);

        assertThrows(InvalidTokenException.class, () -> authService.logout(blacklistedAccess, null));
    }

    @Test
    void logout_withRefreshTokenInAuthHeader_shouldRevokeExistingAccessTokens() {
        String refreshToken = "refresh.token";
        Date expiration = new Date(System.currentTimeMillis() + 60_000);

        when(jwtUtil.validateToken(refreshToken)).thenReturn(true);
        when(tokenBlacklistService.isBlacklisted(refreshToken)).thenReturn(false);
        when(jwtUtil.getUserIdFromToken(refreshToken)).thenReturn(7L);
        when(jwtUtil.getExpirationDateFromToken(refreshToken)).thenReturn(expiration);
        when(jwtUtil.isRefreshToken(refreshToken)).thenReturn(true);

        authService.logout(refreshToken, null);

        verify(tokenBlacklistService).blacklistToken(refreshToken, expiration);
        verify(tokenBlacklistService).revokeAccessTokensIssuedBefore(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.any(Date.class),
                org.mockito.ArgumentMatchers.eq(expiration)
        );
    }

    @Test
    void getCurrentUser_whenPrincipalIsString_shouldThrowInvalidTokenException() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of())
        );

        assertThrows(InvalidTokenException.class, () -> authService.getCurrentUser());

        SecurityContextHolder.clearContext();
    }
}
