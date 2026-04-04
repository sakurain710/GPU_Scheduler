# RBAC 权限覆盖矩阵（按接口分层）

## 目标
- 用接口粒度对齐“代码真实鉴权行为”与“文档口径”。
- 保留 `ROLE_*` 兼容策略，同时明确权限码门禁。

## 全局基线
- `SecurityConfig` 路由规则：
  - `/api/auth/**`、`/api/public/**`、`/actuator/health`、`/v3/api-docs/**`、`/swagger-ui/**`、`/ws/**` 放行。
  - 其余接口统一 `authenticated()`。
- 方法级规则以控制器 `@PreAuthorize` 为准；未标注 `@PreAuthorize` 的接口仅受全局 `authenticated()` 约束。

## 接口分层矩阵

### A. 仅需已认证（`authenticated()`）
| 控制器 | 接口 | 角色兼容 | 权限码 | 说明 |
| -- | -- | -- | -- | -- |
| `GpuController` | `GET /api/gpus` | 无强制角色 | 无强制权限 | 仅登录态可访问 |
| `GpuController` | `GET /api/gpus/{gpuId}` | 无强制角色 | 无强制权限 | 仅登录态可访问 |
| `GpuTaskController` | `POST /api/tasks/submit` | 无强制角色 | 无强制权限 | 高优任务是否审批由业务规则决定 |
| `GpuTaskController` | `GET /api/tasks/{taskId}` | 任务所有者或审批角色 | 任务所有者或审批权限 | 服务层对象级校验 |
| `GpuTaskController` | `GET /api/tasks/my` | 无强制角色 | 无强制权限 | 仅返回当前用户任务 |
| `GpuTaskController` | `POST /api/tasks/{taskId}/cancel` | 任务所有者或审批角色 | 任务所有者或审批权限 | 服务层对象级校验 |
| `RbacDictionaryController` | `GET /api/rbac/me/menu-tree` | 无强制角色 | 无强制权限 | 仅登录态，返回当前用户可见菜单 |
| `RbacDictionaryController` | `GET /api/rbac/me/button-permissions` | 无强制角色 | 无强制权限 | 仅登录态，返回当前用户按钮权限码 |

### B. 角色或权限码门禁（`@PreAuthorize`）
| 控制器 | 接口 | 角色兼容 | 权限码 | 规则表达式 |
| -- | -- | -- | -- | -- |
| `GpuController` | `POST /api/gpus` | `ROLE_ADMIN` | `gpu:write` | `hasAnyAuthority('ROLE_ADMIN','gpu:write')` |
| `GpuController` | `PUT /api/gpus/{gpuId}/status` | `ROLE_ADMIN` | `gpu:write` | 同上 |
| `GpuController` | `DELETE /api/gpus/{gpuId}` | `ROLE_ADMIN` | `gpu:write` | 同上 |
| `GpuController` | `GET /api/gpus/health` | `ROLE_ADMIN` | `gpu:read` | `hasAnyAuthority('ROLE_ADMIN','gpu:read')` |
| `GpuController` | `GET /api/gpus/metrics` | `ROLE_ADMIN` | `gpu:read` | 同上 |
| `GpuController` | `POST /api/gpus/{gpuId}/heartbeat` | `ROLE_ADMIN` | `gpu:heartbeat` | `hasAnyAuthority('ROLE_ADMIN','gpu:heartbeat')` |
| `GpuTaskController` | `GET /api/tasks/approval/pending` | `ROLE_ADMIN`,`ROLE_TASK_REVIEWER` | `task:approval:read` | `hasAnyAuthority('ROLE_ADMIN','ROLE_TASK_REVIEWER','task:approval:read')` |
| `GpuTaskController` | `POST /api/tasks/{taskId}/approve` | `ROLE_ADMIN`,`ROLE_TASK_REVIEWER` | `task:approval:review` | `hasAnyAuthority('ROLE_ADMIN','ROLE_TASK_REVIEWER','task:approval:review')` |
| `GpuTaskController` | `POST /api/tasks/{taskId}/reject` | `ROLE_ADMIN`,`ROLE_TASK_REVIEWER` | `task:approval:review` | 同上 |
| `GpuTaskController` | `POST /api/tasks/approval/batch/approve` | `ROLE_ADMIN`,`ROLE_TASK_REVIEWER` | `task:approval:review` | 同上 |
| `GpuTaskController` | `POST /api/tasks/approval/batch/reject` | `ROLE_ADMIN`,`ROLE_TASK_REVIEWER` | `task:approval:review` | 同上 |
| `UserController` | `/api/users/**` | `ROLE_ADMIN` | `user:manage` | 类级 `@PreAuthorize` |
| `RoleController` | `/api/roles/**` | `ROLE_ADMIN` | `role:manage` | 类级 `@PreAuthorize` |
| `RbacDictionaryController` | `GET /api/rbac/resources` | `ROLE_ADMIN` | `rbac:read` | `hasAnyAuthority('ROLE_ADMIN','rbac:read')` |
| `RbacDictionaryController` | `GET /api/rbac/permissions` | `ROLE_ADMIN` | `rbac:read` | 同上 |
| `OpsController` | `/api/ops/**` | `ROLE_ADMIN` | `ops:manage` | 类级 `@PreAuthorize` |
| `MonitoringController` | `GET /api/health` | `ROLE_ADMIN` | `monitoring:read` | `hasAnyAuthority('ROLE_ADMIN','monitoring:read')` |
| `MonitoringController` | `GET /api/metrics` | `ROLE_ADMIN` | `monitoring:read` | 同上 |

## 数据初始化依赖
- 权限码生效依赖 `resource` / `permission` / `role_permission` 初始化。
- 初始化脚本：`docs/mysql/gpu_scheduler_db/seed_data.sql`。

## 说明
- 本矩阵是“后端当前代码行为”的文档投影；新增接口时应同步更新本文件。
