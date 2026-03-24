package com.sakurain.gpuscheduler.dto.gpu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * GPU响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpuResponse {

    private Long id;

    /** GPU型号名称（例如 NVIDIA A100 80G） */
    private String name;

    /** 制造商 */
    private String manufacturer;

    /** 显存大小（GB） */
    private BigDecimal memoryGb;

    /** FP32峰值算力（TFLOPS） */
    private BigDecimal computingPowerTflops;

    /** 状态码：1=空闲 2=忙碌 3=离线 4=维护 */
    private Integer status;

    /** 状态标签 */
    private String statusLabel;

    /** 备注 */
    private String remark;

    /** 创建者用户ID */
    private Long createdBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
