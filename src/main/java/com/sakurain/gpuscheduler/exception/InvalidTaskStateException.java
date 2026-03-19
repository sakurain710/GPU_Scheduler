package com.sakurain.gpuscheduler.exception;

/**
 * 非法的任务状态转换异常
 */
public class InvalidTaskStateException extends BusinessException {

    public InvalidTaskStateException(String message) {
        super("INVALID_TASK_STATE", message, 409);
    }
}
