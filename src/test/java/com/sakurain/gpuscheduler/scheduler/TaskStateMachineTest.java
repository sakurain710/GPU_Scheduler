package com.sakurain.gpuscheduler.scheduler;

import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.exception.InvalidTaskStateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务状态机单元测试
 */
class TaskStateMachineTest {

    private final TaskStateMachine stateMachine = new TaskStateMachine();

    @ParameterizedTest
    @CsvSource({
            "PENDING, QUEUED",
            "PENDING, CANCELLED",
            "PENDING_APPROVAL, QUEUED",
            "PENDING_APPROVAL, REJECTED",
            "PENDING_APPROVAL, CANCELLED",
            "QUEUED, RUNNING",
            "QUEUED, CANCELLED",
            "RUNNING, COMPLETED",
            "RUNNING, FAILED",
            "RUNNING, QUEUED",
            "FAILED, QUEUED"
    })
    void validTransitions(TaskStatus from, TaskStatus to) {
        assertTrue(stateMachine.canTransition(from, to));
        assertDoesNotThrow(() -> stateMachine.validateTransition(from, to));
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING, RUNNING",
            "PENDING, COMPLETED",
            "PENDING, FAILED",
            "QUEUED, COMPLETED",
            "QUEUED, FAILED",
            "QUEUED, PENDING",
            "RUNNING, PENDING",
            "RUNNING, CANCELLED",
            "COMPLETED, PENDING",
            "COMPLETED, RUNNING",
            "CANCELLED, PENDING",
            "CANCELLED, QUEUED",
            "REJECTED, QUEUED"
    })
    void invalidTransitions(TaskStatus from, TaskStatus to) {
        assertFalse(stateMachine.canTransition(from, to));
        assertThrows(InvalidTaskStateException.class,
                () -> stateMachine.validateTransition(from, to));
    }

    @Test
    void noOutgoingTransitionsStates() {
        for (TaskStatus status : new TaskStatus[]{TaskStatus.COMPLETED, TaskStatus.CANCELLED, TaskStatus.REJECTED}) {
            for (TaskStatus target : TaskStatus.values()) {
                assertFalse(stateMachine.canTransition(status, target));
            }
        }
    }

    @Test
    void failedCanRequeue() {
        assertTrue(stateMachine.canTransition(TaskStatus.FAILED, TaskStatus.QUEUED));
    }

    @Test
    void resolveEvent() {
        assertEquals("QUEUED", stateMachine.resolveEvent(TaskStatus.QUEUED));
        assertEquals("DISPATCHED", stateMachine.resolveEvent(TaskStatus.RUNNING));
        assertEquals("COMPLETED", stateMachine.resolveEvent(TaskStatus.COMPLETED));
        assertEquals("FAILED", stateMachine.resolveEvent(TaskStatus.FAILED));
        assertEquals("CANCELLED", stateMachine.resolveEvent(TaskStatus.CANCELLED));
        assertEquals("PENDING_APPROVAL", stateMachine.resolveEvent(TaskStatus.PENDING_APPROVAL));
        assertEquals("REJECTED", stateMachine.resolveEvent(TaskStatus.REJECTED));
    }
}