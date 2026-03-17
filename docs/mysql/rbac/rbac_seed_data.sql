-- ============================================================
-- RBAC Seed Data — Minimal Bootstrap Records
-- Database : rbac_test_db
-- Roles    : System Administrator · Task Reviewer · Regular User
-- Resources: User Mgmt · Role Mgmt · Permission Mgmt
-- Spring Security compatible: ROLE_ prefix on role codes
-- Created  : 2026-03-17
-- ============================================================
-- Insertion order respects FK constraints:
--   resource → permission → role → role_permission → user → user_role
-- ============================================================

USE `rbac_test_db`;

-- ============================================================
-- 1. RESOURCES
--    type: 1=Menu  2=API  3=Button  4=Data
-- ============================================================

INSERT INTO `resource`
  (`id`, `code`, `name`, `type`, `parent_id`, `path`, `sort_order`, `description`, `status`)
VALUES
  -- ── Top-level menu groups ─────────────────────────────────
  (1,  'sys',                'System Management',    1, NULL, '/sys',                    0,  'Top-level system management menu',        1),

  -- ── User Management (parent = sys) ───────────────────────
  (2,  'sys:user',           'User Management',      1,  1,   '/sys/user',               10, 'User list page',                          1),
  (3,  'sys:user:api',       'User API',             2,  2,   '/api/sys/user/**',         0,  'RESTful endpoints for user operations',   1),
  (4,  'sys:user:btn:add',   'Add User Button',      3,  2,   NULL,                      0,  'Button: add new user',                    1),
  (5,  'sys:user:btn:edit',  'Edit User Button',     3,  2,   NULL,                      1,  'Button: edit existing user',              1),
  (6,  'sys:user:btn:del',   'Delete User Button',   3,  2,   NULL,                      2,  'Button: delete user',                     1),
  (7,  'sys:user:btn:reset', 'Reset Password Button',3,  2,   NULL,                      3,  'Button: reset user password',             1),

  -- ── Role Management (parent = sys) ───────────────────────
  (8,  'sys:role',           'Role Management',      1,  1,   '/sys/role',               20, 'Role list page',                          1),
  (9,  'sys:role:api',       'Role API',             2,  8,   '/api/sys/role/**',         0,  'RESTful endpoints for role operations',   1),
  (10, 'sys:role:btn:add',   'Add Role Button',      3,  8,   NULL,                      0,  'Button: create role',                     1),
  (11, 'sys:role:btn:edit',  'Edit Role Button',     3,  8,   NULL,                      1,  'Button: edit role',                       1),
  (12, 'sys:role:btn:del',   'Delete Role Button',   3,  8,   NULL,                      2,  'Button: delete role',                     1),
  (13, 'sys:role:btn:assign','Assign Permissions Button',3,8, NULL,                      3,  'Button: assign permissions to role',      1),

  -- ── Permission Management (parent = sys) ─────────────────
  (14, 'sys:perm',           'Permission Management',1,  1,   '/sys/permission',         30, 'Permission list page',                    1),
  (15, 'sys:perm:api',       'Permission API',       2, 14,   '/api/sys/permission/**',   0,  'RESTful endpoints for permission ops',    1),
  (16, 'sys:perm:btn:add',   'Add Permission Button',3, 14,   NULL,                      0,  'Button: add permission',                  1),
  (17, 'sys:perm:btn:edit',  'Edit Permission Button',3,14,   NULL,                      1,  'Button: edit permission',                 1),

  -- ── Task Management (parent = sys) ───────────────────────
  (18, 'sys:task',           'Task Management',      1,  1,   '/sys/task',               40, 'Task list page',                          1),
  (19, 'sys:task:api',       'Task API',             2, 18,   '/api/sys/task/**',         0,  'RESTful endpoints for task operations',   1),
  (20, 'sys:task:btn:review','Review Task Button',   3, 18,   NULL,                      0,  'Button: approve or reject a task',        1),
  (21, 'sys:task:btn:view',  'View Task Button',     3, 18,   NULL,                      1,  'Button: view task detail',                1);


-- ============================================================
-- 2. PERMISSIONS
--    One row per (resource, action) pair.
--    Spring Security @PreAuthorize uses the `code` field.
-- ============================================================

INSERT INTO `permission`
  (`id`, `code`, `name`, `resource_id`, `action`, `description`, `status`)
VALUES
  -- ── User permissions ──────────────────────────────────────
  (1,  'sys:user:view',   'View Users',          2,  'view',   'Query and list users',          1),
  (2,  'sys:user:create', 'Create User',         2,  'create', 'Add a new user',                1),
  (3,  'sys:user:edit',   'Edit User',           2,  'edit',   'Modify user profile/status',    1),
  (4,  'sys:user:delete', 'Delete User',         2,  'delete', 'Soft-delete a user',            1),
  (5,  'sys:user:reset',  'Reset Password',      2,  'reset',  'Reset user password',           1),
  (6,  'sys:user:export', 'Export Users',        2,  'export', 'Export user list to CSV/Excel', 1),

  -- ── Role permissions ──────────────────────────────────────
  (7,  'sys:role:view',   'View Roles',          8,  'view',   'Query and list roles',          1),
  (8,  'sys:role:create', 'Create Role',         8,  'create', 'Create a new role',             1),
  (9,  'sys:role:edit',   'Edit Role',           8,  'edit',   'Modify role properties',        1),
  (10, 'sys:role:delete', 'Delete Role',         8,  'delete', 'Remove a role',                 1),
  (11, 'sys:role:assign', 'Assign Permissions',  8,  'assign', 'Bind permissions to a role',   1),

  -- ── Permission management permissions ─────────────────────
  (12, 'sys:perm:view',   'View Permissions',   14,  'view',   'Query and list permissions',    1),
  (13, 'sys:perm:create', 'Create Permission',  14,  'create', 'Define a new permission',       1),
  (14, 'sys:perm:edit',   'Edit Permission',    14,  'edit',   'Modify permission definition',  1),

  -- ── Task permissions ──────────────────────────────────────
  (15, 'sys:task:view',   'View Tasks',         18,  'view',   'View task list and detail',     1),
  (16, 'sys:task:review', 'Review Task',        18,  'review', 'Approve or reject a task',      1),
  (17, 'sys:task:create', 'Create Task',        18,  'create', 'Submit a new task',             1),
  (18, 'sys:task:edit',   'Edit Own Task',      18,  'edit',   'Edit a self-submitted task',    1);


-- ============================================================
-- 3. ROLES
--    role_type: 1=System  2=Custom  3=Temporary
--    Spring Security requires the ROLE_ prefix for hasRole()
-- ============================================================

INSERT INTO `role`
  (`id`, `code`, `name`, `parent_role_id`, `role_type`, `sort_order`, `description`, `status`)
VALUES
  (1, 'ROLE_ADMIN',
      'System Administrator',
      NULL,   -- no parent; top of hierarchy
      1,      -- System role
      0,
      'Full access to user, role, and permission management. '
      'Maps to Spring Security ROLE_ADMIN.',
      1),

  (2, 'ROLE_TASK_REVIEWER',
      'Task Reviewer',
      NULL,   -- independent role (not a child of admin)
      1,      -- System role
      10,
      'Can view and review (approve/reject) tasks. '
      'Read-only on user/role data. '
      'Maps to Spring Security ROLE_TASK_REVIEWER.',
      1),

  (3, 'ROLE_USER',
      'Regular User',
      NULL,   -- base role
      1,      -- System role
      20,
      'Can view own profile and submit/edit own tasks. '
      'No access to management pages. '
      'Maps to Spring Security ROLE_USER.',
      1);


-- ============================================================
-- 4. ROLE ↔ PERMISSION  (many-to-many)
-- ============================================================

INSERT INTO `role_permission` (`role_id`, `permission_id`)
VALUES
  -- ── ROLE_ADMIN : full access to everything ────────────────
  (1,  1),  -- sys:user:view
  (1,  2),  -- sys:user:create
  (1,  3),  -- sys:user:edit
  (1,  4),  -- sys:user:delete
  (1,  5),  -- sys:user:reset
  (1,  6),  -- sys:user:export
  (1,  7),  -- sys:role:view
  (1,  8),  -- sys:role:create
  (1,  9),  -- sys:role:edit
  (1, 10),  -- sys:role:delete
  (1, 11),  -- sys:role:assign
  (1, 12),  -- sys:perm:view
  (1, 13),  -- sys:perm:create
  (1, 14),  -- sys:perm:edit
  (1, 15),  -- sys:task:view
  (1, 16),  -- sys:task:review
  (1, 17),  -- sys:task:create
  (1, 18),  -- sys:task:edit

  -- ── ROLE_TASK_REVIEWER : view users/roles + full task ops ─
  (2,  1),  -- sys:user:view      (read-only)
  (2,  7),  -- sys:role:view      (read-only)
  (2, 12),  -- sys:perm:view      (read-only)
  (2, 15),  -- sys:task:view
  (2, 16),  -- sys:task:review    (key privilege)
  (2, 17),  -- sys:task:create
  (2, 18),  -- sys:task:edit

  -- ── ROLE_USER : self-service only ─────────────────────────
  (3, 15),  -- sys:task:view
  (3, 17),  -- sys:task:create
  (3, 18);  -- sys:task:edit      (own tasks only)


-- ============================================================
-- 5. USERS  (bcrypt hash of 'Admin@123' / 'Review@123' / 'User@123')
--    Generate real hashes with BCryptPasswordEncoder in your app.
--    user_type: 1=Normal  2=Admin  3=SuperAdmin
--    status   : 1=Active  0=Disabled  2=Locked
-- ============================================================

INSERT INTO `user`
  (`id`, `username`, `password`, `nickname`, `email`, `user_type`, `status`, `created_by`)
VALUES
  (1, 'admin',
      '$2a$12$demoHashPlaceholderAdminAAAAAAAAAAAAAAAAAAAAAAAAAAA',
      'System Admin',
      'admin@example.com',
      3,   -- SuperAdmin
      1,
      NULL),

  (2, 'reviewer01',
      '$2a$12$demoHashPlaceholderReviewerAAAAAAAAAAAAAAAAAAAAAAAA',
      'Task Reviewer One',
      'reviewer01@example.com',
      1,   -- Normal
      1,
      1),

  (3, 'user01',
      '$2a$12$demoHashPlaceholderUserAAAAAAAAAAAAAAAAAAAAAAAAAAAA',
      'Regular User One',
      'user01@example.com',
      1,   -- Normal
      1,
      1);


-- ============================================================
-- 6. USER ↔ ROLE  (many-to-many)
--    expires_at NULL = permanent assignment
-- ============================================================

INSERT INTO `user_role` (`user_id`, `role_id`, `expires_at`, `granted_by`)
VALUES
  (1, 1, NULL, NULL),   -- admin       → ROLE_ADMIN         (permanent, self-bootstrapped)
  (2, 2, NULL, 1),      -- reviewer01  → ROLE_TASK_REVIEWER  (permanent, granted by admin)
  (3, 3, NULL, 1);      -- user01      → ROLE_USER           (permanent, granted by admin)


-- ============================================================
-- END OF SEED DATA
-- ============================================================
