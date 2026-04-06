package com.sakurain.gpuscheduler.service;

import com.sakurain.gpuscheduler.dto.monitor.TelemetrySnapshot;
import com.sakurain.gpuscheduler.dto.monitor.PublicTelemetrySnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class TelemetryPushService {

    private final SimpMessagingTemplate messagingTemplate;
    private final MonitoringService monitoringService;

    @Value("${telemetry.topic:/topic/telemetry}")
    private String telemetryTopic;

    @Value("${telemetry.public-topic:/topic/public/telemetry}")
    private String publicTelemetryTopic;

    public TelemetryPushService(SimpMessagingTemplate messagingTemplate,
                                MonitoringService monitoringService) {
        this.messagingTemplate = messagingTemplate;
        this.monitoringService = monitoringService;
    }

    @Scheduled(
            initialDelayString = "${telemetry.initial-delay-ms:5000}",
            fixedDelayString = "${telemetry.push-interval-ms:3000}"
    )
    public void pushTelemetry() {
        try {
            TelemetrySnapshot snapshot = TelemetrySnapshot.builder()
                    .timestamp(LocalDateTime.now())
                    .health(monitoringService.getSystemHealth())
                    .tasks(monitoringService.getTaskMetrics())
                    .gpus(monitoringService.getGpuMetrics())
                    .build();
            PublicTelemetrySnapshot publicSnapshot = monitoringService.getPublicTelemetrySnapshot();

            messagingTemplate.convertAndSend(telemetryTopic, snapshot);
            messagingTemplate.convertAndSend(publicTelemetryTopic, publicSnapshot);
        } catch (Exception ex) {
            log.warn("Telemetry push failed: {}", ex.getMessage());
        }
    }
}
