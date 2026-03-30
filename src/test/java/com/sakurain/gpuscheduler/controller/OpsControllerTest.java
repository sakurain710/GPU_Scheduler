package com.sakurain.gpuscheduler.controller;

import com.sakurain.gpuscheduler.dto.Result;
import com.sakurain.gpuscheduler.exception.BusinessException;
import com.sakurain.gpuscheduler.exception.ResourceNotFoundException;
import com.sakurain.gpuscheduler.scheduler.CircuitBreakerService;
import com.sakurain.gpuscheduler.scheduler.TaskDispatcher;
import com.sakurain.gpuscheduler.service.GpuTaskService;
import com.sakurain.gpuscheduler.service.TaskNotificationService;
import com.sakurain.gpuscheduler.service.TaskRetryDlqService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpsControllerTest {

    @Mock
    private TaskDispatcher taskDispatcher;
    @Mock
    private CircuitBreakerService circuitBreakerService;
    @Mock
    private TaskRetryDlqService retryDlqService;
    @Mock
    private GpuTaskService gpuTaskService;
    @Mock
    private TaskNotificationService taskNotificationService;

    @Test
    void reprocessDlqTask_invalidTaskId_shouldThrowBusinessException() {
        OpsController controller = new OpsController(
                taskDispatcher,
                circuitBreakerService,
                retryDlqService,
                gpuTaskService,
                taskNotificationService
        );

        assertThrows(BusinessException.class, () -> controller.reprocessDlqTask(0L));
    }

    @Test
    void reprocessDlqTask_notFound_shouldThrowResourceNotFound() {
        OpsController controller = new OpsController(
                taskDispatcher,
                circuitBreakerService,
                retryDlqService,
                gpuTaskService,
                taskNotificationService
        );
        when(retryDlqService.reprocessDlqTask(100L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> controller.reprocessDlqTask(100L));
    }

    @Test
    void reprocessDlqTask_success_shouldReturnReprocessedTrue() {
        OpsController controller = new OpsController(
                taskDispatcher,
                circuitBreakerService,
                retryDlqService,
                gpuTaskService,
                taskNotificationService
        );
        when(retryDlqService.reprocessDlqTask(101L)).thenReturn(true);

        Result<Map<String, Object>> result = controller.reprocessDlqTask(101L);

        assertEquals(200, result.getCode());
        assertEquals(true, result.getData().get("reprocessed"));
        assertEquals(101L, result.getData().get("taskId"));
    }
}
