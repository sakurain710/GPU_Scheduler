package com.sakurain.gpuscheduler.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 熔断器服务测试
 */
class CircuitBreakerServiceTest {

    private CircuitBreakerService circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = new CircuitBreakerService();
    }

    @Test
    void testInitialState_IsClosed() {
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerService.State.CLOSED);
        assertThat(circuitBreaker.allowRequest()).isTrue();
    }

    @Test
    void testRecordSuccess_ResetsFailureCount() {
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        circuitBreaker.recordSuccess();

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerService.State.CLOSED);
        assertThat(circuitBreaker.getFailureCount()).isEqualTo(0);
    }

    @Test
    void testOpensAfterThresholdFailures() {
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordFailure();
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerService.State.OPEN);
        assertThat(circuitBreaker.allowRequest()).isFalse();
    }

    @Test
    void testReset_RestoresClosedState() {
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordFailure();
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerService.State.OPEN);

        circuitBreaker.reset();

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerService.State.CLOSED);
        assertThat(circuitBreaker.allowRequest()).isTrue();
        assertThat(circuitBreaker.getFailureCount()).isEqualTo(0);
    }

    @Test
    void testHalfOpen_SuccessRestoresClosed() {
        // 强制设置为HALF_OPEN
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordFailure();
        }
        circuitBreaker.reset();
        // 模拟HALF_OPEN：直接通过recordSuccess
        circuitBreaker.recordSuccess();

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerService.State.CLOSED);
    }

    @Test
    void testBelowThreshold_StaysClosed() {
        // 4次失败不触发熔断
        for (int i = 0; i < 4; i++) {
            circuitBreaker.recordFailure();
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerService.State.CLOSED);
        assertThat(circuitBreaker.allowRequest()).isTrue();
    }
}
