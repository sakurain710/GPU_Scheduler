package com.sakurain.gpuscheduler.scheduler;

import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.exception.InvalidTaskStateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 任务状态机 — 定义并强制执行合法的状态转换
 * <p>
 * 合法转换:
 *   PENDING  → QUEUED, CANCELLED
 *   QUEUED   → RUNNING, CANCELLED
 *   RUNNING  → COMPLETED, FAILED
 *   COMPLETED / FAILED / CANCELLED → (终态，不可转换)
 */
@Slf4j
@Component
public class TaskStateMachine {

    private static final Map<TaskStatus, Set<TaskStatus>> TRANSITIONS = Map.of(
            TaskStatus.PENDING,   Set.of(TaskStatus.QUEUED, TaskStatus.CANCELLED),
            TaskStatus.PENDING_APPROVAL, Set.of(TaskStatus.QUEUED, TaskStatus.REJECTED, TaskStatus.CANCELLED),
            TaskStatus.QUEUED,    Set.of(TaskStatus.RUNNING, TaskStatus.CANCELLED),
            TaskStatus.RUNNING,   Set.of(TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.QUEUED),
            TaskStatus.COMPLETED, Set.of(),
            TaskStatus.FAILED,    Set.of(TaskStatus.QUEUED),
            TaskStatus.CANCELLED, Set.of(),
            TaskStatus.REJECTED,  Set.of()
    );

    /**
     * 校验状态转换是否合法
     */
    public boolean canTransition(TaskStatus from, TaskStatus to) {
        Set<TaskStatus> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * 执行状态转换校验 — 不合法时抛出异常
     */
    public void validateTransition(TaskStatus from, TaskStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidTaskStateException(
                    String.format("非法状态转换: %s(%d) → %s(%d)",
                            from.getLabel(), from.getCode(),
                            to.getLabel(), to.getCode()));
        }
        log.debug("状态转换校验通过: {} → {}", from.getLabel(), to.getLabel());
    }

    /**
     * 获取事件名称 — 用于写入 gpu_task_log.event
     */
    public String resolveEvent(TaskStatus to) {
        return switch (to) {
            case QUEUED    -> "QUEUED";
            case RUNNING   -> "DISPATCHED";
            case COMPLETED -> "COMPLETED";
            case FAILED    -> "FAILED";
            case CANCELLED -> "CANCELLED";
            case PENDING_APPROVAL -> "PENDING_APPROVAL";
            case REJECTED -> "REJECTED";
            default        -> to.getLabel().toUpperCase();
        };
    }
}
