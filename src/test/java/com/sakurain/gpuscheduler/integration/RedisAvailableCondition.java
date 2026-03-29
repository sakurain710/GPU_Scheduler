package com.sakurain.gpuscheduler.integration;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Redis不可达时跳过依赖Redis的集成测试，避免环境噪声导致假失败。
 */
public class RedisAvailableCondition implements ExecutionCondition {

    private static final ConditionEvaluationResult ENABLED =
            ConditionEvaluationResult.enabled("Redis is reachable");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        String host = getProperty("REDIS_HOST", "127.0.0.1");
        int port = Integer.parseInt(getProperty("REDIS_PORT", "6379"));
        int timeoutMs = 800;

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return ENABLED;
        } catch (Exception ex) {
            return ConditionEvaluationResult.disabled(
                    "Redis not reachable at %s:%d (%s)".formatted(host, port, ex.getMessage()));
        }
    }

    private String getProperty(String key, String defaultValue) {
        String val = System.getProperty(key);
        if (val != null && !val.isBlank()) {
            return val;
        }
        val = System.getenv(key);
        if (val != null && !val.isBlank()) {
            return val;
        }
        return defaultValue;
    }
}
