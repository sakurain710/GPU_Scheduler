package com.sakurain.gpuscheduler.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * GPU状态枚举 — 与 gpu.status TINYINT 映射
 */
@Getter
public enum GpuStatus {

    IDLE(1, "Idle"),
    BUSY(2, "Busy"),
    OFFLINE(3, "Offline"),
    MAINTENANCE(4, "Maintenance");

    private final int code;
    private final String label;

    GpuStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static GpuStatus fromCode(int code) {
        return Arrays.stream(values())
                .filter(s -> s.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown GpuStatus code: " + code));
    }
}
