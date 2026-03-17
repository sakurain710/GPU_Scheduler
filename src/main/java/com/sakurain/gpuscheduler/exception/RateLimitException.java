package com.sakurain.gpuscheduler.exception;

/**
 * 请求频率超限异常（HTTP 429）
 */
public class RateLimitException extends BusinessException {

    public RateLimitException() {
        super("RATE_LIMIT_EXCEEDED", "请求过于频繁，请稍后重试", 429);
    }

    public RateLimitException(String message) {
        super("RATE_LIMIT_EXCEEDED", message, 429);
    }
}
