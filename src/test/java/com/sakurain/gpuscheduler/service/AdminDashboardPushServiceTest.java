package com.sakurain.gpuscheduler.service;

import com.sakurain.gpuscheduler.dto.dashboard.AdminDashboardOverviewResponse;
import com.sakurain.gpuscheduler.dto.dashboard.AdminDashboardTelemetrySnapshot;
import com.sakurain.gpuscheduler.dto.dashboard.DlqSummary;
import com.sakurain.gpuscheduler.dto.dashboard.MemoryFragmentationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminDashboardPushServiceTest {

    @Test
    void pushTelemetry_shouldPublishToAdminTopic() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        AdminDashboardService dashboardService = mock(AdminDashboardService.class);
        AdminDashboardPushService pushService = new AdminDashboardPushService(messagingTemplate, dashboardService);
        ReflectionTestUtils.setField(pushService, "telemetryTopic", "/topic/admin/dashboard/telemetry");

        when(dashboardService.buildOverview()).thenReturn(AdminDashboardOverviewResponse.builder().build());
        when(dashboardService.buildMemoryFragmentation()).thenReturn(MemoryFragmentationResponse.builder().build());
        when(dashboardService.buildDlqSummary()).thenReturn(DlqSummary.builder().size(0L).build());

        pushService.pushTelemetry();

        verify(messagingTemplate).convertAndSend(
                org.mockito.Mockito.eq("/topic/admin/dashboard/telemetry"),
                any(AdminDashboardTelemetrySnapshot.class)
        );
    }
}
