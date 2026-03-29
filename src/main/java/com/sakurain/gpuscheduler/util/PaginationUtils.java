package com.sakurain.gpuscheduler.util;

/**
 * 分页参数标准化工具。
 */
public final class PaginationUtils {

    private PaginationUtils() {
    }

    public static long normalizePage(Integer page) {
        if (page == null || page < 1) {
            return 1L;
        }
        return page.longValue();
    }

    public static long normalizeSize(Integer size, int defaultSize, int maxSize) {
        int fallback = Math.max(1, defaultSize);
        int upper = Math.max(fallback, maxSize);
        if (size == null || size < 1) {
            return fallback;
        }
        return Math.min(size, upper);
    }
}

