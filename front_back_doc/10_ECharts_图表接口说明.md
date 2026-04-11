# ECharts 图表接口说明

## 1. 文档目标
- 统一前端大盘图表与后端监控接口的数据契约。
- 明确维度、单位、字段转换规则与容错策略，避免二次理解偏差。

## 2. 接口总览

| 用途 | 方法 | 路径 | 权限 | 推荐用途 |
|---|---|---|---|---|
| 合并指标 | `GET` | `/api/metrics` | `ROLE_ADMIN` 或 `monitoring:read` | 主数据源（任务+GPU图表） |
| 全局任务流 | `GET` | `/api/metrics/tasks/stream` | `ROLE_ADMIN` 或 `monitoring:read` | 实时任务表格（支持 `activeOnly=true`） |
| 系统健康 | `GET` | `/api/health` | `ROLE_ADMIN` 或 `monitoring:read` | 健康状态卡片/告警灯 |
| 实时快照 | `WS/STOMP` | 原生 `ws://localhost:8080/ws` 或 SockJS 基址 `http://localhost:8080/ws`，订阅 `/topic/telemetry` | 登录态 | 实时刷新（3秒推送） |

说明：
- `/topic/telemetry` 的推送周期由 `telemetry.push-interval-ms` 控制，默认 `3000ms`。
- 前端建议“启动时拉一次 REST + 持续订阅 WS”，WS 断开时回退定时拉取。

## 3. 统一响应壳
REST 接口统一返回：

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | number | 业务状态码，成功 `200` |
| `errorCode` | string/null | 失败分流码 |
| `message` | string | 提示文案 |
| `data` | object | 图表数据载荷 |

前端处理顺序：
1. 判定 `code === 200`
2. 失败时按 `errorCode` 分流
3. 成功后解析 `data`

## 4. `/api/metrics` 字段定义与图表映射

### 4.1 顶层结构

| 字段 | 类型 | 说明 |
|---|---|---|
| `data.tasks` | object | 任务指标（`TaskMetrics`） |
| `data.gpus` | object | GPU指标（`GpuMetrics`） |

---

### 4.2 任务指标（`data.tasks`）

| 字段 | 类型 | 单位 | ECharts推荐图 | 说明 |
|---|---|---|---|---|
| `queueLength` | number | 个 | `gauge` / `bar` | 当前队列长度 |
| `queueLengthByPriority` | map | 个 | `bar` | 优先级分桶队列量 |
| `taskCountByStatus` | map | 个 | `pie` / `bar` | 各状态任务计数 |
| `avgWaitSecondsByPriority` | map | 秒 | `bar` | 各优先级平均等待时长 |
| `avgDispatchLatencySeconds` | number | 秒 | `stat card` | 入队到运行平均时延 |
| `avgTurnaroundSeconds` | number | 秒 | `stat card` | 入队到完成平均时长 |
| `dispatchLatencyPercentilesSeconds` | map | 秒 | `bar` | `p50/p95/p99` |
| `queueAgeHistogram` | map | 个 | `bar` | 队龄分桶分布 |
| `completionRate` | string | % | `gauge` | 完成率字符串，如 `92.3%` |
| `failureRate` | string | % | `gauge` | 失败率字符串，如 `3.1%` |
| `failureReasons` | map | 个 | `bar` | 失败原因TopN |
| `allocationFailureReasons` | map | 个 | `bar` | 分配失败归因 |
| `userSlaCompliancePct` | map | % | `bar` | 用户维度SLA达标率 |
| `retryQueueSize` | number | 个 | `stat card` | 重试队列大小 |
| `dlqSize` | number | 个 | `stat card` | 死信队列大小 |
| `pendingApprovalCount` | number | 个 | `stat card` | 待审批任务数 |
| `webhookRetryQueueSize` | number | 个 | `stat card` | webhook重试队列 |

#### 任务维度键规范

`queueLengthByPriority` 可能键：
- `High(8-10)`
- `Medium(5-7)`
- `Low(1-4)`
- `Unknown`

`queueAgeHistogram` 固定键：
- `lt_1m`
- `m1_5`
- `m5_15`
- `gte_15m`

`dispatchLatencyPercentilesSeconds` 固定键：
- `p50`
- `p95`
- `p99`

---

### 4.3 GPU指标（`data.gpus`）

| 字段 | 类型 | 单位 | ECharts推荐图 | 说明 |
|---|---|---|---|---|
| `total` | number | 张 | `stat card` | GPU总数 |
| `countByStatus` | map | 张 | `pie` / `bar` | GPU状态分布 |
| `utilizationRate` | string | % | `gauge` | 整体利用率字符串 |
| `usedMemoryGbByGpu` | map | GB | `bar` | 每卡已用显存 |
| `remainingMemoryGbByGpu` | map | GB | `bar` | 每卡剩余显存 |
| `vramFragmentationByGpu` | map | 比例(0-1) | `bar` | 显存碎片率 |
| `idleSecondsByGpu` | map | 秒 | `bar` | 空闲GPU空闲时长 |

#### GPU状态键规范（`countByStatus`）
- `Idle`
- `Busy`
- `Offline`
- `Maintenance`


### 4.4 全局任务流（表格）

接口：`GET /api/metrics/tasks/stream`

推荐查询参数：
- `page=1`
- `size=12`
- `activeOnly=true`
- `sortBy=updatedAt`
- `sortDir=desc`

说明：
- `status` 与 `activeOnly` 同时存在时，以 `status` 精确过滤为准。
- 当 `activeOnly=true` 且 `status` 为空时，仅返回 `Queued` 与 `Running` 任务。
- 适用于大屏右侧任务流转表格，不替代 `/api/metrics` 图表主数据源。

表格建议字段：
- `id`
- `minMemoryGb`
- `basePriority`
- `statusLabel`
- `gpuId`
- `updatedAt/createdAt`
## 5. `/api/health` 字段定义与展示建议

| 字段 | 类型 | 单位 | 展示建议 |
|---|---|---|---|
| `status` | string | - | 全局健康灯（绿/黄/红） |
| `dbStatus` | string | - | 子系统状态卡片 |
| `redisStatus` | string | - | 子系统状态卡片 |
| `schedulerStatus` | string | - | 调度器状态卡片 |
| `circuitBreakerState` | string | - | 熔断器状态标签 |
| `oldestQueuedTaskSeconds` | number | 秒 | 队列拥塞告警指标 |

状态值约定：
- `status`: `UP` / `DEGRADED` / `DOWN`
- `dbStatus`、`redisStatus`、`schedulerStatus`: `UP` / `DOWN`（调度器当前固定 `UP`）
- `circuitBreakerState`: `CLOSED` / `OPEN` / `HALF_OPEN`

## 6. WebSocket 实时快照（`/topic/telemetry`）
消息体 `TelemetrySnapshot`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `timestamp` | string(datetime) | 快照时间 |
| `health` | object | 与 `/api/health` `data` 同结构 |
| `tasks` | object | 与 `/api/metrics` `data.tasks` 同结构 |
| `gpus` | object | 与 `/api/metrics` `data.gpus` 同结构 |

前端建议：
- `timestamp` 作为图表“最后刷新时间”显示。
- WS新消息到达后，更新图表 series，不重建图表实例。

## 7. 字段转换规则（前端必须执行）

### 7.1 百分比字符串转数值
后端字段：`completionRate`、`failureRate`、`utilizationRate`（如 `87.5%`）

转换规则：
- 去掉 `%`
- `parseFloat`
- 结果用于 `gauge` 的 `value`

### 7.2 Map 转 ECharts `xAxis + series`
后端 map 统一按 `Object.entries(map)` 转换：
- `xAxis.data = keys`
- `series[0].data = values`

### 7.3 比例字段格式化
`vramFragmentationByGpu` 值域 `0.0-1.0`，前端展示时：
- 图表数值可乘 `100` 转百分比
- tooltip 显示 `xx.x%`

### 7.4 时间/时长
- 所有 *_Seconds 字段均为“秒”。
- 前端显示可换算为 `m/s`，但图表原始值建议保留秒，避免精度误差。

## 8. 推荐图表清单（可直接落地）

| 图表ID | 数据源字段 | 图表类型 | 维度 |
|---|---|---|---|
| `chart_task_status_dist` | `tasks.taskCountByStatus` | 饼图 | 状态->数量 |
| `chart_task_queue_priority` | `tasks.queueLengthByPriority` | 柱状图 | 优先级->队列数 |
| `chart_task_wait_priority` | `tasks.avgWaitSecondsByPriority` | 柱状图 | 优先级->平均等待秒 |
| `chart_task_latency_pct` | `tasks.dispatchLatencyPercentilesSeconds` | 柱状图 | p50/p95/p99->秒 |
| `chart_task_queue_age` | `tasks.queueAgeHistogram` | 柱状图 | 队龄桶->数量 |
| `chart_gpu_status_dist` | `gpus.countByStatus` | 饼图 | 状态->数量 |
| `chart_gpu_memory_used` | `gpus.usedMemoryGbByGpu` | 柱状图 | gpuId->GB |
| `chart_gpu_memory_remain` | `gpus.remainingMemoryGbByGpu` | 柱状图 | gpuId->GB |
| `chart_gpu_fragmentation` | `gpus.vramFragmentationByGpu` | 柱状图 | gpuId->ratio |
| `chart_gpu_idle_seconds` | `gpus.idleSecondsByGpu` | 柱状图 | gpuId->秒 |

## 9. 容错与联调约束
- 所有 map 字段均可能为空对象 `{}`，前端需显示“暂无数据”。
- `oldestQueuedTaskSeconds = -1` 代表当前无排队任务，不应按异常展示。
- 部分字段（如 `vramFragmentationByGpu`、`idleSecondsByGpu`）仅在满足条件时出现，前端按可选字段处理。
- 图表刷新建议节流到 `3s~5s`，与后端推送周期保持一致。

## 10. 联调最小验收清单
1. `/api/metrics` 成功返回后，至少 6 个核心图表可渲染（任务状态、优先级队列、延迟分位、GPU状态、显存使用、显存剩余）。
2. 百分比字符串字段完成数值化并用于仪表盘。
3. `/topic/telemetry` 断开重连后可恢复更新，不重复订阅。
4. map 空数据场景下页面无报错。

