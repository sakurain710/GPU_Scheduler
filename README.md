## GPU Scheduler 代码库概述

这是一个 **GPU 资源调度与任务管理系统**，作为学术论文项目开发，使用 **Java 17 + Spring Boot 3.5.11** 构建。

### 核心功能

该系统管理共享 GPU 硬件池（如 NVIDIA A100、AMD、华为昇腾），并在多租户环境下调度计算工作负载（模型训练、推理、渲染等）。

### 技术栈

| 层次      | 技术                                                    |
| --------- | ------------------------------------------------------- |
| 框架      | Spring Boot 3.5.11 (Web, Security, WebSocket, Actuator) |
| ORM       | MyBatis Plus 3.5.12                                     |
| 数据库    | MySQL (主库)                                            |
| 缓存/队列 | Redis (ZSet 优先级队列、分布式锁、令牌黑名单、限流)     |
| 认证      | Spring Security + JWT (无状态)                          |
| 实时推送  | WebSocket / STOMP                                       |
| 连接池    | Druid                                                   |
| 限流      | Bucket4j + Redis                                        |
| 监控      | Micrometer + Prometheus                                 |

### 系统架构

采用经典的 **Spring Boot MVC 分层架构**：

```
controller/ (9个)  →  service/ (15个)  →  mapper/ (12个)
                              ↑
                     scheduler/ (8个核心调度组件)
```

### 核心调度引擎 (`scheduler/`)

这是系统的核心，由 8 个组件协同工作：

1. **TaskStateMachine** — 任务状态机，定义 8 种状态的合法转换（PENDING → QUEUED → RUNNING → COMPLETED/FAILED，含审批和拒绝分支）
2. **TaskPriorityQueue** — 基于 Redis ZSet 的优先级队列，带老化机制（等待越久优先级越高，防止饥饿）
3. **GpuAllocator** — Best-Fit GPU 分配算法，选择满足任务最小内存需求的最小 GPU，最小化碎片
4. **TaskAssignmentService** — 事务性 GPU-任务绑定
5. **TaskDispatcher** — 定时调度循环（每 5s），从队列取任务、分配 GPU、提交执行
6. **TaskExecutionSimulator** — 线程池模拟 GPU 任务执行，集成熔断器
7. **TaskCompletionMonitor** — 定时监控（每 10s），检测任务完成/失败/超时，释放 GPU，触发重试/死信队列
8. **TaskAgingScheduler** — 定期刷新队列中的优先级分数，实现老化

### 数据库设计

12 张表，分三组：

- **用户与 RBAC**：`user`, `resource`, `permission`, `role`, `role_permission`, `user_role`（层级角色，细粒度权限）
- **GPU 调度域**：`gpu`, `gpu_task`, `gpu_task_log`, `metric_snapshot`
- **运维与监控**：`system_config`, `notification`, `task_dlq`, `ops_event_log`

### API 接口

REST API 挂载在 `/api` 下，主要端点：`/auth`（认证）、`/users`（用户管理）、`/roles`（角色管理）、`/gpus`（GPU 管理）、`/tasks`（任务提交/审批/取消）、`/ops`（运维操作）、`/admin/dashboard`（管理面板）、`/public`（公开概览）。WebSocket 通过 `/ws` 的 STOMP 协议提供实时仪表盘和任务状态推送。

### 任务生命周期

```
提交 → (可选审批) → 入队 Redis ZSet → Dispatcher 取任务 → Best-Fit 分配 GPU
→ 绑定 GPU → 线程池模拟执行 → 完成监控检测 → 释放 GPU → 成功/失败
                                                    ↓ 失败
                                              重试 → 死信队列(DLQ)
```

高优先级任务可以抢占低优先级运行中任务。系统具备熔断器、分布式锁、孤儿任务恢复等韧性机制。