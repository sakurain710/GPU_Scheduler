package com.sakurain.gpuscheduler.dto.gpu;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新GPU状态请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGpuStatusRequest {

    /**
     * GPU状态：1=空闲 2=忙碌 3=离线 4=维护
     */
    @NotNull(message = "状态不能为空")
    @Min(value = 1, message = "状态值最小为1")
    @Max(value = 4, message = "状态值最大为4")
    private Integer status;
}
