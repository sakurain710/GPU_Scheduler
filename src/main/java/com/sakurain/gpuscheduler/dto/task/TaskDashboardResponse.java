package com.sakurain.gpuscheduler.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Task dashboard response")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDashboardResponse {

    private CurrentUserTaskStats userStats;
    private TaskWorkbenchPage taskList;
    private GpuRadarSnapshot gpuRadar;
    private List<PriorityQueueTopItem> priorityTop5;
    private LocalDateTime timestamp;
}
