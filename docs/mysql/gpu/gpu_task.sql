-- ============================================================
--  GPU Resource Tables
--  Fixed    : 2026-03-19
-- ============================================================


-- ============================================================
--  GPU Resource Table
-- ============================================================
CREATE TABLE `gpu` (
  `id`                    BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
  `name`                  VARCHAR(128)     NOT NULL                 COMMENT 'GPU model name (e.g. NVIDIA A100 80G)',
  `manufacturer`          VARCHAR(64)      NOT NULL                 COMMENT 'Manufacturer (e.g. NVIDIA / AMD)',
  `memory_gb`             DECIMAL(8,2)     NOT NULL                 COMMENT 'Total VRAM in GB (used by BestFit)',
  `computing_power_tflops` DECIMAL(10,4)    NOT NULL                 COMMENT 'FP32 peak throughput in TFLOPS. estimated_seconds = compute_units_gflop / (this × 1000)',
  `status`                TINYINT UNSIGNED NOT NULL DEFAULT 1       COMMENT '1=Idle 2=Busy 3=Offline 4=Maintenance',
  `remark`                VARCHAR(500)         NULL DEFAULT NULL,
  `created_by`            BIGINT UNSIGNED      NULL DEFAULT NULL,
  `created_at`            DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`            DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at`            DATETIME             NULL DEFAULT NULL    COMMENT 'Soft delete timestamp',
  CONSTRAINT `chk_gpu_status`  CHECK (`status` IN (1, 2, 3, 4)),
  CONSTRAINT `chk_gpu_memory`  CHECK (`memory_gb`              > 0),
  CONSTRAINT `chk_gpu_tflops`  CHECK (`computing_power_tflops` > 0),
  PRIMARY KEY (`id`),
  KEY `idx_gpu_status`   (`status`),
  KEY `idx_gpu_deleted`  (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Physical GPU hardware pool';


-- ============================================================
--  GPU Task Table  (the priority queue entry)
-- ============================================================
CREATE TABLE `gpu_task` (
  `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
  `user_id`             BIGINT UNSIGNED      NULL DEFAULT NULL    COMMENT 'FK → user.id (NULL when owner has been deleted)',
  `gpu_id`              BIGINT UNSIGNED      NULL DEFAULT NULL    COMMENT 'Assigned GPU (NULL while pending)',

  -- ── Task description ─────────────────────────────────────
  `title`               VARCHAR(128)     NOT NULL                 COMMENT 'Short task name',
  `description`         TEXT                 NULL DEFAULT NULL    COMMENT 'Optional detail',
  `task_type`           VARCHAR(64)      NOT NULL                 COMMENT 'Category label (e.g. model_training / inference / rendering)',

  -- ── Resource requirements ─────────────────────────────────
  `min_memory_gb`       DECIMAL(8,2)     NOT NULL                 COMMENT 'Minimum VRAM needed; BestFit selects the GPU with the smallest memory_gb >= this value',
  `compute_units_gflop` DECIMAL(16,4)    NOT NULL                 COMMENT 'Total FP32 ops in GFLOP. estimated_seconds = this / (gpu.computing_power_tflops × 1000)',

  -- ── Priority & aging ─────────────────────────────────────
  `base_priority`       TINYINT UNSIGNED NOT NULL DEFAULT 5       COMMENT 'User-supplied priority 1 (low) – 10 (high). Effective priority (with aging) is in v_task_queue.',

  -- ── Lifecycle timestamps (DATETIME(3) for ms precision) ───
  `enqueue_at`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'When task entered the queue; aging window starts here',
  `dispatched_at`       DATETIME(3)          NULL DEFAULT NULL    COMMENT 'When a GPU was assigned and the executor thread started',
  `estimated_finish_at` DATETIME(3)          NULL DEFAULT NULL    COMMENT 'dispatched_at + INTERVAL estimated_seconds SECOND; set on dispatch',
  `finished_at`         DATETIME(3)          NULL DEFAULT NULL    COMMENT 'Actual completion time',

  -- ── Execution result ──────────────────────────────────────
  `estimated_seconds`   DECIMAL(12,4)        NULL DEFAULT NULL    COMMENT 'compute_units_gflop / (gpu.computing_power_tflops × 1000). WARNING: always multiply TFLOPS by 1000 first.',
  `actual_seconds`      DECIMAL(12,4)        NULL DEFAULT NULL    COMMENT 'Measured wall-clock time of the simulation thread',
  `status`              TINYINT UNSIGNED NOT NULL DEFAULT 1       COMMENT '1=Pending 2=Queued 3=Running 4=Completed 5=Failed 6=Cancelled',
  `error_message`       TEXT                 NULL DEFAULT NULL    COMMENT 'Populated on failure',

  -- ── Audit ─────────────────────────────────────────────────
  `created_at`          DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`          DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at`          DATETIME             NULL DEFAULT NULL    COMMENT 'Soft delete timestamp',

  CONSTRAINT `chk_task_status`        CHECK (`status`         IN (1, 2, 3, 4, 5, 6)),
  CONSTRAINT `chk_task_base_priority` CHECK (`base_priority`  BETWEEN 1 AND 10),
  CONSTRAINT `chk_task_min_memory`    CHECK (`min_memory_gb`       > 0),
  CONSTRAINT `chk_task_compute_units` CHECK (`compute_units_gflop` > 0),
  CONSTRAINT `chk_task_est_seconds`   CHECK (`estimated_seconds` IS NULL OR `estimated_seconds` > 0),
  CONSTRAINT `chk_task_act_seconds`   CHECK (`actual_seconds`    IS NULL OR `actual_seconds`    > 0),
  PRIMARY KEY (`id`),
  KEY `idx_task_user`    (`user_id`),
  KEY `idx_task_gpu`     (`gpu_id`),
  KEY `idx_task_status`  (`status`),
  KEY `idx_task_queue`   (`status`, `base_priority` DESC, `enqueue_at`),
  KEY `idx_task_deleted` (`deleted_at`),
  CONSTRAINT `fk_task_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE SET NULL  ON UPDATE CASCADE,
  CONSTRAINT `fk_task_gpu`  FOREIGN KEY (`gpu_id`)  REFERENCES `gpu`  (`id`) ON DELETE RESTRICT  ON UPDATE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='GPU compute task queue. Dynamic aging is in v_task_queue. Unit chain: compute_units_gflop [GFLOP] / (computing_power_tflops [TFLOPS] × 1000 [GFLOP/TFLOP]) = estimated_seconds [s]. NEVER divide by raw TFLOPS — multiply by 1000 first.';


-- ============================================================
--  Task Execution Log  (append-only audit trail)
-- ============================================================
CREATE TABLE `gpu_task_log` (
  `id`          BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
  `task_id`     BIGINT UNSIGNED  NOT NULL                 COMMENT 'FK → gpu_task.id',
  `gpu_id`      BIGINT UNSIGNED      NULL DEFAULT NULL    COMMENT 'GPU involved in this event',
  `event`       VARCHAR(32)      NOT NULL                 COMMENT 'QUEUED / DISPATCHED / STARTED / COMPLETED / FAILED / CANCELLED',
  `old_status`  TINYINT UNSIGNED     NULL DEFAULT NULL    COMMENT 'Status before transition',
  `new_status`  TINYINT UNSIGNED     NULL DEFAULT NULL    COMMENT 'Status after transition',
  `age_delta`   DECIMAL(8,4)         NULL DEFAULT NULL    COMMENT 'Deprecated — was age_weight increment (AGED events). Always NULL now that aging is view-computed.',
  `detail`      TEXT                 NULL DEFAULT NULL    COMMENT 'Free-form context (thread ID, error stack, etc.)',
  `operator_id` BIGINT UNSIGNED      NULL DEFAULT NULL    COMMENT 'user.id of admin/system that triggered event',
  `created_at`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT `chk_log_old_status` CHECK (`old_status` IS NULL OR `old_status` IN (1, 2, 3, 4, 5, 6)),
  CONSTRAINT `chk_log_new_status` CHECK (`new_status` IS NULL OR `new_status` IN (1, 2, 3, 4, 5, 6)),

  PRIMARY KEY (`id`),
  KEY `idx_log_task`    (`task_id`),
  KEY `idx_log_gpu`     (`gpu_id`),
  KEY `idx_log_event`   (`event`),
  KEY `idx_log_created` (`created_at`),

  CONSTRAINT `fk_log_task` FOREIGN KEY (`task_id`) REFERENCES `gpu_task` (`id`) ON DELETE CASCADE  ON UPDATE CASCADE,
  CONSTRAINT `fk_log_gpu`  FOREIGN KEY (`gpu_id`)  REFERENCES `gpu`      (`id`) ON DELETE SET NULL ON UPDATE CASCADE

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable audit log of every gpu_task lifecycle event';


-- ============================================================
CREATE OR REPLACE VIEW `v_task_queue` AS
SELECT
    t.*,
    ROUND(
            t.`base_priority`
                + TIMESTAMPDIFF(MINUTE, t.`enqueue_at`, NOW()) * 0.1,
            4
    ) AS `effective_priority`
FROM `gpu_task` t
WHERE t.`status` = 2             -- Queued rows only (in Redis priority queue)
  AND t.`deleted_at` IS NULL;    -- Exclude soft-deleted tasks


-- ============================================================
--  Estimated time formula (reference — computed by scheduler on dispatch):
--
--  Unit chain:
--    compute_units_gflop  [GFLOP]
--    ─────────────────────────────────────────── = estimated_seconds [s]
--    computing_power_tflops [TFLOPS] × 1000 [GFLOP/TFLOP]
--
--  WARNING: computing_power_tflops is in TFLOPS, NOT GFLOPS.
--           Divide by (tflops × 1000), never by raw tflops.
--           Skipping ×1000 inflates the result by ~1000× (seconds → kiloseconds).
--
--  SET estimated_seconds   = compute_units_gflop / (computing_power_tflops * 1000);
--  SET estimated_finish_at = dispatched_at + INTERVAL estimated_seconds SECOND;
-- ============================================================