package com.sakurain.gpuscheduler.exception;

/**
 * 无效凭证异常
 */
public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException(String message) {
        super("INVALID_CREDENTIALS", message, 401);
    }
}
