-- ============================================================
-- GPU Scheduler Unified Database Schema
-- Database: gpu_scheduler_db
-- Source merge: docs/mysql/gpu/gpu_task.sql + docs/mysql/rbac/rbac_test_db.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS `gpu_scheduler_db`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `gpu_scheduler_db`;

-- ------------------------------------------------------------
-- 1) User & RBAC Core
-- ------------------------------------------------------------

CREATE TABLE `user` (
  `id`           BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `username`     VARCHAR(64)      NOT NULL,
  `password`     VARCHAR(255)     NOT NULL,
  `nickname`     VARCHAR(64)          NULL DEFAULT NULL,
  `email`        VARCHAR(128)         NULL DEFAULT NULL,
  `mobile`       VARCHAR(20)          NULL DEFAULT NULL,
  `avatar`       VARCHAR(500)         NULL DEFAULT NULL,
  `gender`       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=Unknown 1=Male 2=Female',
  `user_type`    TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1=Normal 2=Reviewer 3=Admin',
  `status`       TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1=Active 0=Disabled 2=Locked',
  `login_ip`     VARCHAR(50)          NULL DEFAULT NULL,
  `login_at`     DATETIME             NULL DEFAULT NULL,
  `pwd_reset_at` DATETIME             NULL DEFAULT NULL,
  `remark`       VARCHAR(500)         NULL DEFAULT NULL,
  `created_by`   BIGINT UNSIGNED      NULL DEFAULT NULL,
  `created_at`   DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at`   DATETIME             NULL DEFAULT NULL,
  CONSTRAINT `chk_user_gender`    CHECK (`gender` IN (0, 1, 2)),
  CONSTRAINT `chk_user_user_type` CHECK (`user_type` IN (1, 2, 3)),
  CONSTRAINT `chk_user_status`    CHECK (`status` IN (0, 1, 2)),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_username` (`username`),
  UNIQUE KEY `uq_user_email` (`email`),
  KEY `idx_user_mobile`  (`mobile`),
  KEY `idx_user_deleted` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `resource` (
  `id`          BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `code`        VARCHAR(100)     NOT NULL,
  `name`        VARCHAR(100)     NOT NULL,
  `type`        TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1=Menu 2=API 3=Button 4=Data',
  `parent_id`   BIGINT UNSIGNED      NULL DEFAULT NULL,
  `path`        VARCHAR(255)         NULL DEFAULT NULL,
  `sort_order`  INT UNSIGNED     NOT NULL DEFAULT 0,
  `description` VARCHAR(500)         NULL DEFAULT NULL,
  `status`      TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1=Active 0=Disabled',
  `created_at`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT `chk_resource_type`   CHECK (`type` IN (1, 2, 3, 4)),
  CONSTRAINT `chk_resource_status` CHECK (`status` IN (0, 1)),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_resource_code` (`code`),
  KEY `idx_resource_parent` (`parent_id`),
  KEY `idx_resource_type` (`type`),
  CONSTRAINT `fk_resource_parent`
    FOREIGN KEY (`parent_id`) REFERENCES `resource` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `permission` (
  `id`          BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `code`        VARCHAR(150)     NOT NULL,
  `name`        VARCHAR(100)     NOT NULL,
  `resource_id` BIGINT UNSIGNED  NOT NULL,
  `action`      VARCHAR(50)      NOT NULL,
  `description` VARCHAR(500)         NULL DEFAULT NULL,
  `status`      TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1=Active 0=Disabled',
  `created_at`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT `chk_permission_status` CHECK (`status` IN (0, 1)),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_permission_code` (`code`),
  KEY `idx_permission_resource` (`resource_id`),
  CONSTRAINT `fk_permission_resource`
    FOREIGN KEY (`resource_id`) REFERENCES `resource` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `role` (
  `id`             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `code`           VARCHAR(100)     NOT NULL,
  `name`           VARCHAR(100)     NOT NULL,
  `parent_role_id` BIGINT UNSIGNED      NULL DEFAULT NULL,
  `role_type`      TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1=System 2=Custom 3=Temporary',
  `sort_order`     INT UNSIGNED     NOT NULL DEFAULT 0,
  `description`    VARCHAR(500)         NULL DEFAULT NULL,
  `status`         TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1=Active 0=Disabled',
  `created_by`     BIGINT UNSIGNED      NULL DEFAULT NULL,
  `created_at`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT `chk_role_type`   CHECK (`role_type` IN (1, 2, 3)),
  CONSTRAINT `chk_role_status` CHECK (`status` IN (0, 1)),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_role_code` (`code`),
  KEY `idx_role_parent` (`parent_role_id`),
  KEY `idx_role_created_by` (`created_by`),
  CONSTRAINT `fk_role_parent`
      FOREIGN KEY (`parent_role_id`) REFERENCES `role` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_role_created_by`
      FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `role_permission` (
  `id`            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `role_id`       BIGINT UNSIGNED  NOT NULL,
  `permission_id` BIGINT UNSIGNED  NOT NULL,
  `granted_by`    BIGINT UNSIGNED      NULL DEFAULT NULL,
  `created_at`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_role_permission` (`role_id`, `permission_id`),
  KEY `idx_rp_permission` (`permission_id`),
  KEY `idx_rp_granted_by` (`granted_by`),
  CONSTRAINT `fk_rp_role`
    FOREIGN KEY (`role_id`) REFERENCES `role` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_rp_permission`
    FOREIGN KEY (`permission_id`) REFERENCES `permission` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_rp_granted_by`
    FOREIGN KEY (`granted_by`) REFERENCES `user` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_role` (
  `id`         BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED  NOT NULL,
  `role_id`    BIGINT UNSIGNED  NOT NULL,
  `expires_at` DATETIME             NULL DEFAULT NULL,
  `granted_by` BIGINT UNSIGNED      NULL DEFAULT NULL,
  `created_at` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_role` (`user_id`, `role_id`),
  KEY `idx_ur_role` (`role_id`),
  KEY `idx_ur_expires` (`expires_at`),
  KEY `idx_ur_user_expires` (`user_id`, `expires_at`),
  KEY `idx_ur_granted_by` (`granted_by`),
  CONSTRAINT `fk_ur_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_ur_role`
    FOREIGN KEY (`role_id`) REFERENCES `role` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_ur_granted_by`
    FOREIGN KEY (`granted_by`) REFERENCES `user` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `user`
  ADD CONSTRAINT `fk_user_created_by`
    FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- ------------------------------------------------------------
-- 2) GPU Scheduling Domain
-- ------------------------------------------------------------

CREATE TABLE `gpu` (
  `id`                     BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `name`                   VARCHAR(128)     NOT NULL,
  `manufacturer`           VARCHAR(64)      NOT NULL,
  `memory_gb`              DECIMAL(8,2)     NOT NULL,
  `computing_power_tflops` DECIMAL(10,4)    NOT NULL,
  `status`                 TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1=Idle 2=Busy 3=Offline 4=Maintenance',
  `remark`                 VARCHAR(500)         NULL DEFAULT NULL,
  `created_by`             BIGINT UNSIGNED      NULL DEFAULT NULL,
  `created_at`             DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`             DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at`             DATETIME             NULL DEFAULT NULL,
  CONSTRAINT `chk_gpu_status` CHECK (`status` IN (1, 2, 3, 4)),
  CONSTRAINT `chk_gpu_memory` CHECK (`memory_gb` > 0),
  CONSTRAINT `chk_gpu_tflops` CHECK (`computing_power_tflops` > 0),
  PRIMARY KEY (`id`),
  KEY `idx_gpu_status` (`status`),
  KEY `idx_gpu_deleted` (`deleted_at`),
  CONSTRAINT `fk_gpu_created_by`
    FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `gpu_task` (
  `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `user_id`             BIGINT UNSIGNED      NULL DEFAULT NULL,
  `gpu_id`              BIGINT UNSIGNED      NULL DEFAULT NULL,
  `title`               VARCHAR(128)     NOT NULL,
  `description`         TEXT                 NULL DEFAULT NULL,
  `task_type`           VARCHAR(64)      NOT NULL COMMENT 'model_training | inference | rendering',
  `min_memory_gb`       DECIMAL(8,2)     NOT NULL,
  `compute_units_gflop` DECIMAL(16,4)    NOT NULL,
  `base_priority`       TINYINT UNSIGNED NOT NULL DEFAULT 5,
  `enqueue_at`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `dispatched_at`       DATETIME(3)          NULL DEFAULT NULL,
  `estimated_finish_at` DATETIME(3)          NULL DEFAULT NULL,
  `finished_at`         DATETIME(3)          NULL DEFAULT NULL,
  `estimated_seconds`   DECIMAL(12,4)        NULL DEFAULT NULL,
  `actual_seconds`      DECIMAL(12,4)        NULL DEFAULT NULL,
  `status`              TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1=Pending 2=Queued 3=Running 4=Completed 5=Failed 6=Cancelled 7=PendingApproval 8=Rejected',
  `error_message`       TEXT                 NULL DEFAULT NULL,
  `created_at`          DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`          DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at`          DATETIME             NULL DEFAULT NULL,
  CONSTRAINT `chk_task_status`        CHECK (`status` IN (1, 2, 3, 4, 5, 6, 7, 8)),
  CONSTRAINT `chk_task_type`          CHECK (`task_type` IN ('model_training', 'inference', 'rendering')),
  CONSTRAINT `chk_task_base_priority` CHECK (`base_priority` BETWEEN 1 AND 10),
  CONSTRAINT `chk_task_min_memory`    CHECK (`min_memory_gb` > 0),
  CONSTRAINT `chk_task_compute_units` CHECK (`compute_units_gflop` > 0),
  CONSTRAINT `chk_task_est_seconds`   CHECK (`estimated_seconds` IS NULL OR `estimated_seconds` > 0),
  CONSTRAINT `chk_task_act_seconds`   CHECK (`actual_seconds` IS NULL OR `actual_seconds` > 0),
  PRIMARY KEY (`id`),
  KEY `idx_task_user` (`user_id`),
  KEY `idx_task_gpu` (`gpu_id`),
  KEY `idx_task_status` (`status`),
  KEY `idx_task_status_enqueue` (`status`, `enqueue_at`),
  KEY `idx_task_user_status_created` (`user_id`, `status`, `created_at`),
  KEY `idx_task_queue` (`status`, `base_priority` DESC, `enqueue_at`),
  KEY `idx_task_deleted` (`deleted_at`),
  CONSTRAINT `fk_task_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_task_gpu`
    FOREIGN KEY (`gpu_id`) REFERENCES `gpu` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `gpu_task_log` (
  `id`          BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `task_id`     BIGINT UNSIGNED  NOT NULL,
  `gpu_id`      BIGINT UNSIGNED      NULL DEFAULT NULL,
  `event`       VARCHAR(32)      NOT NULL,
  `old_status`  TINYINT UNSIGNED     NULL DEFAULT NULL,
  `new_status`  TINYINT UNSIGNED     NULL DEFAULT NULL,
  `age_delta`   DECIMAL(8,4)         NULL DEFAULT NULL,
  `detail`      TEXT                 NULL DEFAULT NULL,
  `operator_id` BIGINT UNSIGNED      NULL DEFAULT NULL,
  `created_at`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT `chk_log_old_status` CHECK (`old_status` IS NULL OR `old_status` IN (1, 2, 3, 4, 5, 6, 7, 8)),
  CONSTRAINT `chk_log_new_status` CHECK (`new_status` IS NULL OR `new_status` IN (1, 2, 3, 4, 5, 6, 7, 8)),
  PRIMARY KEY (`id`),
  KEY `idx_log_task` (`task_id`),
  KEY `idx_log_gpu` (`gpu_id`),
  KEY `idx_log_event` (`event`),
  KEY `idx_log_created` (`created_at`),
  CONSTRAINT `fk_log_task`
    FOREIGN KEY (`task_id`) REFERENCES `gpu_task` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_log_gpu`
    FOREIGN KEY (`gpu_id`) REFERENCES `gpu` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_log_operator`
    FOREIGN KEY (`operator_id`) REFERENCES `user` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 3) Views and maintenance event
-- ------------------------------------------------------------

CREATE OR REPLACE VIEW `v_active_user` AS
SELECT *
FROM `user`
WHERE `deleted_at` IS NULL;

CREATE OR REPLACE VIEW `v_active_user_role` AS
SELECT *
FROM `user_role`
WHERE `expires_at` IS NULL
   OR `expires_at` > NOW();

CREATE OR REPLACE VIEW `v_task_queue` AS
SELECT
    t.*,
    ROUND(t.`base_priority` + TIMESTAMPDIFF(MINUTE, t.`enqueue_at`, NOW()) * 0.1, 4) AS `effective_priority`
FROM `gpu_task` t
WHERE t.`status` = 2
  AND t.`deleted_at` IS NULL;

DELIMITER $$
CREATE EVENT IF NOT EXISTS `evt_purge_expired_user_role`
    ON SCHEDULE EVERY 1 DAY
    STARTS (DATE(NOW()) + INTERVAL 1 DAY + INTERVAL 2 HOUR)
    ON COMPLETION PRESERVE
    COMMENT 'Purge expired user_role records'
DO
BEGIN
    DELETE FROM `user_role`
    WHERE `expires_at` IS NOT NULL
      AND `expires_at` <= NOW();
END$$
DELIMITER ;

-- ============================================================
-- End of file
-- ============================================================
