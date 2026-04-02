package com.sakurain.gpuscheduler.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakurain.gpuscheduler.dto.Result;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * JWT 认证入口点
 * 处理未认证用户访问受保护资源时的响应
 */
@Slf4j
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        log.error("未授权访问: path={}, reason={}", request.getRequestURI(), authException.getMessage());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");

        ObjectMapper objectMapper = new ObjectMapper();
        Result<Void> result = Result.error(
                HttpServletResponse.SC_UNAUTHORIZED,
                "AUTH_UNAUTHORIZED",
                "未授权访问，请先登录"
        );
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
