package com.sakurain.gpuscheduler.dto.task;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 提交GPU任务请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitTaskRequest {

    @NotBlank(message = "任务名称不能为空")
    @Size(max = 128, message = "任务名称不能超过128个字符")
    private String title;

    @Size(max = 65535, message = "描述过长")
    private String description;

    @NotBlank(message = "任务类型不能为空")
    @Size(max = 64, message = "任务类型不能超过64个字符")
    private String taskType;

    @NotNull(message = "最低显存需求不能为空")
    @DecimalMin(value = "0.01", message = "最低显存需求必须大于0")
    private BigDecimal minMemoryGb;

    @NotNull(message = "计算量不能为空")
    @DecimalMin(value = "0.0001", message = "计算量必须大于0")
    private BigDecimal computeUnitsGflop;

    @Min(value = 1, message = "优先级最小为1")
    @Max(value = 10, message = "优先级最大为10")
    private Integer basePriority = 5;
}
