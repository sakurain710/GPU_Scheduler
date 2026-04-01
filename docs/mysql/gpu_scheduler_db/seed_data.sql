-- ============================================================
-- GPU Scheduler Seed Data
-- Database: gpu_scheduler_db
-- Purpose : Bootstrap 3 roles and 3 users (Normal / Reviewer / Admin)
-- ============================================================

USE `gpu_scheduler_db`;

-- ------------------------------------------------------------
-- 1) Roles
-- ------------------------------------------------------------
INSERT INTO `role` (`code`, `name`, `role_type`, `sort_order`, `description`, `status`)
VALUES
  ('ROLE_USER', 'Normal', 1, 20, '普通用户，可提交并查看自身任务', 1),
  ('ROLE_TASK_REVIEWER', 'Reviewer', 1, 10, '任务审批员，可查看任务并执行审批', 1),
  ('ROLE_ADMIN', 'Admin', 1, 0, '系统管理员，拥有系统管理权限', 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `role_type` = VALUES(`role_type`),
  `sort_order` = VALUES(`sort_order`),
  `description` = VALUES(`description`),
  `status` = VALUES(`status`);

-- ------------------------------------------------------------
-- 2) Users
-- Password hash below is BCrypt for plaintext "password"
-- ------------------------------------------------------------
INSERT INTO `user` (`username`, `password`, `nickname`, `email`, `user_type`, `status`, `created_by`)
VALUES
  ('admin', '$2a$10$7EqJtq98hPqEX7fNZaFWoO.H7h7H5f1fJf0hB1iKsFcfoTmyaKOe.', 'Admin', 'admin@gpu-scheduler.local', 3, 1, NULL),
  ('reviewer', '$2a$10$7EqJtq98hPqEX7fNZaFWoO.H7h7H5f1fJf0hB1iKsFcfoTmyaKOe.', 'Reviewer', 'reviewer@gpu-scheduler.local', 2, 1, NULL),
  ('normal', '$2a$10$7EqJtq98hPqEX7fNZaFWoO.H7h7H5f1fJf0hB1iKsFcfoTmyaKOe.', 'Normal', 'normal@gpu-scheduler.local', 1, 1, NULL)
ON DUPLICATE KEY UPDATE
  `password` = VALUES(`password`),
  `nickname` = VALUES(`nickname`),
  `email` = VALUES(`email`),
  `user_type` = VALUES(`user_type`),
  `status` = VALUES(`status`);

UPDATE `user` reviewer
JOIN `user` admin ON admin.`username` = 'admin'
SET reviewer.`created_by` = admin.`id`
WHERE reviewer.`username` IN ('reviewer', 'normal');

-- ------------------------------------------------------------
-- 3) User-Role mapping
-- ------------------------------------------------------------
INSERT INTO `user_role` (`user_id`, `role_id`, `expires_at`, `granted_by`)
SELECT u.`id`, r.`id`, NULL, NULL
FROM `user` u
JOIN `role` r ON r.`code` = 'ROLE_ADMIN'
WHERE u.`username` = 'admin'
ON DUPLICATE KEY UPDATE
  `expires_at` = VALUES(`expires_at`),
  `granted_by` = VALUES(`granted_by`);

INSERT INTO `user_role` (`user_id`, `role_id`, `expires_at`, `granted_by`)
SELECT u.`id`, r.`id`, NULL, admin.`id`
FROM `user` u
JOIN `role` r ON r.`code` = 'ROLE_TASK_REVIEWER'
JOIN `user` admin ON admin.`username` = 'admin'
WHERE u.`username` = 'reviewer'
ON DUPLICATE KEY UPDATE
  `expires_at` = VALUES(`expires_at`),
  `granted_by` = VALUES(`granted_by`);

INSERT INTO `user_role` (`user_id`, `role_id`, `expires_at`, `granted_by`)
SELECT u.`id`, r.`id`, NULL, admin.`id`
FROM `user` u
JOIN `role` r ON r.`code` = 'ROLE_USER'
JOIN `user` admin ON admin.`username` = 'admin'
WHERE u.`username` = 'normal'
ON DUPLICATE KEY UPDATE
  `expires_at` = VALUES(`expires_at`),
  `granted_by` = VALUES(`granted_by`);

-- ============================================================
-- End of seed
-- ============================================================
