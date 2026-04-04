# 第四阶段文档审查结论与后续改进清单

## 1. 审查结论（front_back_doc）
已按 `gowork.md` 第四阶段清单完成全部前端对接文档产出，目录内已包含以下 10 类文档：

1. 项目概述
2. 接口文档（已拆分 8 部分）
3. 数据字典（已拆分 2 部分）
4. 业务流程文档（已拆分 5 部分）
5. 页面功能结构与交互说明（已拆分 5 部分）
6. 权限设计说明
7. 统一响应与错误码规范
8. 联调说明文档
9. 前后端字段映射说明（已拆分 4 部分）
10. ECharts 图表接口说明

本文件仅汇总“未实现”与“待确认”状态事项，供后续迭代。

## 2. 已完成事项（本轮）

### D-01 菜单树按用户裁剪接口
- 状态：`✅ 已实现`
- 接口：`GET /api/rbac/me/menu-tree`
- 说明：后端按“用户 -> 角色 -> 权限 -> 资源”链路聚合资源，并按菜单父子结构返回可见菜单树。

### D-02 按钮级权限清单按用户聚合接口
- 状态：`✅ 已实现`
- 接口：`GET /api/rbac/me/button-permissions`
- 说明：后端返回当前用户可用的按钮权限码清单（去重、排序）。


### D-03 全局 Jackson 时间格式与时区
- 状态：`✅ 已实现`
- 配置：`spring.jackson.date-format=yyyy-MM-dd HH:mm:ss`、`spring.jackson.time-zone=Asia/Shanghai`
- 说明：统一所有接口时间序列化口径，避免前端时间解析与展示偏差。

### D-04 roleType 枚举语义固定
- 状态：`✅ 已实现`
- 标准：`1=System, 2=Custom, 3=Temporary`（与数据库 `role.role_type` 设计一致）
- 说明：已在 DTO/服务校验与文档中同步固定。

### D-05 任务日志事件命名规范
- 状态：`✅ 已实现`
- 标准：`UPPER_SNAKE_CASE`，白名单 `QUEUED`、`DISPATCHED`、`COMPLETED`、`FAILED`、`CANCELLED`、`PENDING_APPROVAL`、`REJECTED`
- 说明：已在数据库 CHECK 约束、代码枚举与文档中统一。

### D-06 全局拦截器策略与路由命名规范
- 状态：`✅ 已确定并落文档`
- 说明：前端采用“401 单飞刷新 + 请求重放（单次）”策略；路由采用后端资源语义 `kebab-case`，推荐 API 路径统一复数资源风格（`/api/tasks`、`/api/gpus`）。

### D-07 任务审批批量操作能力
- 状态：`✅ 已实现`
- 接口：`POST /api/tasks/approval/batch/approve`、`POST /api/tasks/approval/batch/reject`
- 说明：审批中心已支持多任务批量通过/拒绝，复用审批权限 `task:approval:review`。

### D-08 RBAC/运维只读角色策略
- 状态：`✅ 已确认`
- 说明：本阶段不引入 RBAC 只读角色与运维只读角色，继续使用现有管理权限模型。

### D-09 数据资源类型口径
- 状态：`✅ 已确认并补充`
- 说明：数据库模型固定 `resource.type` 注释：`1=Menu 2=API 3=Button 4=Data`；`type=4` 作为数据资源预留，当前未启用执行链路。

### D-10 授权变更缓存刷新策略
- 状态：`✅ 已确认并落文档`
- 说明：前端在登录成功、refresh 成功、角色/授权变更提交成功后，主动刷新菜单树与按钮权限缓存。

### D-11 统一错误码治理
- 状态：`✅ 已实现`
- 说明：`GlobalExceptionHandler` 已统一补齐 `AuthenticationException/AccessDeniedException/MethodArgumentNotValidException/Exception` 的 `errorCode`，并固定前缀规范 `AUTH_*`/`TASK_*`/`GPU_*`/`ROLE_*`/`COMMON_*`。

### D-12 联调环境与路径规范
- 状态：`✅ 已确认并落地`
- 说明：不拆分 dev/test 两套 Base URL；后端控制器与文档统一使用推荐路径 `/api/tasks`、`/api/gpus`。

### D-13 监控订阅与容错策略
- 状态：`✅ 已确认并落文档`
- 说明：固定为“REST首屏 + WebSocket增量 + 30s REST校准”，重连采用指数退避并设置最大重试；前端仅内存保留最近 200 条快照。

### D-14 Redis 不可用时鉴权策略
- 状态：`✅ 已实现并可配置`
- 说明：新增 `security.token-blacklist.fail-open` 开关，默认 `true`（fail-open），可切换 `false`（fail-close，返回 `503 + AUTH_BLACKLIST_BACKEND_UNAVAILABLE`）。

### D-15 执行器与心跳边界
- 状态：`✅ 已确认`
- 说明：当前阶段不接入真实执行器；保留后续可切换冗余。GPU 心跳仅保留业务完整性，不新增批量心跳与额外维护能力。

## 3. 未实现事项（❌）

### U-03 通用行级数据权限框架未实现
- 来源：`front_back_doc/06_权限设计说明.md:95`
- 现状：部门/租户/项目域等通用行级数据权限链路尚未实现。
- 影响：当前仅接口/资源级控制，复杂数据隔离场景需依赖业务代码硬编码。
- 建议改进：设计统一数据域策略（注解+拦截器/SQL 片段注入），并在查询接口落地。

## 4. 待确认事项（⚠️）

## 5. 统计
- 已完成（本轮）：15 项
- 未实现（❌）：1 项
- 待确认（⚠️）：0 项
- 合计：16 项（含已完成回溯项）

