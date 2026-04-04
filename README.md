# GPU 异构算力调度系统（gpu-scheduler）

一个基于 Spring Boot 3 的后端系统，用于管理异构 GPU 资源并调度用户计算任务，包含任务排队、优先级老化、自动抢占、失败重试、RBAC 权限控制、限流幂等、监控与实时推送能力。

## 1. 代码库在做什么

这个项目把“任务提交到执行完成”的流程拆成了几条清晰链路：

1. 用户通过 API 提交任务（`/api/tasks/submit`）。
2. 任务进入状态机流转（`PENDING/PENDING_APPROVAL -> QUEUED -> RUNNING -> COMPLETED/FAILED/...`）。
3. 调度器从 Redis 优先队列取任务，按 Best-Fit 选择合适 GPU。
4. 执行模拟器异步执行任务，完成监控器回收结果并更新状态。
5. 失败任务进入重试队列，超过上限进入死信队列（DLQ）。
6. 任务状态通过 WebSocket（可选 Webhook）推送给客户端。

同时系统带有完整的认证鉴权（JWT + RBAC），并提供运维接口（暂停调度、重置熔断、DLQ 重处理、强制重排队/抢占等）。

## 2. 核心能力

- 任务优先级调度：Redis ZSet 队列，支持同优先级 FIFO。
- 老化机制：等待越久，有效优先级越高，降低低优任务饥饿。
- GPU 分配：`GpuAllocator` 使用 Best-Fit，按显存浪费率最小化分配。
- 自动抢占：高优任务可按策略抢占低优运行任务。
- 失败重试与 DLQ：指数退避重试，超阈值进入死信队列。
- Worker 心跳恢复：检测失联 worker，将任务回队并标记 GPU OFFLINE。
- 认证与权限：JWT 无状态认证 + RBAC（用户/角色/权限/资源模型）。
- 安全防护：Bucket4j + Redis 限流、幂等过滤器、Token 黑名单。
- 可观测性：`/api/health`、`/api/metrics`、Actuator、Prometheus。
- 实时推送：STOMP WebSocket（`/ws`）推送遥测与任务状态。

## 3. 技术栈

- Java 17
- Spring Boot 3.5.11
- Spring Security
- Spring Web / Validation / WebSocket(STOMP)
- MyBatis-Plus 3.5.12
- MySQL 8
- Redis + Lettuce
- Bucket4j 8.10.1
- JWT（jjwt 0.12.6）
- Druid 1.2.28
- springdoc-openapi + Swagger UI
- Actuator + Micrometer Prometheus

## 4. 项目结构（主干）

```text
src/main/java/com/sakurain/gpuscheduler
├─ controller    # 对外 API（认证、GPU、任务、监控、运维、RBAC）
├─ service       # 业务服务层（任务、通知、心跳、鉴权等）
├─ scheduler     # 调度核心（队列、分配、状态机、执行、老化、派发）
├─ security      # JWT/限流/幂等/Spring Security 过滤链
├─ mapper        # MyBatis-Plus Mapper
├─ entity        # 数据库实体
├─ dto           # 请求/响应模型
├─ config        # 配置绑定（调度、重试、抢占、JWT、WebSocket 等）
└─ util          # 工具类（JWT、Redis 分布式锁等）

src/main/resources
├─ application.yaml
└─ mapper/*.xml

docs/mysql
├─ gpu_scheduler_db/gpu_scheduler_db.sql      # 数据库 基础表结构
└─ gpu_scheduler_db/seed_data.sql             # 初始化种子数据
```

## 5. 快速开始

### 5.1 前置条件

- JDK 17+
- Maven 3.9+
- MySQL 8+
- Redis 6+

### 5.2 初始化数据库

按顺序执行：

1. `docs/mysql/gpu_scheduler_db/gpu_scheduler_db.sql`
2. `docs/mysql/gpu_scheduler_db/seed_data.sql`

### 5.3 配置环境变量

项目默认从 `application.yaml` + `env.env` 读取配置，关键变量：

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_PASSWORD`
- `REDIS_DB`
- `JWT_SECRET_KEY`
- `JWT_ACCESS_TOKEN_EXPIRATION`
- `JWT_REFRESH_TOKEN_EXPIRATION`
- `JWT_ISSUER`

示例（PowerShell）：

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/gpu_scheduler_db?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='your_password'
$env:REDIS_HOST='127.0.0.1'
$env:REDIS_PORT='6379'
$env:REDIS_PASSWORD=''
$env:REDIS_DB='0'
$env:JWT_SECRET_KEY='replace-with-your-32bytes-plus-secret'
```

### 5.4 启动项目

```powershell
mvn spring-boot:run
```

或：

```powershell
mvn -DskipTests package
java -jar target/gpu-scheduler-0.0.1-SNAPSHOT.jar
```

## 6. 常用入口

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/actuator/health`
- Prometheus: `http://localhost:8080/actuator/prometheus`
- WebSocket STOMP endpoint: `ws://localhost:8080/ws`

## 7. 主要 API（按模块）

- 认证：`/api/auth/*`（登录、刷新、登出、当前用户）
- GPU 管理：`/api/gpus/*`
- 任务管理：`/api/tasks/*`（提交、查询、取消、审批）
- 运维控制：`/api/ops/*`（调度暂停/恢复、DLQ、强制操作）
- 监控：`/api/health`、`/api/metrics`
- RBAC：`/api/users/*`、`/api/roles/*`、`/api/rbac/*`

## 8. 调度与状态流转说明

- Redis 优先队列评分公式：
  - `score = -effectivePriority * PRIORITY_SCALE + sequence`
- 有效优先级：
  - `effectivePriority = basePriority + waitMinutes * ageWeightPerMinute`
- 合法状态迁移由 `TaskStateMachine` 统一校验。
- `TaskDispatcher` 使用分布式锁避免多实例重复调度。

## 9. 测试

运行全部测试：

```powershell
mvn test
```

运行关键集成测试（需要可用 Redis）：

```powershell
mvn "-Dtest=TaskSubmissionToCompletionIT,ConcurrentSchedulingIT,StateMachineRedisIT" test
```

## 10. 配置建议

- 生产环境必须替换 `JWT_SECRET_KEY`，且不要在仓库提交真实密码。
- `scheduler.scheduled-jobs-enabled=false` 可临时关闭调度类定时任务。
- 根据业务规模调优：
  - `task-retry.*`
  - `task-preemption.*`
  - `worker-heartbeat.*`
  - `rate-limit.*`

## 11. 当前实现边界

当前是“任务执行模拟”架构（`TaskExecutionSimulator`），适合验证调度算法、状态机和管控流程；若接入真实 GPU 集群，可在 `TaskAssignmentService` / 执行器层替换为实际 worker 通讯与执行协议。

## 12. 后续迭代说明

- 通用行级数据权限框架（U-03）当前不纳入交付范围，以控制系统复杂度与维护成本。
- 后续如需落地，可按“注解 + SQL拦截器 + 角色数据域规则表”的方案分阶段接入。
