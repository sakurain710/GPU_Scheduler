package com.sakurain.gpuscheduler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * GPU硬件资源实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("gpu")
public class Gpu {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * GPU型号名称（例如 NVIDIA A100 80G）
     */
    @TableField("name")
    private String name;

    /**
     * 制造商（例如 NVIDIA / AMD）
     */
    @TableField("manufacturer")
    private String manufacturer;

    /**
     * 显存大小（GB），BestFit算法使用
     */
    @TableField("memory_gb")
    private BigDecimal memoryGb;

    /**
     * FP32峰值算力（TFLOPS）
     * estimated_seconds = compute_units_gflop / (此值 × 1000)
     */
    @TableField("computing_power_tflops")
    private BigDecimal computingPowerTflops;

    /**
     * 状态：1=空闲 2=忙碌 3=离线 4=维护
     */
    @TableField("status")
    private Integer status;

    /**
     * 备注
     */
    @TableField("remark")
    private String remark;

    /**
     * 创建者用户ID
     */
    @TableField("created_by")
    private Long createdBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 软删除时间戳
     */
    @TableLogic(value = "null", delval = "now()")
    @TableField("deleted_at")
    private LocalDateTime deletedAt;
}
