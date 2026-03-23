package com.sakurain.gpuscheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 调度器配置 — 老化机制参数
 */
@Data
@Configuration
@EnableScheduling
@ConfigurationProperties(prefix = "scheduler")
public class SchedulerConfig {

    /**
     * 老化权重 — 每分钟等待时间增加的优先级
     * 默认 0.1，即等待10分钟 = +1优先级
     */
    private double ageWeightPerMinute = 0.1;

    /**
     * 队列score刷新间隔（毫秒）
     * 默认 60000ms = 1分钟
     */
    private long refreshIntervalMs = 60000L;

    /**
     * 是否启用老化机制
     */
    private boolean agingEnabled = true;
}
