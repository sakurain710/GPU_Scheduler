package com.sakurain.gpuscheduler.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.*;

/**
 * 集成测试公共注解 — 启动 Spring 上下文，使用 H2 内存数据库（@Primary DataSource 覆盖 Druid）
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@ActiveProfiles("test")
@Import(TestDataSourceConfig.class)
@ExtendWith(RedisAvailableCondition.class)
public @interface IntegrationTest {
}
