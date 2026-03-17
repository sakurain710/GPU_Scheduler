package com.sakurain.gpuscheduler.security;

import com.sakurain.gpuscheduler.config.JwtConfig;
import com.sakurain.gpuscheduler.service.TokenBlacklistService;
import com.sakurain.gpuscheduler.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器
 * 从请求头中提取 JWT token，验证并设置用户认证信息到 SecurityContext
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final JwtConfig jwtConfig;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    @Autowired
    public JwtAuthenticationFilter(JwtUtil jwtUtil, JwtConfig jwtConfig,
                                   CustomUserDetailsService userDetailsService,
                                   TokenBlacklistService tokenBlacklistService) {
        this.jwtUtil = jwtUtil;
        this.jwtConfig = jwtConfig;
        this.userDetailsService = userDetailsService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            // 从请求头中获取 JWT token
            String jwt = getJwtFromRequest(request);

            // 验证 token 是否有效
            if (StringUtils.hasText(jwt) && jwtUtil.validateToken(jwt)) {
                // 检查令牌是否已被吊销（加入黑名单）
                if (tokenBlacklistService.isBlacklisted(jwt)) {
                    log.warn("令牌已被吊销，拒绝访问");
                    filterChain.doFilter(request, response);
                    return;
                }

                // 从 token 中获取用户ID
                Long userId = jwtUtil.getUserIdFromToken(jwt);

                // 加载用户信息
                UserDetails userDetails = userDetailsService.loadUserById(userId);

                // 检查用户是否已启用
                if (userDetails.isEnabled()) {
                    // 创建认证对象
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // 设置认证信息到 SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("JWT 认证成功，用户ID: {}", userId);
                } else {
                    log.warn("用户已被禁用: {}", userId);
                }
            }
        } catch (Exception ex) {
            log.error("JWT 认证失败: {}", ex.getMessage());
        }

        // 继续过滤器链
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头中提取 JWT token
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(jwtConfig.getHeaderName());
        return jwtUtil.extractTokenFromHeader(bearerToken);
    }
}
