package com.sakurain.gpuscheduler.exception;

/**
 * 资源重复异常
 */
public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(String message) {
        super("DUPLICATE_RESOURCE", message, 409);
    }
}
