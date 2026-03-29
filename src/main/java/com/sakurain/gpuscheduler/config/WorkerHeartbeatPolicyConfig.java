package com.sakurain.gpuscheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "worker-heartbeat")
public class WorkerHeartbeatPolicyConfig {

    /**
     * 是否启用 worker 心跳监控。默认关闭，避免影响本地模拟执行。
     */
    private boolean enabled = false;

    /**
     * 心跳过期阈值（秒）。超过该阈值视为 stale worker。
     */
    private int staleThresholdSeconds = 120;

    /**
     * stale 检测扫描间隔（毫秒）。
     */
    private long scanIntervalMs = 15000L;
}
