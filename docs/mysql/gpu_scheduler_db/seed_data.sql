-- ============================================================
-- GPU Scheduler Seed Data
-- Database: gpu_scheduler_db
-- Purpose : Bootstrap roles/users/resources/permissions for frontend integration
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
-- 2) Resources (Menu / API / Button)
-- ------------------------------------------------------------
INSERT INTO `resource` (`code`, `name`, `type`, `parent_id`, `path`, `sort_order`, `description`, `status`)
VALUES
  ('menu:dashboard', 'Dashboard', 1, NULL, '/dashboard', 10, '监控大盘菜单', 1),
  ('menu:task', 'Task Center', 1, NULL, '/tasks', 20, '任务中心菜单', 1),
  ('menu:gpu', 'GPU Center', 1, NULL, '/gpus', 30, 'GPU 管理菜单', 1),
  ('menu:admin', 'System Admin', 1, NULL, '/admin', 40, '用户角色权限管理菜单', 1),

  ('api:monitoring:read', 'Read Monitoring Metrics', 2, (SELECT id FROM (SELECT id FROM resource WHERE code = 'menu:dashboard' LIMIT 1) t), '/api/metrics', 101, '监控指标查询接口', 1),
  ('api:task:approval:read', 'Read Pending Task Approval', 2, (SELECT id FROM (SELECT id FROM resource WHERE code = 'menu:task' LIMIT 1) t), '/api/tasks/approval/pending', 201, '任务审批查询接口', 1),
  ('btn:task:approval:review', 'Review Task Button', 3, (SELECT id FROM (SELECT id FROM resource WHERE code = 'menu:task' LIMIT 1) t), 'task-approve-btn', 202, '任务审批操作按钮', 1),
  ('api:gpu:read', 'Read GPU', 2, (SELECT id FROM (SELECT id FROM resource WHERE code = 'menu:gpu' LIMIT 1) t), '/api/gpus', 301, 'GPU 查询接口', 1),
  ('api:gpu:write', 'Write GPU', 2, (SELECT id FROM (SELECT id FROM resource WHERE code = 'menu:gpu' LIMIT 1) t), '/api/gpus', 302, 'GPU 写接口', 1),
  ('api:gpu:heartbeat', 'GPU Heartbeat', 2, (SELECT id FROM (SELECT id FROM resource WHERE code = 'menu:gpu' LIMIT 1) t), '/api/gpus/{gpuId}/heartbeat', 303, 'GPU 心跳接口', 1),
  ('btn:ops:manage', 'Ops Manage Button', 3, (SELECT id FROM (SELECT id FROM resource WHERE code = 'menu:gpu' LIMIT 1) t), 'ops-manage-btn', 304, '运维操作按钮', 1),
  ('api:user:manage', 'Manage User', 2, (SELECT id FROM (SELECT id FROM resource WHERE code = 'menu:admin' LIMIT 1) t), '/api/users', 401, '用户管理接口', 1),
  ('api:role:manage', 'Manage Role', 2, (SELECT id FROM (SELECT id FROM resource WHERE code = 'menu:admin' LIMIT 1) t), '/api/roles', 402, '角色管理接口', 1),
  ('api:rbac:read', 'Read RBAC Dictionary', 2, (SELECT id FROM (SELECT id FROM resource WHERE code = 'menu:admin' LIMIT 1) t), '/api/rbac', 403, 'RBAC 字典接口', 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `type` = VALUES(`type`),
  `parent_id` = VALUES(`parent_id`),
  `path` = VALUES(`path`),
  `sort_order` = VALUES(`sort_order`),
  `description` = VALUES(`description`),
  `status` = VALUES(`status`);

-- ------------------------------------------------------------
-- 3) Permissions
-- ------------------------------------------------------------
INSERT INTO `permission` (`code`, `name`, `resource_id`, `action`, `description`, `status`)
VALUES
  ('monitoring:read', 'Monitoring Read Permission', (SELECT id FROM (SELECT id FROM resource WHERE code = 'api:monitoring:read' LIMIT 1) t), 'view', '读取系统监控指标', 1),
  ('task:approval:read', 'Task Approval Read Permission', (SELECT id FROM (SELECT id FROM resource WHERE code = 'api:task:approval:read' LIMIT 1) t), 'view', '读取待审批任务', 1),
  ('task:approval:review', 'Task Approval Review Permission', (SELECT id FROM (SELECT id FROM resource WHERE code = 'btn:task:approval:review' LIMIT 1) t), 'review', '审批任务（按钮级）', 1),
  ('gpu:read', 'GPU Read Permission', (SELECT id FROM (SELECT id FROM resource WHERE code = 'api:gpu:read' LIMIT 1) t), 'view', '查看 GPU 资源信息', 1),
  ('gpu:write', 'GPU Write Permission', (SELECT id FROM (SELECT id FROM resource WHERE code = 'api:gpu:write' LIMIT 1) t), 'edit', '管理 GPU 资源信息', 1),
  ('gpu:heartbeat', 'GPU Heartbeat Permission', (SELECT id FROM (SELECT id FROM resource WHERE code = 'api:gpu:heartbeat' LIMIT 1) t), 'edit', '上报 GPU 心跳状态', 1),
  ('ops:manage', 'Ops Manage Permission', (SELECT id FROM (SELECT id FROM resource WHERE code = 'btn:ops:manage' LIMIT 1) t), 'manage', '执行运维控制操作（按钮级）', 1),
  ('user:manage', 'User Manage Permission', (SELECT id FROM (SELECT id FROM resource WHERE code = 'api:user:manage' LIMIT 1) t), 'manage', '管理用户信息', 1),
  ('role:manage', 'Role Manage Permission', (SELECT id FROM (SELECT id FROM resource WHERE code = 'api:role:manage' LIMIT 1) t), 'manage', '管理角色授权', 1),
  ('rbac:read', 'RBAC Read Permission', (SELECT id FROM (SELECT id FROM resource WHERE code = 'api:rbac:read' LIMIT 1) t), 'view', '读取 RBAC 字典数据', 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `resource_id` = VALUES(`resource_id`),
  `action` = VALUES(`action`),
  `description` = VALUES(`description`),
  `status` = VALUES(`status`);

-- ------------------------------------------------------------
-- 4) Users
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
-- 5) User-Role mapping
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

-- ------------------------------------------------------------
-- 6) Role-Permission mapping
-- ------------------------------------------------------------
INSERT INTO `role_permission` (`role_id`, `permission_id`, `granted_by`)
SELECT r.`id`, p.`id`, admin.`id`
FROM `role` r
JOIN `permission` p ON p.`code` IN (
  'monitoring:read',
  'task:approval:read',
  'task:approval:review',
  'gpu:read',
  'gpu:write',
  'gpu:heartbeat',
  'ops:manage',
  'user:manage',
  'role:manage',
  'rbac:read'
)
JOIN `user` admin ON admin.`username` = 'admin'
WHERE r.`code` = 'ROLE_ADMIN'
ON DUPLICATE KEY UPDATE
  `granted_by` = VALUES(`granted_by`);

INSERT INTO `role_permission` (`role_id`, `permission_id`, `granted_by`)
SELECT r.`id`, p.`id`, admin.`id`
FROM `role` r
JOIN `permission` p ON p.`code` IN (
  'monitoring:read',
  'task:approval:read',
  'task:approval:review'
)
JOIN `user` admin ON admin.`username` = 'admin'
WHERE r.`code` = 'ROLE_TASK_REVIEWER'
ON DUPLICATE KEY UPDATE
  `granted_by` = VALUES(`granted_by`);

INSERT INTO `role_permission` (`role_id`, `permission_id`, `granted_by`)
SELECT r.`id`, p.`id`, admin.`id`
FROM `role` r
JOIN `permission` p ON p.`code` IN ('monitoring:read')
JOIN `user` admin ON admin.`username` = 'admin'
WHERE r.`code` = 'ROLE_USER'
ON DUPLICATE KEY UPDATE
  `granted_by` = VALUES(`granted_by`);

-- ============================================================
-- End of seed
-- ============================================================
