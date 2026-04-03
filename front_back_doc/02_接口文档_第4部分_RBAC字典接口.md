# 接口文档（第4部分）：RBAC字典接口

## 1. 文档范围
- 控制器：`RbacDictionaryController`
- 路径前缀：`/api/rbac`
- 覆盖接口：权限字典、资源字典、当前用户菜单树、当前用户按钮权限码

## 2. 访问控制与通用约定

### 2.1 权限要求
- `GET /api/rbac/permissions`、`GET /api/rbac/resources`：`ROLE_ADMIN` 或 `rbac:read`
- `GET /api/rbac/me/menu-tree`、`GET /api/rbac/me/button-permissions`：已登录用户（`isAuthenticated()`）
- 未认证：`401`
- 无权限：`403`

### 2.2 统一响应结构（Result）

| 字段 | 类型 | 含义 | 可空 | 默认值 |
|---|---|---|---|---|
| code | Integer | 业务状态码 | 否 | 成功时 `200` |
| errorCode | String | 业务错误码 | 是 | `null` |
| message | String | 响应消息 | 否 | 成功时通常为`操作成功` |
| data | Object | 业务数据 | 是 | `null` |

### 2.3 常见错误码

| HTTP | errorCode | 场景 |
|---|---|---|
| 401 | `AUTH_UNAUTHORIZED` | 未登录访问受保护资源 |
| 403 | `null` | 权限不足 |

## 3. 接口明细

---

### 3.1 查询权限字典
- **方法**：`GET`
- **路径**：`/api/rbac/permissions`
- **鉴权**：`ROLE_ADMIN` 或 `rbac:read`

#### 查询参数

| 字段 | 类型 | 含义 | 可空 | 默认值 | 枚举/约束 |
|---|---|---|---|---|---|
| resourceId | Long | 按资源ID过滤 | 是 | `null` | 正整数 |
| status | Integer | 按状态过滤 | 是 | `1` | `0=禁用,1=启用` |

#### 成功响应 `data` 类型
- `List<Permission>`

#### Permission 字段说明

| 字段 | 类型 | 含义 | 可空 | 备注 |
|---|---|---|---|---|
| id | Long | 权限ID | 否 | 主键 |
| code | String | 权限编码 | 是 | 例如 `gpu:write` |
| name | String | 权限名称 | 是 | - |
| resourceId | Long | 关联资源ID | 是 | `resource.id` |
| action | String | 操作类型 | 是 | 例如 `view/create/edit/delete/export` |
| description | String | 描述 | 是 | - |
| status | Integer | 状态 | 是 | `1`启用, `0`禁用 |
| createdAt | LocalDateTime | 创建时间 | 是 | 序列化格式待确认 |
| updatedAt | LocalDateTime | 更新时间 | 是 | 序列化格式待确认 |

---

### 3.2 查询资源字典
- **方法**：`GET`
- **路径**：`/api/rbac/resources`
- **鉴权**：`ROLE_ADMIN` 或 `rbac:read`

#### 查询参数

| 字段 | 类型 | 含义 | 可空 | 默认值 | 枚举/约束 |
|---|---|---|---|---|---|
| parentId | Long | 按父资源ID过滤 | 是 | `null` | 正整数 |
| type | Integer | 按资源类型过滤 | 是 | `null` | `1=菜单,2=API,3=按钮,4=数据` |
| status | Integer | 按状态过滤 | 是 | `1` | `0=禁用,1=启用` |

#### 成功响应 `data` 类型
- `List<Resource>`

---

### 3.3 查询当前用户可见菜单树
- **方法**：`GET`
- **路径**：`/api/rbac/me/menu-tree`
- **鉴权**：已登录

#### 查询参数
- 无

#### 成功响应 `data` 类型
- `List<MenuNodeResponse>`

#### MenuNodeResponse 字段说明

| 字段 | 类型 | 含义 | 可空 | 备注 |
|---|---|---|---|---|
| id | Long | 菜单资源ID | 否 | 对应 `resource.id` |
| code | String | 菜单编码 | 是 | 对应 `resource.code` |
| name | String | 菜单名称 | 是 | 对应 `resource.name` |
| path | String | 菜单路由 | 是 | 对应 `resource.path` |
| parentId | Long | 父菜单ID | 是 | 根节点为 `null` |
| sortOrder | Integer | 排序值 | 是 | 越小越靠前 |
| children | List<MenuNodeResponse> | 子菜单列表 | 否 | 叶子节点返回空数组 |

#### 成功示例
```json
{
  "code": 200,
  "errorCode": null,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "code": "menu:task",
      "name": "Task Center",
      "path": "/tasks",
      "parentId": null,
      "sortOrder": 20,
      "children": []
    }
  ]
}
```

---

### 3.4 查询当前用户按钮权限码清单
- **方法**：`GET`
- **路径**：`/api/rbac/me/button-permissions`
- **鉴权**：已登录

#### 查询参数
- 无

#### 成功响应 `data` 类型
- `List<String>`（按钮权限码，去重并按字典序排序）

#### 成功示例
```json
{
  "code": 200,
  "errorCode": null,
  "message": "操作成功",
  "data": [
    "ops:manage",
    "task:approval:review"
  ]
}
```

## 4. 行为说明
- 权限查询按 `id` 升序返回。
- 资源查询按 `sortOrder` 升序、再按 `id` 升序返回。
- 两个字典接口默认仅返回 `status=1` 的启用数据。
- 菜单树按用户权限关联资源向上追溯父菜单并构建树形结构。
- 按钮权限接口仅返回 `resource.type=3` 关联的权限码。

## 5. 联调注意事项
- 字典数据来源于数据库表 `permission` 与 `resource`，环境若未初始化数据将返回空数组。
- 若角色权限变更后前端有缓存，需主动刷新菜单树与按钮权限码。

## 6. 依据
- `src/main/java/com/sakurain/gpuscheduler/controller/RbacDictionaryController.java`
- `src/main/java/com/sakurain/gpuscheduler/service/RbacDictionaryService.java`
- `src/main/java/com/sakurain/gpuscheduler/dto/rbac/MenuNodeResponse.java`
- `src/main/java/com/sakurain/gpuscheduler/entity/Permission.java`
- `src/main/java/com/sakurain/gpuscheduler/entity/Resource.java`
- `src/main/java/com/sakurain/gpuscheduler/dto/Result.java`
