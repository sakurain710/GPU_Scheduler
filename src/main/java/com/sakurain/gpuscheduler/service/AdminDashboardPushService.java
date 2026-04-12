package com.sakurain.gpuscheduler.service;

import com.sakurain.gpuscheduler.dto.dashboard.AdminDashboardTelemetrySnapshot;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class AdminDashboardPushService {

    private final SimpMessagingTemplate messagingTemplate;
    private final AdminDashboardService adminDashboardService;

    @Value("${admin-dashboard.telemetry.topic:/topic/admin/dashboard/telemetry}")
    private String telemetryTopic;

    @Getter
    private volatile LocalDateTime lastPushAt;

    public AdminDashboardPushService(SimpMessagingTemplate messagingTemplate,
                                     AdminDashboardService adminDashboardService) {
        this.messagingTemplate = messagingTemplate;
        this.adminDashboardService = adminDashboardService;
    }

    @Scheduled(
            initialDelayString = "${admin-dashboard.telemetry.initial-delay-ms:5000}",
            fixedDelayString = "${admin-dashboard.telemetry.push-interval-ms:3000}"
    )
    public void pushTelemetry() {
        try {
            LocalDateTime now = LocalDateTime.now();
            AdminDashboardTelemetrySnapshot snapshot = AdminDashboardTelemetrySnapshot.builder()
                    .timestamp(now)
                    .overview(adminDashboardService.buildOverview())
                    .memoryFragmentation(adminDashboardService.buildMemoryFragmentation())
                    .dlqSummary(adminDashboardService.buildDlqSummary())
                    .build();
            messagingTemplate.convertAndSend(telemetryTopic, snapshot);
            lastPushAt = now;
        } catch (Exception ex) {
            log.warn("Admin dashboard telemetry push failed: {}", ex.getMessage());
        }
    }
}
