-- ============================================================
-- RBAC Model Database Design
-- Database : rbac_test_db
-- Standard : Role-Based Access Control (RBAC / NIST RBAC Model)
-- Created  : 2026-03-17
-- Fixed    : 2026-03-19
-- ============================================================
-- Core entities:
--   User  →  UserRole  →  Role  →  RolePermission  →  Permission  →  Resource
-- Extended entities:
--   Role hierarchy (parent_role_id)
-- ============================================================
-- Fix summary:
--   [Fix 1] role.created_by — FK to user.id added via ALTER TABLE after user is defined
--   [Fix 2] Expired user_role rows — v_active_user_role view + cleanup EVENT added
--   [Fix 3] CHECK constraints — tinyint enum fields bounded on all tables
--   [Fix 4] granted_by audit fields — FK (ON DELETE SET NULL) added to role_permission & user_role
--   [Fix 5] Soft-delete FK gap — v_active_user view + policy comment; index made partial-friendly
-- ============================================================

CREATE DATABASE IF NOT EXISTS `rbac_test_db`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `rbac_test_db`;

-- ------------------------------------------------------------
-- 1. Resource  (system modules / objects being protected)
-- ------------------------------------------------------------
CREATE TABLE `resource` (
  `id`          BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
  `code`        VARCHAR(100)     NOT NULL                 COMMENT 'Unique resource code, e.g. "user:list"',
  `name`        VARCHAR(100)     NOT NULL                 COMMENT 'Human-readable name',
  `type`        TINYINT UNSIGNED NOT NULL DEFAULT 1       COMMENT '1=Menu 2=API 3=Button 4=Data',
  `parent_id`   BIGINT UNSIGNED      NULL DEFAULT NULL    COMMENT 'Parent resource (tree structure)',
  `path`        VARCHAR(255)         NULL DEFAULT NULL    COMMENT 'URL path or menu route',
  `sort_order`  INT UNSIGNED     NOT NULL DEFAULT 0       COMMENT 'Display sort order',
  `description` VARCHAR(500)         NULL DEFAULT NULL    COMMENT 'Resource description',
  `status`      TINYINT UNSIGNED NOT NULL DEFAULT 1       COMMENT '1=Active 0=Disabled',
  `created_at`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT `chk_resource_type`   CHECK (`type`   IN (1, 2, 3, 4)),
  CONSTRAINT `chk_resource_status` CHECK (`status` IN (0, 1)),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_resource_code` (`code`),
  KEY `idx_resource_parent` (`parent_id`),
  KEY `idx_resource_type`   (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Protected resources (menus, APIs, buttons, data scopes)';


-- ------------------------------------------------------------
-- 2. Permission  (actions that can be performed on resources)
-- ------------------------------------------------------------
CREATE TABLE `permission` (
  `id`          BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
  `code`        VARCHAR(150)     NOT NULL                 COMMENT 'Unique permission code, e.g. "user:list:view"',
  `name`        VARCHAR(100)     NOT NULL                 COMMENT 'Human-readable name',
  `resource_id` BIGINT UNSIGNED  NOT NULL                 COMMENT 'FK → resource.id',
  `action`      VARCHAR(50)      NOT NULL                 COMMENT 'Action: view / create / edit / delete / export …',
  `description` VARCHAR(500)         NULL DEFAULT NULL    COMMENT 'Permission description',
  `status`      TINYINT UNSIGNED NOT NULL DEFAULT 1       COMMENT '1=Active 0=Disabled',
  `created_at`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT `chk_permission_status` CHECK (`status` IN (0, 1)),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_permission_code` (`code`),
  KEY `idx_permission_resource` (`resource_id`),
  CONSTRAINT `fk_permission_resource`
    FOREIGN KEY (`resource_id`) REFERENCES `resource` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Fine-grained permissions (resource × action)';


-- ------------------------------------------------------------
-- 3. Role  (named collection of permissions; supports hierarchy)
-- ------------------------------------------------------------
CREATE TABLE `role` (
  `id`             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
  `code`           VARCHAR(100)     NOT NULL                 COMMENT 'Unique role code, e.g. "ROLE_ADMIN"',
  `name`           VARCHAR(100)     NOT NULL                 COMMENT 'Display name',
  `parent_role_id` BIGINT UNSIGNED      NULL DEFAULT NULL    COMMENT 'Parent role for hierarchical RBAC',
  `role_type`      TINYINT UNSIGNED NOT NULL DEFAULT 1       COMMENT '1=System 2=Custom 3=Temporary',
  `sort_order`     INT UNSIGNED     NOT NULL DEFAULT 0       COMMENT 'Display sort order',
  `description`    VARCHAR(500)         NULL DEFAULT NULL    COMMENT 'Role description',
  `status`         TINYINT UNSIGNED NOT NULL DEFAULT 1       COMMENT '1=Active 0=Disabled',
  `created_by`     BIGINT UNSIGNED      NULL DEFAULT NULL    COMMENT 'Creator user id — FK added post-user via ALTER TABLE',
  `created_at`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT `chk_role_type`   CHECK (`role_type` IN (1, 2, 3)),
  CONSTRAINT `chk_role_status` CHECK (`status`    IN (0, 1)),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_role_code` (`code`),
  KEY `idx_role_parent` (`parent_role_id`),
  KEY `idx_role_created_by` (`created_by`),
  CONSTRAINT `fk_role_parent`
      FOREIGN KEY (`parent_role_id`) REFERENCES `role` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_role_created_by`
      FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Roles — named permission bundles with optional hierarchy';


-- ------------------------------------------------------------
-- 4. Role ↔ Permission  (many-to-many)
-- ------------------------------------------------------------
CREATE TABLE `role_permission` (
  `id`            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `role_id`       BIGINT UNSIGNED  NOT NULL  COMMENT 'FK → role.id',
  `permission_id` BIGINT UNSIGNED  NOT NULL  COMMENT 'FK → permission.id',
  `granted_by`    BIGINT UNSIGNED      NULL  COMMENT 'Operator user id — FK added post-user via ALTER TABLE',
  `created_at`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_role_permission` (`role_id`, `permission_id`),
  KEY `idx_rp_permission` (`permission_id`),
  KEY `idx_rp_granted_by` (`granted_by`),
  CONSTRAINT `fk_rp_role`
    FOREIGN KEY (`role_id`)       REFERENCES `role`       (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_rp_permission`
    FOREIGN KEY (`permission_id`) REFERENCES `permission` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_rp_granted_by`
    FOREIGN KEY (`granted_by`) REFERENCES `user` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Role–Permission assignment (many-to-many)';


-- ------------------------------------------------------------
-- 5. User  (system accounts)
-- ------------------------------------------------------------
CREATE TABLE `user` (
  `id`           BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT 'Primary key',
  `username`     VARCHAR(64)      NOT NULL                 COMMENT 'Login name (unique)',
  `password`     VARCHAR(255)     NOT NULL                 COMMENT 'Hashed password (bcrypt)',
  `nickname`     VARCHAR(64)          NULL DEFAULT NULL    COMMENT 'Display name',
  `email`        VARCHAR(128)         NULL DEFAULT NULL    COMMENT 'Email address',
  `mobile`       VARCHAR(20)          NULL DEFAULT NULL    COMMENT 'Mobile number',
  `avatar`       VARCHAR(500)         NULL DEFAULT NULL    COMMENT 'Avatar URL',
  `gender`       TINYINT UNSIGNED NOT NULL DEFAULT 0       COMMENT '0=Unknown 1=Male 2=Female',
  `user_type`    TINYINT UNSIGNED NOT NULL DEFAULT 1       COMMENT '1=Normal 2=Reviewer 3=Admin',
  `status`       TINYINT UNSIGNED NOT NULL DEFAULT 1       COMMENT '1=Active 0=Disabled 2=Locked',
  `login_ip`     VARCHAR(50)          NULL DEFAULT NULL    COMMENT 'Last login IP',
  `login_at`     DATETIME             NULL DEFAULT NULL    COMMENT 'Last login time',
  `pwd_reset_at` DATETIME             NULL DEFAULT NULL    COMMENT 'Last password reset time',
  `remark`       VARCHAR(500)         NULL DEFAULT NULL,
  `created_by`   BIGINT UNSIGNED      NULL DEFAULT NULL,
  `created_at`   DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at`   DATETIME             NULL DEFAULT NULL    COMMENT 'Soft delete timestamp — use v_active_user for live rows only',
  CONSTRAINT `chk_user_gender`    CHECK (`gender`    IN (0, 1, 2)),
  CONSTRAINT `chk_user_user_type` CHECK (`user_type` IN (1, 2, 3)),
  CONSTRAINT `chk_user_status`    CHECK (`status`    IN (0, 1, 2)),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_username` (`username`),
  KEY `idx_user_email`   (`email`),
  KEY `idx_user_mobile`  (`mobile`),
  KEY `idx_user_deleted` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='System user accounts';


-- ------------------------------------------------------------
-- 6. User ↔ Role  (many-to-many, time-bounded)
-- ------------------------------------------------------------
CREATE TABLE `user_role` (
  `id`         BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED  NOT NULL  COMMENT 'FK → user.id',
  `role_id`    BIGINT UNSIGNED  NOT NULL  COMMENT 'FK → role.id',
  `expires_at` DATETIME             NULL DEFAULT NULL  COMMENT 'NULL = permanent; expired rows purged by evt_purge_expired_user_role',
  `granted_by` BIGINT UNSIGNED      NULL DEFAULT NULL  COMMENT 'Operator user id — FK → user.id ON DELETE SET NULL',
  `created_at` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_role` (`user_id`, `role_id`),
  KEY `idx_ur_role`    (`role_id`),
  KEY `idx_ur_expires` (`expires_at`),
  KEY `idx_ur_granted_by`      (`granted_by`),
  CONSTRAINT `fk_ur_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_ur_role`
    FOREIGN KEY (`role_id`) REFERENCES `role` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_ur_granted_by`
    FOREIGN KEY (`granted_by`) REFERENCES `user` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='User–Role assignment (many-to-many, supports expiry)';


CREATE OR REPLACE VIEW `v_active_user_role` AS
SELECT *
FROM   `user_role`
WHERE  `expires_at` IS NULL
   OR  `expires_at` > NOW();

DELIMITER $$
CREATE EVENT IF NOT EXISTS `evt_purge_expired_user_role`
    ON SCHEDULE EVERY 1 DAY
        STARTS (DATE(NOW()) + INTERVAL 1 DAY + INTERVAL 2 HOUR)  -- first run tomorrow at 02:00
    ON COMPLETION PRESERVE
    COMMENT 'Purge user_role rows whose expires_at has passed'
    DO
    BEGIN
        DELETE FROM `user_role`
        WHERE  `expires_at` IS NOT NULL
          AND  `expires_at` <= NOW();
    END$$
DELIMITER ;


CREATE OR REPLACE VIEW `v_active_user` AS
SELECT *
FROM   `user`
WHERE  `deleted_at` IS NULL;


-- ============================================================
-- END OF FILE
-- ============================================================