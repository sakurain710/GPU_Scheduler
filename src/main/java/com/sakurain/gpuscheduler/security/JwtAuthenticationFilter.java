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
 * JWT认证过滤器
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final JwtConfig jwtConfig;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    @Autowired
    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   JwtConfig jwtConfig,
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
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtUtil.validateToken(jwt)) {
                // 只允许 access token 进入鉴权流程
                if (!jwtUtil.isAccessToken(jwt)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                if (tokenBlacklistService.isBlacklisted(jwt)) {
                    log.warn("令牌已被吊销，拒绝访问");
                    filterChain.doFilter(request, response);
                    return;
                }

                Long userId = jwtUtil.getUserIdFromToken(jwt);
                UserDetails userDetails = userDetailsService.loadUserById(userId);

                if (userDetails.isEnabled()) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    log.warn("用户已被禁用: {}", userId);
                }
            }
        } catch (Exception ex) {
            log.error("JWT认证失败: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(jwtConfig.getHeaderName());
        return jwtUtil.extractTokenFromHeader(bearerToken);
    }
}
