package com.sakurain.gpuscheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 任务抢占策略配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "task-preemption")
public class TaskPreemptionPolicyConfig {

    /**
     * 是否启用自动抢占
     */
    private boolean enabled = true;

    /**
     * 触发抢占的最小优先级
     */
    private int triggerPriority = 9;

    /**
     * 抢占优先级差阈值（waiting - running >= 阈值）
     */
    private int minPriorityGap = 2;
}

