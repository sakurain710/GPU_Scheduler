package com.sakurain.gpuscheduler.scheduler;

import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.exception.InvalidTaskStateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 任务状态机单元测试
 */
class TaskStateMachineTest {

    private final TaskStateMachine stateMachine = new TaskStateMachine();

    @ParameterizedTest
    @CsvSource({
            "PENDING, QUEUED",
            "PENDING, CANCELLED",
            "QUEUED, RUNNING",
            "QUEUED, CANCELLED",
            "RUNNING, COMPLETED",
            "RUNNING, FAILED"
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
            "RUNNING, QUEUED",
            "RUNNING, PENDING",
            "RUNNING, CANCELLED",
            "COMPLETED, PENDING",
            "COMPLETED, RUNNING",
            "FAILED, PENDING",
            "FAILED, QUEUED",
            "CANCELLED, PENDING",
            "CANCELLED, QUEUED"
    })
    void invalidTransitions(TaskStatus from, TaskStatus to) {
        assertFalse(stateMachine.canTransition(from, to));
        assertThrows(InvalidTaskStateException.class,
                () -> stateMachine.validateTransition(from, to));
    }

    @Test
    void terminalStatesHaveNoTransitions() {
        for (TaskStatus terminal : new TaskStatus[]{TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.CANCELLED}) {
            assertTrue(terminal.isTerminal());
            for (TaskStatus target : TaskStatus.values()) {
                assertFalse(stateMachine.canTransition(terminal, target));
            }
        }
    }

    @Test
    void resolveEvent() {
        assertEquals("QUEUED", stateMachine.resolveEvent(TaskStatus.QUEUED));
        assertEquals("DISPATCHED", stateMachine.resolveEvent(TaskStatus.RUNNING));
        assertEquals("COMPLETED", stateMachine.resolveEvent(TaskStatus.COMPLETED));
        assertEquals("FAILED", stateMachine.resolveEvent(TaskStatus.FAILED));
        assertEquals("CANCELLED", stateMachine.resolveEvent(TaskStatus.CANCELLED));
    }
}
