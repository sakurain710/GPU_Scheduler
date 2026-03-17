package com.sakurain.gpuscheduler.exception;

/**
 * 用户账户被禁用异常
 */
public class UserDisabledException extends BusinessException {

    public UserDisabledException(String message) {
        super("USER_DISABLED", message, 403);
    }
}
