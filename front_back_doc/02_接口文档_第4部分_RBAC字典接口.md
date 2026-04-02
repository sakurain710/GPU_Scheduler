# 接口文档（第4部分）：RBAC字典接口

## 1. 文档范围
- 控制器：`RbacDictionaryController`
- 路径前缀：`/api/rbac`
- 覆盖接口：权限字典查询、资源字典查询

## 2. 访问控制与通用约定

### 2.1 权限要求
- 控制器级鉴权：`hasAnyAuthority('ROLE_ADMIN','rbac:read')`
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
- **鉴权**：是

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

#### 成功示例
```json
{
  "code": 200,
  "errorCode": null,
  "message": "操作成功",
  "data": [
    {
      "id": 101,
      "code": "user:manage",
      "name": "用户管理",
      "resourceId": 11,
      "action": "edit",
      "description": "用户模块管理权限",
      "status": 1,
      "createdAt": "2026-04-01T09:00:00",
      "updatedAt": "2026-04-03T09:00:00"
    }
  ]
}
```

---

### 3.2 查询资源字典
- **方法**：`GET`
- **路径**：`/api/rbac/resources`
- **鉴权**：是

#### 查询参数

| 字段 | 类型 | 含义 | 可空 | 默认值 | 枚举/约束 |
|---|---|---|---|---|---|
| parentId | Long | 按父资源ID过滤 | 是 | `null` | 正整数 |
| type | Integer | 按资源类型过滤 | 是 | `null` | `1=菜单,2=API,3=按钮,4=数据` |
| status | Integer | 按状态过滤 | 是 | `1` | `0=禁用,1=启用` |

#### 成功响应 `data` 类型
- `List<Resource>`

#### Resource 字段说明

| 字段 | 类型 | 含义 | 可空 | 备注 |
|---|---|---|---|---|
| id | Long | 资源ID | 否 | 主键 |
| code | String | 资源编码 | 是 | 例如 `gpu:list` |
| name | String | 资源名称 | 是 | - |
| type | Integer | 资源类型 | 是 | `1/2/3/4` |
| parentId | Long | 父资源ID | 是 | 树形关系 |
| path | String | 资源路径 | 是 | 菜单路由或 API 路径 |
| sortOrder | Integer | 排序值 | 是 | 越小越靠前 |
| description | String | 描述 | 是 | - |
| status | Integer | 状态 | 是 | `1`启用, `0`禁用 |
| createdAt | LocalDateTime | 创建时间 | 是 | 序列化格式待确认 |
| updatedAt | LocalDateTime | 更新时间 | 是 | 序列化格式待确认 |

#### 成功示例
```json
{
  "code": 200,
  "errorCode": null,
  "message": "操作成功",
  "data": [
    {
      "id": 11,
      "code": "user:module",
      "name": "用户管理模块",
      "type": 1,
      "parentId": null,
      "path": "/users",
      "sortOrder": 10,
      "description": "用户与角色管理",
      "status": 1,
      "createdAt": "2026-04-01T09:00:00",
      "updatedAt": "2026-04-03T09:00:00"
    }
  ]
}
```

## 4. 行为说明
- 权限查询按 `id` 升序返回。
- 资源查询按 `sortOrder` 升序、再按 `id` 升序返回。
- 两个接口默认仅返回 `status=1` 的启用数据。

## 5. 联调注意事项
- 返回对象是实体结构，不是精简 DTO，字段可能包含前端暂不使用内容。
- 字典数据来源于数据库表 `permission` 与 `resource`，环境若未初始化数据将返回空数组。

## 6. 依据
- `src/main/java/com/sakurain/gpuscheduler/controller/RbacDictionaryController.java`
- `src/main/java/com/sakurain/gpuscheduler/service/RbacDictionaryService.java`
- `src/main/java/com/sakurain/gpuscheduler/entity/Permission.java`
- `src/main/java/com/sakurain/gpuscheduler/entity/Resource.java`
- `src/main/java/com/sakurain/gpuscheduler/dto/Result.java`
