# 第三阶段：对接文档制定计划

## 阶段目标
- 形成可执行的对接文档编写计划，覆盖前后端联调所需全部文档。
- 明确每份文档的代码来源、编写顺序、拆分策略、风险项与待确认项。
- 本阶段仅产出计划文件，不进入文档正文编写。

## 文档清单与执行计划

| 序号 | 文档 | 目标 | 主要来源代码/配置 | 编写顺序 | 是否拆分执行 |
|---|---|---|---|---|---|
| 1 | 项目概述 | 统一系统边界、模块职责、核心流程视图 | `README.md`、`src/main/resources/application.yaml`、`controller/*`、`service/*` | 1 | 否 |
| 2 | 接口文档 | 输出接口级请求/响应/状态码与示例 | `controller/AuthController.java`、`controller/UserController.java`、`controller/RoleController.java`、`controller/RbacDictionaryController.java`、`controller/GpuController.java`、`controller/GpuTaskController.java`、`controller/MonitoringController.java`、`controller/OpsController.java` | 2 | 是（按控制器拆分） |
| 3 | 数据字典 | 统一实体字段、枚举语义、状态定义、时间格式 | `entity/*`、`dto/*`、`docs/mysql/gpu_scheduler_db/gpu_scheduler_db.sql`、`docs/mysql/gpu_scheduler_db/seed_data.sql` | 3 | 是（按业务域拆分） |
| 4 | 业务流程文档 | 梳理端到端流程、状态流转、关键分支 | `service/GpuTaskService.java`、`service/AuthService.java`、`service/MonitoringService.java`、`scheduler/*` | 4 | 是（提交/调度/审批/运维拆分） |
| 5 | 页面功能结构与交互说明（重点） | 面向前端输出页面级功能结构、操作行为、接口依赖 | `controller/*`、`dto/*`、`service/*`、`docs/rbac_permission_matrix.md` | 5 | 是（按页面模块拆分） |
| 6 | 权限设计说明 | 说明认证、鉴权、角色/权限映射、接口保护策略 | `security/SecurityConfig.java`、`security/JwtAuthenticationFilter.java`、`security/CustomUserDetailsService.java`、`controller/*`（`@PreAuthorize`） | 6 | 否 |
| 7 | 统一响应与错误码规范 | 固化返回结构、错误码、前端处理约定 | `dto/Result.java`、`exception/GlobalExceptionHandler.java`、`security/JwtAuthenticationEntryPoint.java`、`security/RateLimitFilter.java`、`security/IdempotencyFilter.java` | 7 | 否 |
| 8 | 联调说明文档 | 统一联调入口、环境变量、账号角色、联调步骤 | `src/main/resources/application.yaml`、`env.env`、`README.md`、`controller/AuthController.java` | 8 | 否 |
| 9 | 前后端字段映射说明 | 建立前端模型与后端字段的一一映射 | `dto/user/*`、`dto/role/*`、`dto/gpu/*`、`dto/task/*`、`dto/monitor/*` | 9 | 是（按模块拆分） |
| 10 | ECharts 图表接口说明 | 输出图表数据结构、维度、单位、转换规则 | `controller/MonitoringController.java`、`dto/monitor/*`、`service/MonitoringService.java` | 10 | 否 |

## 编写阶段拆分策略
- 第 1 批：基础约束类文档（1、7、8），先统一术语与协议。
- 第 2 批：接口与数据类文档（2、3、9），保证字段和示例可直接联调。
- 第 3 批：流程与权限类文档（4、6），补齐流程和访问控制。
- 第 4 批：前端落地类文档（5、10），收口页面交互和图表接入。

## 风险项（代办）
- `[TODO-R1]` 接口路径存在历史兼容别名（如单复数并存）时，文档需标注“推荐路径”和“兼容路径”。
- `[TODO-R2]` 权限已支持角色+权限码并行鉴权，若前端仅按角色判断会产生越权/漏权风险，需在权限文档中明确最小权限粒度。
- `[TODO-R3]` 限流、幂等、鉴权失败均已统一返回 `Result`，但前端错误处理若未按 `errorCode` 分流会导致提示不准确。
- `[TODO-R4]` 监控图表接口的时间粒度与单位需要与前端图表配置对齐，避免二次换算偏差。

## 待确认项
- `[待确认-C1]` 前端路由命名规范（是否与后端资源命名保持一致）。
- `[待确认-C2]` 联调环境 Base URL 与跨域策略（开发/测试环境是否分离）。
- `[待确认-C3]` 测试账号是否需要按角色预置（管理员、运维、普通用户、审批角色）。
- `[待确认-C4]` 监控大盘刷新频率与数据延迟容忍阈值。

## 本阶段输出
- 文件：`to_front_plan.md`
- 状态：已完成第三阶段计划制定，未进入第四阶段文档生成。
