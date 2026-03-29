package com.sakurain.gpuscheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "task-submission")
public class TaskSubmissionPolicyConfig {

    /**
     * Tasks at or above this priority require approval role.
     */
    private int highPriorityThreshold = 9;

    /**
     * Non-approver users cannot exceed this many active tasks (PENDING/QUEUED/RUNNING).
     */
    private int maxActiveTasksPerUser = 3;

    /**
     * Roles allowed to submit high-priority tasks without extra approval.
     */
    private List<String> approverRoles = List.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN");
}
