package com.sakurain.gpuscheduler.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Task workbench item")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskWorkbenchItem {

    private Long id;
    private Long userId;
    private Long gpuId;
    private String title;
    private String description;
    private String applyReason;
    private String taskType;
    private BigDecimal minMemoryGb;
    private Integer status;
    private String statusLabel;
    private Integer basePriority;
    private BigDecimal estimatedSeconds;
    private BigDecimal actualSeconds;
    private String errorMessage;
    private Long reviewerId;
    private LocalDateTime reviewAt;
    private String rejectReason;
    private String cancelReason;
    private LocalDateTime enqueueAt;
    private LocalDateTime dispatchedAt;
    private LocalDateTime estimatedFinishAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private Long queuePosition;
    private Long runningSeconds;
    private Long totalDurationSeconds;
    private String reviewStatus;
    private Long waitSeconds;
    private String resultSummary;
    private BigDecimal agingScore;
    private BigDecimal progressPct;
    private Long remainingExecutionSeconds;
    private BigDecimal memoryFitScore;
}
