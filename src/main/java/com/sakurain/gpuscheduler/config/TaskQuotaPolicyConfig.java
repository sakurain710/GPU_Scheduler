package com.sakurain.gpuscheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 任务配额策略配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "task-quota")
public class TaskQuotaPolicyConfig {

    /**
     * 是否启用配额
     */
    private boolean enabled = true;

    /**
     * 月度GPU秒上限
     */
    private long monthlyMaxGpuSeconds = 200_000L;

    /**
     * 月度显存GB秒上限
     */
    private double monthlyMaxMemoryGbSeconds = 2_000_000D;

    /**
     * 审批角色是否豁免配额
     */
    private boolean approverBypass = true;

    /**
     * 提交阶段缺少估时时的默认值
     */
    private long defaultEstimatedSeconds = 600L;
}

