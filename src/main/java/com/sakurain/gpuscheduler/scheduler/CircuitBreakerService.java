package com.sakurain.gpuscheduler.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 熔断器服务 — 防止故障传播
 * <p>
 * 状态机：
 * CLOSED（正常）→ OPEN（熔断）→ HALF_OPEN（探测）→ CLOSED
 * <p>
 * 规则：
 * - 连续失败达到阈值 → CLOSED 转 OPEN
 * - OPEN 状态持续 resetTimeoutMs 后 → 转 HALF_OPEN
 * - HALF_OPEN 状态下成功 → 转 CLOSED；失败 → 转 OPEN
 */
@Slf4j
@Service
public class CircuitBreakerService {

    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private static final int FAILURE_THRESHOLD = 5;
    private static final long RESET_TIMEOUT_MS = 30_000L;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);

    /**
     * 判断是否允许请求通过
     */
    public boolean allowRequest() {
        State current = state.get();

        if (current == State.CLOSED) {
            return true;
        }

        if (current == State.OPEN) {
            long elapsed = System.currentTimeMillis() - lastFailureTime.get();
            if (elapsed >= RESET_TIMEOUT_MS) {
                // 超过重置超时，转为半开状态
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    log.info("熔断器转为HALF_OPEN，开始探测");
                }
                return true;
            }
            return false;
        }

        // HALF_OPEN 状态允许一个请求通过
        return true;
    }

    /**
     * 记录成功
     */
    public void recordSuccess() {
        State current = state.get();
        if (current == State.HALF_OPEN) {
            reset();
            log.info("熔断器探测成功，恢复CLOSED状态");
        } else if (current == State.CLOSED) {
            failureCount.set(0);
        }
    }

    /**
     * 记录失败
     */
    public void recordFailure() {
        lastFailureTime.set(System.currentTimeMillis());
        int failures = failureCount.incrementAndGet();

        State current = state.get();
        if (current == State.HALF_OPEN) {
            state.set(State.OPEN);
            log.warn("熔断器探测失败，重新OPEN");
            return;
        }

        if (current == State.CLOSED && failures >= FAILURE_THRESHOLD) {
            if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                log.warn("连续{}次失败，熔断器OPEN", failures);
            }
        }
    }

    /**
     * 重置熔断器到CLOSED状态
     */
    public void reset() {
        state.set(State.CLOSED);
        failureCount.set(0);
        lastFailureTime.set(0);
    }

    public State getState() {
        return state.get();
    }

    public int getFailureCount() {
        return failureCount.get();
    }
}
