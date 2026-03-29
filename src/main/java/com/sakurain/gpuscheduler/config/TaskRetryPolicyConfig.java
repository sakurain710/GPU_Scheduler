package com.sakurain.gpuscheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 任务重试与死信配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "task-retry")
public class TaskRetryPolicyConfig {

    /**
     * 是否启用失败任务重试
     */
    private boolean enabled = true;

    /**
     * 最大重试次数
     */
    private int maxRetries = 3;

    /**
     * 初始退避秒数（指数退避）
     */
    private long initialBackoffSeconds = 30;

    /**
     * 每轮最大处理数量
     */
    private int batchSize = 100;
}

