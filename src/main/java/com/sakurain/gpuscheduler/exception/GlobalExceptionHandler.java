package com.sakurain.gpuscheduler.exception;

import com.sakurain.gpuscheduler.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException ex) {
        log.warn("业务异常: code={}, message={}", ex.getCode(), ex.getMessage());
        return Result.<Void>builder()
                .code(ex.getHttpStatus())
                .message(ex.getMessage())
                .build();
    }

    /**
     * 处理请求频率超限异常
     */
    @ExceptionHandler(RateLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Result<Void> handleRateLimitException(RateLimitException ex) {
        log.warn("请求频率超限: {}", ex.getMessage());
        return Result.<Void>builder()
                .code(429)
                .message(ex.getMessage())
                .build();
    }

    /**
     * 处理 Spring Security 认证异常
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleAuthenticationException(AuthenticationException ex) {
        log.warn("认证失败: {}", ex.getMessage());

        String message = "认证失败";
        if (ex instanceof BadCredentialsException) {
            message = "用户名或密码错误";
        } else if (ex instanceof DisabledException) {
            message = "账户已被禁用";
        } else if (ex instanceof InsufficientAuthenticationException) {
            message = "未提供有效的认证凭证";
        }

        return Result.<Void>builder()
                .code(401)
                .message(message)
                .build();
    }

    /**
     * 处理 Spring Security 授权异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("权限不足: {}", ex.getMessage());
        return Result.<Void>builder()
                .code(403)
                .message("权限不足，无法访问该资源")
                .build();
    }

    /**
     * 处理参数验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Map<String, String>> handleValidationException(MethodArgumentNotValidException ex) {
        log.warn("参数验证失败: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        return Result.<Map<String, String>>builder()
                .code(400)
                .message("参数验证失败")
                .data(errors)
                .build();
    }

    /**
     * 处理通用异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception ex) {
        log.error("系统异常: ", ex);
        return Result.<Void>builder()
                .code(500)
                .message("系统内部错误，请稍后重试")
                .build();
    }
}
