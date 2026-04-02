# 数据字典（第1部分）：用户与RBAC域

## 1. 范围
- 数据库来源：`docs/mysql/gpu_scheduler_db/gpu_scheduler_db.sql`、`seed_data.sql`
- 实体来源：`entity/User.java`、`Role.java`、`Permission.java`、`Resource.java`、`UserRole.java`、`RolePermission.java`
- 本部分覆盖：`user`、`role`、`resource`、`permission`、`user_role`、`role_permission`

## 2. user（用户表）

### 2.1 表说明
- 主键：`id`
- 唯一约束：`username`、`email`
- 软删除字段：`deleted_at`

### 2.2 字段字典

| 字段 | 类型 | 可空 | 默认值 | 含义 |
|---|---|---|---|---|
| id | BIGINT UNSIGNED | 否 | 自增 | 用户ID |
| username | VARCHAR(64) | 否 | - | 登录名（唯一） |
| password | VARCHAR(255) | 否 | - | 密码哈希 |
| nickname | VARCHAR(64) | 是 | NULL | 昵称 |
| email | VARCHAR(128) | 是 | NULL | 邮箱（唯一） |
| mobile | VARCHAR(20) | 是 | NULL | 手机号 |
| avatar | VARCHAR(500) | 是 | NULL | 头像URL |
| gender | TINYINT UNSIGNED | 否 | 0 | 性别 |
| user_type | TINYINT UNSIGNED | 否 | 1 | 用户类型 |
| status | TINYINT UNSIGNED | 否 | 1 | 账号状态 |
| login_ip | VARCHAR(50) | 是 | NULL | 最近登录IP |
| login_at | DATETIME | 是 | NULL | 最近登录时间 |
| pwd_reset_at | DATETIME | 是 | NULL | 最近密码重置时间 |
| remark | VARCHAR(500) | 是 | NULL | 备注 |
| created_by | BIGINT UNSIGNED | 是 | NULL | 创建人ID（FK user.id） |
| created_at | DATETIME | 否 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 否 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| deleted_at | DATETIME | 是 | NULL | 软删除时间 |

### 2.3 枚举值
- `gender`: `0=Unknown`, `1=Male`, `2=Female`
- `user_type`(SQL注释): `1=Normal`, `2=Reviewer`, `3=Admin`
- `status`: `1=Active`, `0=Disabled`, `2=Locked`

### 2.4 索引/约束
- `uq_user_username`、`uq_user_email`
- `idx_user_mobile`、`idx_user_deleted`
- CHECK：`gender/user_type/status` 合法值约束

## 3. role（角色表）

### 3.1 表说明
- 主键：`id`
- 唯一约束：`code`
- 自引用：`parent_role_id -> role.id`

### 3.2 字段字典

| 字段 | 类型 | 可空 | 默认值 | 含义 |
|---|---|---|---|---|
| id | BIGINT UNSIGNED | 否 | 自增 | 角色ID |
| code | VARCHAR(100) | 否 | - | 角色编码（唯一） |
| name | VARCHAR(100) | 否 | - | 角色名称 |
| parent_role_id | BIGINT UNSIGNED | 是 | NULL | 父角色ID |
| role_type | TINYINT UNSIGNED | 否 | 1 | 角色类型 |
| sort_order | INT UNSIGNED | 否 | 0 | 排序 |
| description | VARCHAR(500) | 是 | NULL | 描述 |
| status | TINYINT UNSIGNED | 否 | 1 | 状态 |
| created_by | BIGINT UNSIGNED | 是 | NULL | 创建人ID |
| created_at | DATETIME | 否 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 否 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

### 3.3 枚举值
- `role_type`: `1=System`, `2=Custom`, `3=Temporary`
- `status`: `1=Active`, `0=Disabled`

## 4. resource（资源表）

### 4.1 表说明
- 主键：`id`
- 唯一约束：`code`
- 自引用：`parent_id -> resource.id`

### 4.2 字段字典

| 字段 | 类型 | 可空 | 默认值 | 含义 |
|---|---|---|---|---|
| id | BIGINT UNSIGNED | 否 | 自增 | 资源ID |
| code | VARCHAR(100) | 否 | - | 资源编码（唯一） |
| name | VARCHAR(100) | 否 | - | 资源名称 |
| type | TINYINT UNSIGNED | 否 | 1 | 资源类型 |
| parent_id | BIGINT UNSIGNED | 是 | NULL | 父资源ID |
| path | VARCHAR(255) | 是 | NULL | 资源路径/路由 |
| sort_order | INT UNSIGNED | 否 | 0 | 排序 |
| description | VARCHAR(500) | 是 | NULL | 描述 |
| status | TINYINT UNSIGNED | 否 | 1 | 状态 |
| created_at | DATETIME | 否 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 否 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

### 4.3 枚举值
- `type`: `1=Menu`, `2=API`, `3=Button`, `4=Data`
- `status`: `1=Active`, `0=Disabled`

## 5. permission（权限表）

### 5.1 表说明
- 主键：`id`
- 唯一约束：`code`
- 外键：`resource_id -> resource.id`

### 5.2 字段字典

| 字段 | 类型 | 可空 | 默认值 | 含义 |
|---|---|---|---|---|
| id | BIGINT UNSIGNED | 否 | 自增 | 权限ID |
| code | VARCHAR(150) | 否 | - | 权限编码（唯一） |
| name | VARCHAR(100) | 否 | - | 权限名 |
| resource_id | BIGINT UNSIGNED | 否 | - | 关联资源ID |
| action | VARCHAR(50) | 否 | - | 操作类型 |
| description | VARCHAR(500) | 是 | NULL | 描述 |
| status | TINYINT UNSIGNED | 否 | 1 | 状态 |
| created_at | DATETIME | 否 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 否 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

### 5.3 枚举值
- `status`: `1=Active`, `0=Disabled`
- `action`: 无DB层枚举约束，约定示例 `view/create/edit/delete/export`

## 6. user_role（用户-角色关联）

### 6.1 表说明
- 主键：`id`
- 唯一键：`(user_id, role_id)`
- 外键：`user_id -> user.id`，`role_id -> role.id`，`granted_by -> user.id`

### 6.2 字段字典

| 字段 | 类型 | 可空 | 默认值 | 含义 |
|---|---|---|---|---|
| id | BIGINT UNSIGNED | 否 | 自增 | 主键 |
| user_id | BIGINT UNSIGNED | 否 | - | 用户ID |
| role_id | BIGINT UNSIGNED | 否 | - | 角色ID |
| expires_at | DATETIME | 是 | NULL | 过期时间（NULL=永久） |
| granted_by | BIGINT UNSIGNED | 是 | NULL | 授权人 |
| created_at | DATETIME | 否 | CURRENT_TIMESTAMP | 创建时间 |

## 7. role_permission（角色-权限关联）

### 7.1 表说明
- 主键：`id`
- 唯一键：`(role_id, permission_id)`
- 外键：`role_id -> role.id`，`permission_id -> permission.id`，`granted_by -> user.id`

### 7.2 字段字典

| 字段 | 类型 | 可空 | 默认值 | 含义 |
|---|---|---|---|---|
| id | BIGINT UNSIGNED | 否 | 自增 | 主键 |
| role_id | BIGINT UNSIGNED | 否 | - | 角色ID |
| permission_id | BIGINT UNSIGNED | 否 | - | 权限ID |
| granted_by | BIGINT UNSIGNED | 是 | NULL | 授权人 |
| created_at | DATETIME | 否 | CURRENT_TIMESTAMP | 创建时间 |

## 8. 种子数据摘要（RBAC）
- 预置角色：`ROLE_USER`、`ROLE_TASK_REVIEWER`、`ROLE_ADMIN`
- 预置用户：`admin`、`reviewer`、`normal`
- 预置绑定：
- `admin -> ROLE_ADMIN`
- `reviewer -> ROLE_TASK_REVIEWER`
- `normal -> ROLE_USER`

## 9. 实体映射对照（Java）
- 表 `user` -> 实体 `User`
- 表 `role` -> 实体 `Role`
- 表 `resource` -> 实体 `Resource`
- 表 `permission` -> 实体 `Permission`
- 表 `user_role` -> 实体 `UserRole`
- 表 `role_permission` -> 实体 `RolePermission`

## 10. 依据
- `docs/mysql/gpu_scheduler_db/gpu_scheduler_db.sql`
- `docs/mysql/gpu_scheduler_db/seed_data.sql`
- `src/main/java/com/sakurain/gpuscheduler/entity/User.java`
- `src/main/java/com/sakurain/gpuscheduler/entity/Role.java`
- `src/main/java/com/sakurain/gpuscheduler/entity/Resource.java`
- `src/main/java/com/sakurain/gpuscheduler/entity/Permission.java`
- `src/main/java/com/sakurain/gpuscheduler/entity/UserRole.java`
- `src/main/java/com/sakurain/gpuscheduler/entity/RolePermission.java`
