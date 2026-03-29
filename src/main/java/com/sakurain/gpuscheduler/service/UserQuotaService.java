package com.sakurain.gpuscheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakurain.gpuscheduler.config.TaskQuotaPolicyConfig;
import com.sakurain.gpuscheduler.dto.task.QuotaUsageResponse;
import com.sakurain.gpuscheduler.dto.task.SubmitTaskRequest;
import com.sakurain.gpuscheduler.entity.GpuTask;
import com.sakurain.gpuscheduler.enums.TaskStatus;
import com.sakurain.gpuscheduler.exception.BusinessException;
import com.sakurain.gpuscheduler.mapper.GpuTaskMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * 用户配额服务
 */
@Service
public class UserQuotaService {

    private final GpuTaskMapper gpuTaskMapper;
    private final TaskQuotaPolicyConfig quotaPolicy;

    public UserQuotaService(GpuTaskMapper gpuTaskMapper, TaskQuotaPolicyConfig quotaPolicy) {
        this.gpuTaskMapper = gpuTaskMapper;
        this.quotaPolicy = quotaPolicy;
    }

    public void assertWithinQuota(Long userId, SubmitTaskRequest request, boolean hasApproverRole) {
        if (!quotaPolicy.isEnabled()) {
            return;
        }
        if (hasApproverRole && quotaPolicy.isApproverBypass()) {
            return;
        }

        QuotaRawUsage usage = calculateRawUsage(userId);
        double submitGpuSeconds = estimateSubmitGpuSeconds(request);
        double submitMemoryGbSeconds = estimateSubmitMemoryGbSeconds(request, submitGpuSeconds);

        double projectedGpuSeconds = usage.usedGpuSeconds + submitGpuSeconds;
        double projectedMemoryGbSeconds = usage.usedMemoryGbSeconds + submitMemoryGbSeconds;

        if (projectedGpuSeconds > quotaPolicy.getMonthlyMaxGpuSeconds()
                || projectedMemoryGbSeconds > quotaPolicy.getMonthlyMaxMemoryGbSeconds()) {
            throw new BusinessException(
                    "TASK_MONTHLY_QUOTA_EXCEEDED",
                    "Monthly quota exceeded, please contact administrator",
                    429
            );
        }
    }

    public QuotaUsageResponse getMonthlyUsage(Long userId) {
        QuotaRawUsage usage = calculateRawUsage(userId);
        double gpuLimit = quotaPolicy.getMonthlyMaxGpuSeconds();
        double memoryLimit = quotaPolicy.getMonthlyMaxMemoryGbSeconds();

        return QuotaUsageResponse.builder()
                .userId(userId)
                .month(YearMonth.now().toString())
                .usedGpuSeconds(usage.usedGpuSeconds)
                .limitGpuSeconds(gpuLimit)
                .usedMemoryGbSeconds(usage.usedMemoryGbSeconds)
                .limitMemoryGbSeconds(memoryLimit)
                .gpuUsagePercent(gpuLimit <= 0 ? 0D : usage.usedGpuSeconds * 100D / gpuLimit)
                .memoryUsagePercent(memoryLimit <= 0 ? 0D : usage.usedMemoryGbSeconds * 100D / memoryLimit)
                .build();
    }

    private QuotaRawUsage calculateRawUsage(Long userId) {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<GpuTask> tasks = gpuTaskMapper.selectList(new LambdaQueryWrapper<GpuTask>()
                .eq(GpuTask::getUserId, userId)
                .ge(GpuTask::getCreatedAt, monthStart));

        double usedGpuSeconds = 0D;
        double usedMemoryGbSeconds = 0D;
        for (GpuTask task : tasks) {
            TaskStatus status = TaskStatus.fromCode(task.getStatus());
            if (status == TaskStatus.REJECTED || status == TaskStatus.CANCELLED) {
                continue;
            }
            double seconds = resolveTaskSeconds(task);
            BigDecimal memory = task.getMinMemoryGb() != null ? task.getMinMemoryGb() : BigDecimal.ZERO;
            usedGpuSeconds += seconds;
            usedMemoryGbSeconds += memory.doubleValue() * seconds;
        }
        return new QuotaRawUsage(usedGpuSeconds, usedMemoryGbSeconds);
    }

    private double resolveTaskSeconds(GpuTask task) {
        if (task.getActualSeconds() != null) {
            return task.getActualSeconds().doubleValue();
        }
        if (task.getEstimatedSeconds() != null) {
            return task.getEstimatedSeconds().doubleValue();
        }
        return quotaPolicy.getDefaultEstimatedSeconds();
    }

    private double estimateSubmitGpuSeconds(SubmitTaskRequest request) {
        if (request.getComputeUnitsGflop() != null && request.getComputeUnitsGflop().doubleValue() > 0) {
            // 提交阶段无法确定GPU型号，使用默认估时兜底
            return quotaPolicy.getDefaultEstimatedSeconds();
        }
        return quotaPolicy.getDefaultEstimatedSeconds();
    }

    private double estimateSubmitMemoryGbSeconds(SubmitTaskRequest request, double submitGpuSeconds) {
        BigDecimal memory = request.getMinMemoryGb() != null ? request.getMinMemoryGb() : BigDecimal.ZERO;
        return memory.doubleValue() * submitGpuSeconds;
    }

    private record QuotaRawUsage(double usedGpuSeconds, double usedMemoryGbSeconds) {
    }
}

