# RBAC 权限覆盖矩阵（角色兼容阶段）

## 目标
- 在保留 `ROLE_*` 兼容的前提下，引入权限码鉴权入口。
- 为后续从“角色为主”平滑迁移到“权限为主”提供对照表。

## 现状说明
- 当前后端已在 `CustomUserDetailsService` 中加载用户权限码，并注入 `GrantedAuthority`。
- 控制器鉴权已升级为“角色或权限码”双通道。
- 由于 SQL 种子数据尚未内置 `resource/permission` 初始化数据，权限码需由运维或初始化脚本下发后才能独立生效。

## 覆盖矩阵
| 模块 | 路径前缀 | 兼容角色 | 新增权限码 |
| -- | -- | -- | -- |
| GPU 管理 | `/api/gpu`, `/api/gpus` | `ROLE_ADMIN` | `gpu:read`, `gpu:write`, `gpu:heartbeat` |
| 任务审批 | `/api/task`, `/api/tasks` | `ROLE_ADMIN`, `ROLE_TASK_REVIEWER` | `task:approval:read`, `task:approval:review` |
| 用户管理 | `/api/users` | `ROLE_ADMIN` | `user:manage` |
| 角色管理 | `/api/roles` | `ROLE_ADMIN` | `role:manage` |
| RBAC 字典 | `/api/rbac` | `ROLE_ADMIN` | `rbac:read` |
| 运维控制 | `/api/ops` | `ROLE_ADMIN` | `ops:manage` |
| 监控接口 | `/api/health`, `/api/metrics` | `ROLE_ADMIN` | `monitoring:read` |

## 待落地
1. 在数据库中补充 `resource/permission/role_permission` 初始化脚本。  
2. 前端菜单/按钮按权限码渲染，并与后端接口鉴权保持一致。  
3. 完成灰度后逐步减少对 `ROLE_ADMIN` 的硬依赖。  
