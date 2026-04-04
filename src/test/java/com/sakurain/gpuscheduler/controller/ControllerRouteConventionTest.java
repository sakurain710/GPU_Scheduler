package com.sakurain.gpuscheduler.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ControllerRouteConventionTest {

    @Test
    void gpuController_shouldUsePluralPathOnly() {
        RequestMapping mapping = GpuController.class.getAnnotation(RequestMapping.class);
        assertArrayEquals(new String[]{"/api/gpus"}, mapping.value());
    }

    @Test
    void gpuTaskController_shouldUsePluralPathOnly() {
        RequestMapping mapping = GpuTaskController.class.getAnnotation(RequestMapping.class);
        assertArrayEquals(new String[]{"/api/tasks"}, mapping.value());
    }
}
