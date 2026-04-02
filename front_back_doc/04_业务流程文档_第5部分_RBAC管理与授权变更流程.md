# 业务流程文档（第5部分）：RBAC管理与授权变更流程

## 1. 文档目标
- 说明用户、角色、权限在后台的维护流程。
- 说明“授权变更后何时生效”以及前端联调应关注的调用顺序。

## 2. 涉及模块
- 用户管理：`UserController`、`UserService`
- 角色管理：`RoleController`、`RoleService`
- 字典查询：`RbacDictionaryController`、`RbacDictionaryService`
- 鉴权载入：`CustomUserDetailsService`、`CustomUserDetails`

## 3. 用户生命周期流程

### 3.1 创建用户
1. 管理员调用 `POST /api/users`。
2. `UserService.createUser` 检查用户名/邮箱唯一性。
3. 密码加密后写入 `user` 表。
4. 返回 `UserResponse`（含基础信息与角色列表）。

### 3.2 更新用户
1. 调用 `PUT /api/users/{userId}`。
2. 可更新 `email/status`。
3. 更新邮箱时再次执行唯一性校验。

### 3.3 删除用户
1. 调用 `DELETE /api/users/{userId}`。
2. 当前实现通过 Mapper 删除（实际行为取决于实体/表软删配置）。

## 4. 角色生命周期流程

### 4.1 创建角色
1. 调用 `POST /api/roles`。
2. `RoleService.createRole` 校验 `role.code` 唯一。
3. 插入 `role`。
4. 如请求包含 `permissionIds`，立即执行角色权限绑定。

### 4.2 更新角色
1. 调用 `PUT /api/roles/{roleId}`。
2. 仅更新非空字段。
3. `permissionIds != null` 时触发“先清空后重建”角色权限。

### 4.3 删除角色
1. 调用 `DELETE /api/roles/{roleId}`。
2. 若角色下仍有用户绑定（`user_role` 非空）则拒绝删除（`ROLE_IN_USE`）。
3. 否则先删 `role_permission`，再删 `role`。

## 5. 绑定关系变更流程

### 5.1 用户绑定角色（user_role）
1. 调用 `POST /api/users/{userId}/roles`。
2. `UserService.assignRoles` 先校验用户存在、角色存在。
3. 删除该用户全部旧角色绑定。
4. 按请求 `roleIds` 重建绑定，可写 `expiresAt`。

说明：该接口语义为“覆盖写入”，不是增量追加。

### 5.2 角色绑定权限（role_permission）
1. 调用 `POST /api/roles/{roleId}/permissions`。
2. 校验角色与权限存在。
3. 先删除角色已有权限，再插入新权限集合。

说明：同样是“覆盖写入”语义。

### 5.3 角色解绑权限
1. 调用 `DELETE /api/roles/{roleId}/permissions`。
2. 仅删除请求体中的权限 ID，不影响其他权限。

### 5.4 角色绑定用户/解绑用户
- 绑定：`POST /api/roles/{roleId}/users`（批量新增 `user_role`）
- 解绑：`DELETE /api/roles/{roleId}/users`（按用户ID批量删除）

## 6. 权限字典查询流程

### 6.1 查询权限字典
- `GET /api/rbac/permissions`
- 可按 `resourceId/status` 过滤，默认 `status=1`。

### 6.2 查询资源字典
- `GET /api/rbac/resources`
- 可按 `parentId/type/status` 过滤，默认 `status=1`。

用途：支撑前端“角色授权页面”中的可选权限树/资源树。

## 7. 授权生效时机流程

### 7.1 当前生效机制
1. 请求到达时，`JwtAuthenticationFilter` 根据 token 解析 userId。
2. `CustomUserDetailsService.loadUserById` 实时从数据库加载：
- 用户角色列表
- 用户权限码列表
3. `CustomUserDetails` 合并角色码与权限码为 `GrantedAuthority`。
4. 控制器 `@PreAuthorize` 立即基于新权限集判断。

结论：授权变更后无需等待 token 刷新即可在后续请求生效（依赖每次请求实时加载）。

### 7.2 与前端的联动建议
- 角色/权限改动后，前端应主动刷新当前用户信息与菜单权限缓存。
- 若前端本地仅按角色控制展示，需同步引入权限码维度以避免漏控。

## 8. 页面与接口关系
- 用户管理页：`/api/users`、`/api/users/{id}`、`/api/users/{id}/roles`
- 角色管理页：`/api/roles`、`/api/roles/{id}`、`/api/roles/{id}/permissions`、`/api/roles/{id}/users`
- 授权配置页：`/api/rbac/resources` + `/api/rbac/permissions`

## 9. 风险与边界
- 用户绑定角色与角色绑定权限均为覆盖写入，前端提交前需合并现有选中项。
- `AssignUsersRequest/AssignPermissionsRequest` 同时包含路径 roleId 与 body roleId，当前未做一致性校验。
- 权限字典数据依赖数据库初始化；若无种子数据，授权页会返回空集合。

## 10. 依据
- `src/main/java/com/sakurain/gpuscheduler/controller/UserController.java`
- `src/main/java/com/sakurain/gpuscheduler/controller/RoleController.java`
- `src/main/java/com/sakurain/gpuscheduler/controller/RbacDictionaryController.java`
- `src/main/java/com/sakurain/gpuscheduler/service/UserService.java`
- `src/main/java/com/sakurain/gpuscheduler/service/RoleService.java`
- `src/main/java/com/sakurain/gpuscheduler/service/RbacDictionaryService.java`
- `src/main/java/com/sakurain/gpuscheduler/security/JwtAuthenticationFilter.java`
- `src/main/java/com/sakurain/gpuscheduler/security/CustomUserDetailsService.java`
- `src/main/java/com/sakurain/gpuscheduler/security/CustomUserDetails.java`
