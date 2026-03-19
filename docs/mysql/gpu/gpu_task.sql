-- ============================================================
--  GPU Resource Table
-- ============================================================
CREATE TABLE `gpu` (
  `id`                    BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
  `name`                  VARCHAR(128)     NOT NULL                 COMMENT 'GPU model name (e.g. NVIDIA A100 80G)',
  `manufacturer`          VARCHAR(64)      NOT NULL                 COMMENT 'Manufacturer (e.g. NVIDIA / AMD)',
  `memory_gb`             DECIMAL(8,2)     NOT NULL                 COMMENT 'Total VRAM in GB (used by BestFit)',
  `computing_power_tflops` DECIMAL(10,4)   NOT NULL                 COMMENT 'FP32 peak throughput in TFLOPS; used to compute estimated_time = compute_units_gflop / (power × 1000)',
  `status`                TINYINT UNSIGNED NOT NULL DEFAULT 1       COMMENT '1=Idle 2=Busy 3=Offline 4=Maintenance',
  `remark`                VARCHAR(500)         NULL DEFAULT NULL,
  `created_by`            BIGINT UNSIGNED      NULL DEFAULT NULL,
  `created_at`            DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`            DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at`            DATETIME             NULL DEFAULT NULL    COMMENT 'Soft delete timestamp',
  PRIMARY KEY (`id`),
  KEY `idx_gpu_status`   (`status`),
  KEY `idx_gpu_deleted`  (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Physical GPU hardware pool';


-- ============================================================
--  GPU Task Table  (the priority queue entry)
-- ============================================================
CREATE TABLE `gpu_task` (
  `id`                    BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
  `user_id`               BIGINT UNSIGNED  NOT NULL                 COMMENT 'FK → user.id',
  `gpu_id`                BIGINT UNSIGNED      NULL DEFAULT NULL    COMMENT 'Assigned GPU (NULL while pending)',

  -- ── Task description ─────────────────────────────────────
  `title`                 VARCHAR(128)     NOT NULL                 COMMENT 'Short task name',
  `description`           TEXT                 NULL DEFAULT NULL    COMMENT 'Optional detail',
  `task_type`             VARCHAR(64)      NOT NULL                 COMMENT 'Category label (e.g. model_training / inference / rendering)',

  -- ── Resource requirements ─────────────────────────────────
  `min_memory_gb`         DECIMAL(8,2)     NOT NULL                 COMMENT 'Minimum VRAM needed; BestFit uses this to pick the closest-fit GPU',
  `compute_units_gflop`   DECIMAL(16,4)    NOT NULL                 COMMENT 'Total floating-point operations (GFLOP) required. Thread sleep = this / (gpu.computing_power_tflops × 1000) seconds',

  -- ── Priority & aging ─────────────────────────────────────
  `base_priority`         TINYINT UNSIGNED NOT NULL DEFAULT 5       COMMENT 'User-supplied priority 1 (low) – 10 (high)',
  `age_weight`            DECIMAL(8,4)     NOT NULL DEFAULT 0       COMMENT 'Accumulated aging bonus added by the scheduler; reset to 0 on dispatch',
  `effective_priority`    DECIMAL(10,4)    AS (`base_priority` + `age_weight`) STORED
                                                                    COMMENT 'Computed: base_priority + age_weight; ORDER BY this DESC to dequeue',
  `aging_updated_at`      DATETIME             NULL DEFAULT NULL    COMMENT 'Timestamp of last age_weight recalculation',

  -- ── Lifecycle timestamps ──────────────────────────────────
  `enqueue_at`            DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'When task entered the queue; aging window starts here',
  `dispatched_at`         DATETIME             NULL DEFAULT NULL    COMMENT 'When a GPU was assigned and thread started',
  `estimated_finish_at`   DATETIME             NULL DEFAULT NULL    COMMENT 'dispatched_at + estimated_seconds; set on dispatch',
  `finished_at`           DATETIME             NULL DEFAULT NULL    COMMENT 'Actual completion time',

  -- ── Execution result ─────────────────────────────────────
  `estimated_seconds`     DECIMAL(12,4)        NULL DEFAULT NULL    COMMENT 'compute_units_gflop / (gpu.computing_power_tflops × 1000)',
  `actual_seconds`        DECIMAL(12,4)        NULL DEFAULT NULL    COMMENT 'Measured wall-clock time of the simulation thread',
  `status`                TINYINT UNSIGNED NOT NULL DEFAULT 1       COMMENT '1=Pending (row saved to DB; not yet pushed to Redis) 2=Queued (in Redis priority queue; aging applies here only) 3=Running (GPU assigned, ExecutorService thread active) 4=Completed 5=Failed 6=Cancelled',
  `error_message`         TEXT                 NULL DEFAULT NULL    COMMENT 'Populated on failure',

  -- ── Audit ────────────────────────────────────────────────
  `created_at`            DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`            DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at`            DATETIME             NULL DEFAULT NULL    COMMENT 'Soft delete timestamp',

  PRIMARY KEY (`id`),
  KEY `idx_task_user`         (`user_id`),
  KEY `idx_task_gpu`          (`gpu_id`),
  KEY `idx_task_status`       (`status`),
  KEY `idx_task_queue`        (`status`, `effective_priority` DESC, `enqueue_at`),  -- dequeue scan
  KEY `idx_task_aging`        (`status`, `aging_updated_at`),                        -- aging background job: WHERE status=2 AND aging_updated_at < NOW()-INTERVAL N MINUTE
  KEY `idx_task_deleted`      (`deleted_at`),
  CONSTRAINT `fk_task_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_task_gpu`  FOREIGN KEY (`gpu_id`)  REFERENCES `gpu`  (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='GPU compute task queue with BestFit scheduling and dynamic aging. Unit chain for estimated_seconds: compute_units_gflop [GFLOP] / (computing_power_tflops [TFLOPS] × 1000 [GFLOP/TFLOP]) = seconds. TFLOPS must never be used raw as GFLOPS — always multiply by 1000 first.';


-- ============================================================
--  Task Execution Log  (append-only audit trail)
-- ============================================================
CREATE TABLE `gpu_task_log` (
  `id`            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
  `task_id`       BIGINT UNSIGNED  NOT NULL                 COMMENT 'FK → gpu_task.id',
  `gpu_id`        BIGINT UNSIGNED      NULL DEFAULT NULL    COMMENT 'GPU involved in this event',
  `event`         VARCHAR(32)      NOT NULL                 COMMENT 'QUEUED / DISPATCHED / STARTED / COMPLETED / FAILED / CANCELLED / AGED',
  `old_status`    TINYINT UNSIGNED     NULL DEFAULT NULL    COMMENT 'Status before transition',
  `new_status`    TINYINT UNSIGNED     NULL DEFAULT NULL    COMMENT 'Status after transition',
  `age_delta`     DECIMAL(8,4)         NULL DEFAULT NULL    COMMENT 'age_weight increment applied (AGED events only)',
  `detail`        TEXT                 NULL DEFAULT NULL    COMMENT 'Free-form context (thread ID, error stack, etc.)',
  `operator_id`   BIGINT UNSIGNED      NULL DEFAULT NULL    COMMENT 'user.id of admin/system that triggered event',
  `created_at`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_log_task`      (`task_id`),
  KEY `idx_log_gpu`       (`gpu_id`),
  KEY `idx_log_event`     (`event`),
  KEY `idx_log_created`   (`created_at`),
  CONSTRAINT `fk_log_task` FOREIGN KEY (`task_id`) REFERENCES `gpu_task` (`id`),
  CONSTRAINT `fk_log_gpu`  FOREIGN KEY (`gpu_id`)  REFERENCES `gpu`      (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable audit log of every gpu_task lifecycle event';


-- Estimated time formula — the scheduler computes this on dispatch:
-- Unit chain: compute_units_gflop [GFLOP] / (computing_power_tflops [TFLOPS] × 1000 [GFLOP/TFLOP]) = estimated_seconds [s]
-- WARNING: computing_power_tflops is in TFLOPS. Multiply by 1000 to convert to GFLOPS before dividing.
--          Skipping the ×1000 produces a result ~1000× too large (seconds become kiloseconds).

-- SET estimated_seconds    = compute_units_gflop / (computing_power_tflops * 1000);
-- SET estimated_finish_at  = dispatched_at + INTERVAL estimated_seconds SECOND;