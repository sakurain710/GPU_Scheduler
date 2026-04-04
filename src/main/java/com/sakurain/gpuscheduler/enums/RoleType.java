package com.sakurain.gpuscheduler.enums;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum RoleType {
    SYSTEM(1, "System"),
    CUSTOM(2, "Custom"),
    TEMPORARY(3, "Temporary");

    private final int code;
    private final String label;

    RoleType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static boolean isValid(Integer code) {
        return code != null && Arrays.stream(values()).anyMatch(item -> item.code == code);
    }
}
