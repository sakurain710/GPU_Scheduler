# RBAC 权限覆盖矩阵（角色兼容阶段）

## 目标
- 在保留 `ROLE_*` 兼容的前提下，引入权限码鉴权入口。
- 为后续从“以角色为主”平滑迁移到“以权限为主”提供对照表。

## 现状说明
- 后端在 `CustomUserDetailsService` 中加载用户权限码并注入 `GrantedAuthority`。
- 控制器鉴权采用“角色或权限码”双通道。
- 种子数据已补齐基础 `resource/permission/role_permission` 映射。

## 覆盖矩阵
| 模块 | 路径前缀 | 兼容角色 | 权限码 |
| -- | -- | -- | -- |
| GPU 管理 | `/api/gpus` | `ROLE_ADMIN` | `gpu:read`, `gpu:write`, `gpu:heartbeat` |
| 任务审批 | `/api/tasks` | `ROLE_ADMIN`, `ROLE_TASK_REVIEWER` | `task:approval:read`, `task:approval:review` |
| 批量审批 | `/api/tasks/approval/batch/*` | `ROLE_ADMIN`, `ROLE_TASK_REVIEWER` | `task:approval:review` |
| 用户管理 | `/api/users` | `ROLE_ADMIN` | `user:manage` |
| 角色管理 | `/api/roles` | `ROLE_ADMIN` | `role:manage` |
| RBAC 字典 | `/api/rbac` | `ROLE_ADMIN` | `rbac:read` |
| 运维控制 | `/api/ops` | `ROLE_ADMIN` | `ops:manage` |
| 监控接口 | `/api/health`, `/api/metrics` | `ROLE_ADMIN` | `monitoring:read` |

## 后续事项
1. 持续补齐资源与权限字典的业务覆盖面。
2. 前端菜单/按钮按权限码渲染，并与接口鉴权口径一致。
3. 在灰度完成后逐步减少对 `ROLE_ADMIN` 的硬依赖。
