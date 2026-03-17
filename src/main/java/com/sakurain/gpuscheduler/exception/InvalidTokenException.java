package com.sakurain.gpuscheduler.exception;

/**
 * 无效令牌异常
 */
public class InvalidTokenException extends BusinessException {

    public InvalidTokenException(String message) {
        super("INVALID_TOKEN", message, 401);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super("INVALID_TOKEN", message, 401, cause);
    }
}
