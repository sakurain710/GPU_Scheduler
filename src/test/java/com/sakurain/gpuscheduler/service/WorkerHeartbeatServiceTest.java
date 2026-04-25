package com.sakurain.gpuscheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakurain.gpuscheduler.config.WorkerHeartbeatPolicyConfig;
import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.GpuStatus;
import com.sakurain.gpuscheduler.enums.TaskLogEvent;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkerHeartbeatServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private GpuMapper gpuMapper;
    @Mock
    private GpuTaskMapper gpuTaskMapper;
    @Mock
    private GpuTaskService gpuTaskService;
    @Mock
    private TaskRetryDlqService taskRetryDlqService;

    private WorkerHeartbeatService service;
    private WorkerHeartbeatPolicyConfig policy;

    @BeforeEach
    void setUp() {
        policy = new WorkerHeartbeatPolicyConfig();
        policy.setEnabled(true);
        policy.setStaleThresholdSeconds(120);
        policy.setScanIntervalMs(1000L);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service = new WorkerHeartbeatService(
                redisTemplate, policy, gpuMapper, gpuTaskMapper, gpuTaskService, taskRetryDlqService
        );
    }

    @Test
    void beat_shouldStoreHeartbeatAndRecoverOfflineGpu() {
        Gpu gpu = Gpu.builder().id(1L).status(GpuStatus.OFFLINE.getCode()).build();
        when(gpuMapper.selectById(1L)).thenReturn(gpu);

        service.beat(1L);

        verify(valueOperations).set(startsWith("gpu:worker:heartbeat:1"), anyString(), anyLong(), eq(java.util.concurrent.TimeUnit.SECONDS));
        verify(gpuMapper).tryMarkIdleFromOffline(1L, GpuStatus.OFFLINE.getCode(), GpuStatus.IDLE.getCode());
    }

    @Test
    void beat_shouldThrowWhenGpuMissing() {
        when(gpuMapper.selectById(123L)).thenReturn(null);
        assertThatThrownBy(() -> service.beat(123L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void scanAndRecoverStaleWorkers_shouldFailRunningTaskRetryAndMarkOffline() {
        Gpu busyGpu = Gpu.builder().id(2L).status(GpuStatus.BUSY.getCode()).build();
        GpuTask runningTask = GpuTask.builder()
                .id(99L)
                .gpuId(2L)
                .status(TaskStatus.RUNNING.getCode())
                .build();
        when(gpuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(busyGpu));
        when(valueOperations.get(startsWith("gpu:worker:heartbeat:2"))).thenReturn(null);
        when(gpuTaskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(runningTask);

        service.scanAndRecoverStaleWorkers();

        ArgumentCaptor<GpuTask> taskCaptor = ArgumentCaptor.forClass(GpuTask.class);
        verify(gpuTaskMapper).updateById(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getId()).isEqualTo(99L);
        assertThat(taskCaptor.getValue().getErrorMessage()).contains("Worker heartbeat stale");
        verify(gpuTaskService).transition(eq(99L), eq(TaskStatus.FAILED), eq(2L), isNull(),
                eq(TaskLogEvent.HEARTBEAT_LOST), contains("Worker heartbeat stale"));
        verify(taskRetryDlqService).onTaskFailed(eq(99L), contains("Worker heartbeat stale"));
        verify(gpuMapper).tryMarkOfflineFromBusy(eq(2L), eq(GpuStatus.BUSY.getCode()),
                eq(GpuStatus.OFFLINE.getCode()), contains("Worker heartbeat stale"));
    }

    @Test
    void scanAndRecoverStaleWorkers_shouldNotRetryWhenTransitionFails() {
        Gpu busyGpu = Gpu.builder().id(2L).status(GpuStatus.BUSY.getCode()).build();
        GpuTask runningTask = GpuTask.builder()
                .id(99L)
                .gpuId(2L)
                .status(TaskStatus.RUNNING.getCode())
                .build();
        when(gpuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(busyGpu));
        when(valueOperations.get(startsWith("gpu:worker:heartbeat:2"))).thenReturn(null);
        when(gpuTaskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(runningTask);
        doThrow(new RuntimeException("transition failed"))
                .when(gpuTaskService)
                .transition(eq(99L), eq(TaskStatus.FAILED), eq(2L), isNull(),
                        eq(TaskLogEvent.HEARTBEAT_LOST), contains("Worker heartbeat stale"));

        service.scanAndRecoverStaleWorkers();

        verify(taskRetryDlqService, never()).onTaskFailed(anyLong(), anyString());
        verify(gpuMapper).tryMarkOfflineFromBusy(eq(2L), eq(GpuStatus.BUSY.getCode()),
                eq(GpuStatus.OFFLINE.getCode()), contains("Worker heartbeat stale"));
    }
}
