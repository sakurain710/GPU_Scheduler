package com.sakurain.gpuscheduler.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "Task execution logs response")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutionLogResponse {

    @Schema(description = "Task summary")
    private TaskExecutionLogTaskSummary task;

    @Schema(description = "Task execution logs")
    private List<TaskExecutionLogItem> logs;
}
