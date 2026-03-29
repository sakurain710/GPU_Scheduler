package com.sakurain.gpuscheduler.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * GPU任务状态枚举 — 与 gpu_task.status TINYINT 映射
 */
@Getter
public enum TaskStatus {

    PENDING(1, "Pending"),
    QUEUED(2, "Queued"),
    RUNNING(3, "Running"),
    COMPLETED(4, "Completed"),
    FAILED(5, "Failed"),
    CANCELLED(6, "Cancelled"),
    PENDING_APPROVAL(7, "Pending Approval"),
    REJECTED(8, "Rejected");

    private final int code;
    private final String label;

    TaskStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static TaskStatus fromCode(int code) {
        return Arrays.stream(values())
                .filter(s -> s.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown TaskStatus code: " + code));
    }

    /**
     * 是否为终态（不可再转换）
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == REJECTED;
    }
}
