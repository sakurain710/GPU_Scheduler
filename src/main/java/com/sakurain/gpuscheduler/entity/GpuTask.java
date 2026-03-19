package com.sakurain.gpuscheduler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * GPU计算任务实体类（优先队列条目）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("gpu_task")
public class GpuTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 提交用户ID（用户删除后为NULL）
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 分配的GPU（排队中为NULL）
     */
    @TableField("gpu_id")
    private Long gpuId;

    /**
     * 任务名称
     */
    @TableField("title")
    private String title;

    /**
     * 任务详情描述
     */
    @TableField("description")
    private String description;

    /**
     * 任务类型（例如 model_training / inference / rendering）
     */
    @TableField("task_type")
    private String taskType;

    /**
     * 最低显存需求（GB），BestFit选择 memory_gb >= 此值的最小GPU
     */
    @TableField("min_memory_gb")
    private BigDecimal minMemoryGb;

    /**
     * 总FP32计算量（GFLOP）
     * estimated_seconds = 此值 / (gpu.computing_power_tflops × 1000)
     */
    @TableField("compute_units_gflop")
    private BigDecimal computeUnitsGflop;

    /**
     * 用户指定的基础优先级 1（低）– 10（高）
     */
    @TableField("base_priority")
    private Integer basePriority;

    /**
     * 入队时间，老化窗口起点（毫秒精度）
     */
    @TableField("enqueue_at")
    private LocalDateTime enqueueAt;

    /**
     * GPU分配并启动执行线程的时间
     */
    @TableField("dispatched_at")
    private LocalDateTime dispatchedAt;

    /**
     * 预计完成时间 = dispatched_at + estimated_seconds
     */
    @TableField("estimated_finish_at")
    private LocalDateTime estimatedFinishAt;

    /**
     * 实际完成时间
     */
    @TableField("finished_at")
    private LocalDateTime finishedAt;

    /**
     * 预估执行秒数 = compute_units_gflop / (computing_power_tflops × 1000)
     */
    @TableField("estimated_seconds")
    private BigDecimal estimatedSeconds;

    /**
     * 实际执行秒数（模拟线程的墙钟时间）
     */
    @TableField("actual_seconds")
    private BigDecimal actualSeconds;

    /**
     * 状态：1=Pending 2=Queued 3=Running 4=Completed 5=Failed 6=Cancelled
     */
    @TableField("status")
    private Integer status;

    /**
     * 失败时的错误信息
     */
    @TableField("error_message")
    private String errorMessage;

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
