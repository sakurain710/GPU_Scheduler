package com.sakurain.gpuscheduler.service;

import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class TaskDashboardPushService {

    private final SimpMessagingTemplate messagingTemplate;
    private final TaskDashboardService taskDashboardService;
    private final GpuTaskMapper taskMapper;

    @Value("${task-dashboard.topic-prefix:/topic/task-dashboard/}")
    private String topicPrefix;

    public TaskDashboardPushService(SimpMessagingTemplate messagingTemplate,
                                    TaskDashboardService taskDashboardService,
                                    GpuTaskMapper taskMapper) {
        this.messagingTemplate = messagingTemplate;
        this.taskDashboardService = taskDashboardService;
        this.taskMapper = taskMapper;
    }

    @Scheduled(
            initialDelayString = "${task-dashboard.initial-delay-ms:5000}",
            fixedDelayString = "${task-dashboard.push-interval-ms:3000}"
    )
    public void pushDashboards() {
        Set<Long> userIds = loadUserIds();
        for (Long userId : userIds) {
            pushToUser(userId);
        }
    }

    public void pushToUser(Long userId) {
        try {
            taskDashboardService.pushDashboardSnapshotToUser(messagingTemplate, topicPrefix, userId);
        } catch (Exception ex) {
            log.warn("Task dashboard push failed for user {}: {}", userId, ex.getMessage());
        }
    }

    private Set<Long> loadUserIds() {
        List<GpuTask> tasks = taskMapper.selectList(null);
        Set<Long> userIds = new LinkedHashSet<>();
        for (GpuTask task : tasks) {
            if (task.getUserId() != null) {
                userIds.add(task.getUserId());
            }
        }
        return userIds;
    }
}
