package com.sakurain.gpuscheduler.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationUtilsTest {

    @Test
    void normalizePage_defaultsToOneWhenInvalid() {
        assertThat(PaginationUtils.normalizePage(null)).isEqualTo(1L);
        assertThat(PaginationUtils.normalizePage(0)).isEqualTo(1L);
        assertThat(PaginationUtils.normalizePage(-3)).isEqualTo(1L);
    }

    @Test
    void normalizePage_keepsValidPage() {
        assertThat(PaginationUtils.normalizePage(5)).isEqualTo(5L);
    }

    @Test
    void normalizeSize_usesDefaultWhenInvalid() {
        assertThat(PaginationUtils.normalizeSize(null, 10, 200)).isEqualTo(10L);
        assertThat(PaginationUtils.normalizeSize(0, 10, 200)).isEqualTo(10L);
        assertThat(PaginationUtils.normalizeSize(-8, 10, 200)).isEqualTo(10L);
    }

    @Test
    void normalizeSize_capsAtMax() {
        assertThat(PaginationUtils.normalizeSize(500, 10, 200)).isEqualTo(200L);
    }

    @Test
    void normalizeSize_keepsValidSize() {
        assertThat(PaginationUtils.normalizeSize(50, 10, 200)).isEqualTo(50L);
    }
}

