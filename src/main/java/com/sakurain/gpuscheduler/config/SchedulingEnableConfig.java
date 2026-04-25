package com.sakurain.gpuscheduler.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "scheduler", name = "scheduled-jobs-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingEnableConfig {
}
