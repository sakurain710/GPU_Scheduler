package com.sakurain.gpuscheduler.dto.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 任务拒绝请求
 */
@Data
@Schema(description = "任务审批拒绝请求")
public class RejectTaskRequest {

    @Schema(description = "拒绝原因")
    private String reason;
}

