package com.sakurain.gpuscheduler.exception;

import com.sakurain.gpuscheduler.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex) {
        log.warn("业务异常: code={}, message={}", ex.getCode(), ex.getMessage());

        HttpStatus status = HttpStatus.resolve(ex.getHttpStatus());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }

        Result<Void> body = Result.<Void>builder()
                .code(ex.getHttpStatus())
                .errorCode(ex.getCode())
                .message(ex.getMessage())
                .build();

        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(RateLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Result<Void> handleRateLimitException(RateLimitException ex) {
        log.warn("请求频率超限: {}", ex.getMessage());
        return Result.<Void>builder()
                .code(429)
                .errorCode("RATE_LIMIT_EXCEEDED")
                .message(ex.getMessage())
                .build();
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleAuthenticationException(AuthenticationException ex) {
        log.warn("认证失败: {}", ex.getMessage());

        String message = "认证失败";
        String errorCode = "AUTH_UNAUTHORIZED";
        if (ex instanceof BadCredentialsException) {
            message = "用户名或密码错误";
            errorCode = "AUTH_BAD_CREDENTIALS";
        } else if (ex instanceof DisabledException) {
            message = "账户已被禁用";
            errorCode = "AUTH_ACCOUNT_DISABLED";
        } else if (ex instanceof InsufficientAuthenticationException) {
            message = "未提供有效的认证凭证";
            errorCode = "AUTH_CREDENTIAL_MISSING";
        }

        return Result.<Void>builder()
                .code(401)
                .errorCode(errorCode)
                .message(message)
                .build();
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("权限不足: {}", ex.getMessage());
        return Result.<Void>builder()
                .code(403)
                .errorCode("AUTH_ACCESS_DENIED")
                .message("权限不足，无法访问该资源")
                .build();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Map<String, String>> handleValidationException(MethodArgumentNotValidException ex) {
        log.warn("参数校验失败: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        return Result.<Map<String, String>>builder()
                .code(400)
                .errorCode("COMMON_VALIDATION_ERROR")
                .message("参数校验失败")
                .data(errors)
                .build();
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception ex) {
        log.error("系统异常: ", ex);
        return Result.<Void>builder()
                .code(500)
                .errorCode("COMMON_INTERNAL_ERROR")
                .message("系统内部错误，请稍后重试")
                .build();
    }
}
