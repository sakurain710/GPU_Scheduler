package com.sakurain.gpuscheduler.controller;

import com.sakurain.gpuscheduler.dto.Result;
import com.sakurain.gpuscheduler.dto.dashboard.AdminDashboardInitialResponse;
import com.sakurain.gpuscheduler.dto.dashboard.DlqListResponse;
import com.sakurain.gpuscheduler.dto.dashboard.QueueWaitTrendResponse;
import com.sakurain.gpuscheduler.service.AdminDashboardPushService;
import com.sakurain.gpuscheduler.service.AdminDashboardService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminDashboardControllerTest {

    @Test
    void initial_shouldReturnWrappedSnapshot() {
        AdminDashboardService service = mock(AdminDashboardService.class);
        AdminDashboardPushService pushService = mock(AdminDashboardPushService.class);
        AdminDashboardController controller = new AdminDashboardController(service, pushService);
        AdminDashboardInitialResponse snapshot = AdminDashboardInitialResponse.builder().lastTelemetryAt(LocalDateTime.now()).build();

        when(pushService.getLastPushAt()).thenReturn(snapshot.getLastTelemetryAt());
        when(service.getInitialSnapshot(snapshot.getLastTelemetryAt())).thenReturn(snapshot);

        Result<AdminDashboardInitialResponse> result = controller.initial();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isSameAs(snapshot);
    }

    @Test
    void queueWaitTrend_shouldDelegateToService() {
        AdminDashboardService service = mock(AdminDashboardService.class);
        AdminDashboardPushService pushService = mock(AdminDashboardPushService.class);
        AdminDashboardController controller = new AdminDashboardController(service, pushService);
        QueueWaitTrendResponse response = QueueWaitTrendResponse.builder().mode("DAY").build();

        when(service.getQueueWaitTrend("DAY", LocalDate.of(2026, 4, 12))).thenReturn(response);

        Result<QueueWaitTrendResponse> result = controller.queueWaitTrend("DAY", LocalDate.of(2026, 4, 12));
        assertThat(result.getData()).isSameAs(response);
    }

    @Test
    void dlq_shouldDelegateToService() {
        AdminDashboardService service = mock(AdminDashboardService.class);
        AdminDashboardPushService pushService = mock(AdminDashboardPushService.class);
        AdminDashboardController controller = new AdminDashboardController(service, pushService);
        DlqListResponse response = DlqListResponse.builder().total(0L).build();

        when(service.listDlq(1, 20)).thenReturn(response);

        Result<DlqListResponse> result = controller.dlq(1, 20);
        assertThat(result.getData()).isSameAs(response);
    }
}
