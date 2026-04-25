package com.sakurain.gpuscheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakurain.gpuscheduler.dto.dashboard.AdminDashboardOverviewResponse;
import com.sakurain.gpuscheduler.dto.dashboard.DlqListResponse;
import com.sakurain.gpuscheduler.dto.dashboard.MemoryFragmentationResponse;
import com.sakurain.gpuscheduler.dto.dashboard.QueueWaitTrendResponse;
import com.sakurain.gpuscheduler.entity.Gpu;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.entity.TaskDlq;
import com.sakurain.gpuscheduler.entity.User;
import com.sakurain.gpuscheduler.enums.GpuStatus;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.mapper.GpuMapper;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import com.sakurain.gpuscheduler.mapper.TaskDlqMapper;
import com.sakurain.gpuscheduler.mapper.UserMapper;
import com.sakurain.gpuscheduler.scheduler.CircuitBreakerService;
import com.sakurain.gpuscheduler.scheduler.TaskExecutionSimulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock private GpuTaskMapper gpuTaskMapper;
    @Mock private GpuMapper gpuMapper;
    @Mock private TaskDlqMapper taskDlqMapper;
    @Mock private UserMapper userMapper;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private CircuitBreakerService circuitBreakerService;
    @Mock private TaskExecutionSimulator taskExecutionSimulator;
    @Mock private DataSource dataSource;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ListOperations<String, String> listOperations;
    @Mock private RedisConnectionFactory connectionFactory;
    @Mock private RedisConnection redisConnection;

    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardService(
                gpuTaskMapper,
                gpuMapper,
                taskDlqMapper,
                userMapper,
                redisTemplate,
                circuitBreakerService,
                taskExecutionSimulator,
                dataSource,
                new ObjectMapper().findAndRegisterModules()
        );
        ReflectionTestUtils.setField(service, "trendCacheTtlSeconds", 300L);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        lenient().when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        lenient().when(connectionFactory.getConnection()).thenReturn(redisConnection);
        lenient().when(redisConnection.info("memory")).thenReturn(new Properties());
    }

    @Test
    void buildMemoryFragmentation_shouldSplitUsedFragmentedAndFreeMemory() {
        Gpu idleGpu = Gpu.builder().id(1L).name("A100").memoryGb(new BigDecimal("80")).status(GpuStatus.IDLE.getCode()).build();
        Gpu busyGpu = Gpu.builder().id(2L).name("4090").memoryGb(new BigDecimal("24")).status(GpuStatus.BUSY.getCode()).build();
        GpuTask runningTask = GpuTask.builder().id(10L).gpuId(2L).minMemoryGb(new BigDecimal("10")).status(TaskStatus.RUNNING.getCode()).build();
        when(gpuMapper.selectList(null)).thenReturn(List.of(idleGpu, busyGpu));
        when(gpuTaskMapper.selectList(any())).thenReturn(List.of(runningTask));

        MemoryFragmentationResponse response = service.buildMemoryFragmentation();

        assertThat(response.getUsedAllocatedMemoryGb()).isEqualByComparingTo("10.00");
        assertThat(response.getFragmentedMemoryGb()).isEqualByComparingTo("14.00");
        assertThat(response.getFreeMemoryGb()).isEqualByComparingTo("80.00");
        assertThat(response.getGpuBreakdowns()).hasSize(2);
    }

    @Test
    void listDlq_shouldReturnStructuredItems() {
        when(taskDlqMapper.selectCount(any())).thenReturn(1L);
        when(taskDlqMapper.selectList(any()))
                .thenReturn(List.of(TaskDlq.builder()
                        .id(1L)
                        .taskId(12L)
                        .retryCount(3)
                        .failureReason("GPU error")
                        .createdAt(LocalDateTime.of(2026, 4, 12, 12, 0))
                        .status(1)
                        .build()));
        when(gpuTaskMapper.selectBatchIds(List.of(12L)))
                .thenReturn(List.of(GpuTask.builder().id(12L).userId(100L).build()));
        when(userMapper.selectBatchIds(List.of(100L)))
                .thenReturn(List.of(User.builder().id(100L).username("alice").email("alice@example.com").build()));

        DlqListResponse response = service.listDlq(1, 20);

        assertThat(response.getTotal()).isEqualTo(1L);
        assertThat(response.getRecords()).singleElement().satisfies(item -> {
            assertThat(item.getTaskId()).isEqualTo(12L);
            assertThat(item.getUsername()).isEqualTo("alice");
            assertThat(item.getEmail()).isEqualTo("alice@example.com");
            assertThat(item.getRetryCount()).isEqualTo(3L);
            assertThat(item.getFailureReason()).isEqualTo("GPU error");
        });
    }

    @Test
    void getQueueWaitTrend_dayModeShouldReturn24Buckets() {
        when(valueOperations.get(anyString())).thenReturn(null);
        doNothing().when(valueOperations).set(anyString(), anyString(), any(java.time.Duration.class));
        when(gpuTaskMapper.selectList(any()))
                .thenReturn(
                        List.of(
                                GpuTask.builder()
                                        .id(1L)
                                        .enqueueAt(LocalDateTime.of(2026, 4, 12, 1, 0))
                                        .dispatchedAt(LocalDateTime.of(2026, 4, 12, 1, 10))
                                        .minMemoryGb(new BigDecimal("8"))
                                        .actualSeconds(new BigDecimal("60"))
                                        .gpuId(1L)
                                        .build()
                        ),
                        List.of()
                );
        when(gpuMapper.selectList(null))
                .thenReturn(List.of(Gpu.builder().id(1L).memoryGb(new BigDecimal("24")).status(GpuStatus.IDLE.getCode()).build()));

        QueueWaitTrendResponse response = service.getQueueWaitTrend("DAY", LocalDate.of(2026, 4, 12));

        assertThat(response.getMode()).isEqualTo("DAY");
        assertThat(response.getBucketUnit()).isEqualTo("HOUR");
        assertThat(response.getPoints()).hasSize(24);
        assertThat(response.getPoints().get(1).getActualAgingAvgWaitSeconds()).isEqualTo(600.0);
        assertThat(response.getPoints().get(1).getSimulatedFifoAvgWaitSeconds()).isEqualTo(0.0);
    }

    @Test
    void buildOverview_shouldReadNumericExecuteCountFromDatasource() {
        service = createService(new CountingDataSource());
        when(gpuMapper.selectCount(null)).thenReturn(1L);
        when(circuitBreakerService.getState()).thenReturn(CircuitBreakerService.State.CLOSED);

        AdminDashboardOverviewResponse first = service.buildOverview();
        AdminDashboardOverviewResponse second = service.buildOverview();

        assertThat(first.getMysql().getStatus()).isEqualTo("UP");
        assertThat(first.getMysql().getQps()).isEqualTo(0.0);
        assertThat(second.getMysql().getStatus()).isEqualTo("UP");
        assertThat(second.getMysql().getQps()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void buildOverview_shouldFallbackWhenExecuteCountSignatureDoesNotMatch() {
        service = createService(new InvalidExecuteCountDataSource());
        when(gpuMapper.selectCount(null)).thenReturn(1L);
        when(circuitBreakerService.getState()).thenReturn(CircuitBreakerService.State.CLOSED);

        AdminDashboardOverviewResponse response = service.buildOverview();

        assertThat(response.getMysql().getStatus()).isEqualTo("UP");
        assertThat(response.getMysql().getQps()).isEqualTo(0.0);
    }

    private AdminDashboardService createService(DataSource customDataSource) {
        AdminDashboardService customService = new AdminDashboardService(
                gpuTaskMapper,
                gpuMapper,
                taskDlqMapper,
                userMapper,
                redisTemplate,
                circuitBreakerService,
                taskExecutionSimulator,
                customDataSource,
                new ObjectMapper().findAndRegisterModules()
        );
        ReflectionTestUtils.setField(customService, "trendCacheTtlSeconds", 300L);
        return customService;
    }

    public static class CountingDataSource extends StubDataSource {
        private long executeCount;

        public long getExecuteCount() {
            return executeCount++;
        }
    }

    public static class InvalidExecuteCountDataSource extends StubDataSource {
        public long getExecuteCount(String ignored) {
            return 1L;
        }
    }

    public abstract static class StubDataSource implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("not implemented");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("not implemented");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("not implemented");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
