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
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    FORCE_FAILED("FORCE_FAILED"),
    FORCE_REQUEUED("FORCE_REQUEUED"),
    PREEMPTED("PREEMPTED"),
    DLQ_ENTERED("DLQ_ENTERED"),
    DLQ_REPROCESSED("DLQ_REPROCESSED"),
    HEARTBEAT_LOST("HEARTBEAT_LOST"),
    MAINTENANCE("MAINTENANCE");

    private final String code;

    TaskLogEvent(String code) {
        this.code = code;
    }
}
