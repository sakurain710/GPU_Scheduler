package com.sakurain.gpuscheduler.enums;

import lombok.Getter;

@Getter
public enum TaskLogEvent {
    QUEUED("QUEUED"),
    DISPATCHED("DISPATCHED"),
    COMPLETED("COMPLETED"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED"),
    PENDING_APPROVAL("PENDING_APPROVAL"),
    REJECTED("REJECTED");

    private final String code;

    TaskLogEvent(String code) {
        this.code = code;
    }
}
