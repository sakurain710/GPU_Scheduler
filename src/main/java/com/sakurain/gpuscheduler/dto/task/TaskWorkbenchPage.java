package com.sakurain.gpuscheduler.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "Task workbench page")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskWorkbenchPage {

    private List<TaskWorkbenchItem> records;
    private Long total;
    private Long size;
    private Long current;
    private Long pages;
}
