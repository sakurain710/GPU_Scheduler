package com.sakurain.gpuscheduler.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicLong;

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
        openAndExpireResetTimeout();

        assertThat(circuitBreaker.allowRequest()).isTrue();
        circuitBreaker.recordSuccess();

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerService.State.CLOSED);
        assertThat(circuitBreaker.allowRequest()).isTrue();
    }

    @Test
    void testHalfOpen_AllowsOnlyOneProbeRequest() {
        openAndExpireResetTimeout();

        assertThat(circuitBreaker.allowRequest()).isTrue();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerService.State.HALF_OPEN);
        assertThat(circuitBreaker.allowRequest()).isFalse();
    }

    @Test
    void testHalfOpen_FailureReturnsOpen() {
        openAndExpireResetTimeout();

        assertThat(circuitBreaker.allowRequest()).isTrue();
        circuitBreaker.recordFailure();

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerService.State.OPEN);
        assertThat(circuitBreaker.allowRequest()).isFalse();
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

    private void openAndExpireResetTimeout() {
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordFailure();
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreakerService.State.OPEN);

        AtomicLong lastFailureTime = (AtomicLong) ReflectionTestUtils.getField(circuitBreaker, "lastFailureTime");
        assertThat(lastFailureTime).isNotNull();
        lastFailureTime.set(System.currentTimeMillis() - 30_001L);
    }
}
