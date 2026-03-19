package com.sakurain.gpuscheduler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * GPU任务执行日志实体类（只追加的审计记录）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("gpu_task_log")
public class GpuTaskLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联任务ID
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 涉及的GPU ID
     */
    @TableField("gpu_id")
    private Long gpuId;

    /**
     * 事件类型：QUEUED / DISPATCHED / STARTED / COMPLETED / FAILED / CANCELLED
     */
    @TableField("event")
    private String event;

    /**
     * 转换前状态
     */
    @TableField("old_status")
    private Integer oldStatus;

    /**
     * 转换后状态
     */
    @TableField("new_status")
    private Integer newStatus;

    /**
     * 已弃用 — 原为age_weight增量，现在老化由视图计算，始终为NULL
     */
    @TableField("age_delta")
    private BigDecimal ageDelta;

    /**
     * 自由格式上下文（线程ID、错误堆栈等）
     */
    @TableField("detail")
    private String detail;

    /**
     * 触发事件的操作者用户ID
     */
    @TableField("operator_id")
    private Long operatorId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
