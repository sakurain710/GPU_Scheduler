package com.sakurain.gpuscheduler.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务死信队列表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("task_dlq")
public class TaskDlq {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("failure_reason")
    private String failureReason;

    @TableField("payload")
    private String payload;

    /**
     * 1=Pending 2=Reprocessed 3=Ignored
     */
    @TableField("status")
    private Integer status;

    @TableField("processed_by")
    private Long processedBy;

    @TableField("processed_at")
    private LocalDateTime processedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
