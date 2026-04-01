package com.sakurain.gpuscheduler.security;

import com.sakurain.gpuscheduler.config.JwtConfig;
import com.sakurain.gpuscheduler.service.TokenBlacklistService;
import com.sakurain.gpuscheduler.util.JwtUtil;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private JwtConfig jwtConfig;
    @Mock
    private CustomUserDetailsService userDetailsService;
    @Mock
    private TokenBlacklistService tokenBlacklistService;
    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtUtil, jwtConfig, userDetailsService, tokenBlacklistService);
        SecurityContextHolder.clearContext();
    }

    @Test
    void revokedByRefreshCutoff_shouldNotAuthenticateOldAccessToken() throws Exception {
        String token = "old-access-token";
        Date issuedAt = new Date(System.currentTimeMillis() - 10_000);

        when(jwtConfig.getHeaderName()).thenReturn("Authorization");
        when(jwtUtil.extractTokenFromHeader("Bearer " + token)).thenReturn(token);
        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.isAccessToken(token)).thenReturn(true);
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(jwtUtil.getUserIdFromToken(token)).thenReturn(1L);
        when(jwtUtil.getIssuedAtFromToken(token)).thenReturn(issuedAt);
        when(tokenBlacklistService.isAccessTokenRevokedByRefresh(1L, issuedAt)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        verify(userDetailsService, never()).loadUserById(1L);
        verify(filterChain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void refreshTokenUsedOnProtectedApi_shouldReturn401WithTokenTypeError() throws Exception {
        String token = "refresh-token";

        when(jwtConfig.getHeaderName()).thenReturn("Authorization");
        when(jwtUtil.extractTokenFromHeader("Bearer " + token)).thenReturn(token);
        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.isAccessToken(token)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"message\":\"令牌类型错误\"");
    }
}
