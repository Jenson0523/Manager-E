# Phase 4-B：双层 RBAC 补全 设计文档

**日期：** 2026-06-30
**版本：** V1.0
**目标：** 把"谁能用什么"这条线打通——平台层支持差异化的角色/账号管理,公司层管理员开箱即用。

---

## 1. 背景与现状

### 1.1 当前 RBAC 模型
- `user.is_super_admin`(1/0)+ `user.tenant_id`(0=平台,N=公司)。
- `role.tenant_id NOT NULL`,`UNIQUE(tenant_id, code)`——角色天然租户隔离。
- `permission` 表为全局权限点定义(无 tenant_id),现有权限均为**公司级**操作:`company/dept/branch/user/role/log/setting`。
- `role_permission`、`user_role` 关联。
- 登录时(`AuthService.login`):`is_super_admin=1` → `permissions=["*"]`、`roleCodes=["super_admin"]`;否则按用户角色查权限。`JwtAuthFilter` 把权限 `p` 映射为鉴权字符串 `PERM_<p>`,故超管持字面 `PERM_*`,端点用 `hasAuthority('PERM_*')` 守卫。
- 平台菜单(`UserInfoController.buildSuperAdminRoutes`)**硬编码**:工作台/企业管理/操作日志/系统设置。公司菜单(`buildCompanyRoutes`)按权限动态生成。

### 1.2 两个核心问题
1. **平台层无 RBAC**:超管是单个硬编码账号,无法为同事开"只能建公司""只能看数据"这类差异化平台账号。
2. **公司管理员零权限 bug**:`CompanyService.create()` 建公司管理员用户后**未分配任何角色**(对照测试 `OrgTestSupport.seedCompanyAdmin` 才补齐角色+权限)。结果新建公司的管理员登录后只看得到"工作台",组织/人员/角色全部不可见。

### 1.3 设计原则
**复用底层 RBAC 基础设施,平台层与公司层保持独立**:复用租户隔离的角色服务、权限矩阵 UI 组件等;但平台侧通过**独立的端点与页面**暴露,两层数据互不串扰。

---

## 2. 架构:双层 RBAC

| 层级 | 判定 | 账号 | 角色(tenant_id) | 管理对象 |
|------|------|------|------|------|
| **平台层** | `tenant_id = 0` | 超管 + 平台员工 | 平台角色(0) | 建/管公司、平台账号、平台角色、审计、系统设置、看板 |
| **公司层** | `tenant_id != 0` | 公司管理员 + 公司员工 | 公司角色(N) | 部门、分公司、人员、公司角色 |

两层的角色/权限互相隔离:超管建的角色挂 `tenant_id=0`(管平台),公司管理员建的角色挂 `tenant_id=N`(管公司)。

### 2.1 模型关键决策

| 决策 | 内容 |
|------|------|
| **层级判定** | `tenant_id=0`=平台层,`!=0`=公司层。登录时的"所属企业已停用"校验仅对公司层生效(`AuthService` 当前对所有非超管都查公司,需改为仅 `tenant_id!=0` 时查)。 |
| **内置 owner** | 原 `superadmin`(`is_super_admin=1`,`permissions=["*"]`)保留为最高 owner:全平台权限、不可删除/停用/锁死/改权限。 |
| **平台员工** | `tenant_id=0, is_super_admin=0`,权限来自所分配的平台角色(走 `AuthService` 的 else 分支,前提是公司校验已按层级跳过)。 |
| **权限分层** | `permission` 加 `tier` 列(`platform`/`company`);现有权限标 `company`,新增一批 `platform:*` 权限标 `platform`。 |
| **端点守卫** | 平台端点用 `hasAnyAuthority('PERM_*', 'PERM_platform:xxx')`——内置 owner(`PERM_*`)与持具体平台权限的员工都放行。 |
| **复用 vs 独立** | 租户拦截器对 `tenant_id=0` 的调用者**整体关闭**(`TenantContext.isSuperAdmin()` 判定即 `tenant==0`),故平台角色/人员都**不能依赖** `RoleService`/`UserService` 的自动隔离——平台员工按 ID 可能越权改到公司数据。因此平台 RBAC 后端**独立实现**,所有查询/改动显式限定 `tenant_id=0` 并校验目标行属于平台层;**复用只在前端 UI 组件/范式层面**(权限矩阵、列表页样式)。 |

---

## 3. 需求 A — 平台层 RBAC

### A1 权限分层(后端 + 迁移)
- **V3 迁移**:`ALTER TABLE permission ADD COLUMN tier VARCHAR(20) NOT NULL DEFAULT 'company'`;现有权限保持 `company`。
- **新增平台权限点**(module=`platform`, tier=`platform`):
  - `platform:company:view` / `create` / `edit` / `disable` —— 企业管理
  - `platform:user:view` / `create` / `edit` / `delete` / `reset_pwd` —— 平台账号管理
  - `platform:role:view` / `create` / `edit` / `delete` —— 平台角色管理
  - `platform:audit:view` / `export` —— 操作日志
  - `platform:config:view` / `edit` —— 系统设置
  - `platform:dashboard:view` —— 平台看板(只读数据)
- **权限查询接口按 tier 过滤**:权限分组接口(供权限矩阵)根据调用者层级返回对应 tier 的权限——平台用户(tenant 0)只见 `platform` 权限,公司用户只见 `company` 权限。

### A2 平台角色管理
- 新增**独立**平台角色端点 `/api/v1/platform/roles`(CRUD + 权限配置),守卫 `hasAnyAuthority('PERM_*','PERM_platform:role:...')`,由独立的平台 RBAC 服务实现,所有操作**显式限定 `tenant_id=0`**(查询带 `tenant_id=0` 条件;按 ID 改动前校验该角色 `tenant_id=0`),不依赖租户拦截器。
- 权限矩阵走 A1 的 tier 过滤(平台角色只列 platform 权限)。
- 平台角色 `data_scope` 固定为 `all`(平台层无 branch/dept,不做数据范围细分),前端隐藏数据范围选择项。
- 前端**复用权限矩阵 UI 组件**(参照 `RoleView.vue`),但为独立页面、走 `/platform/roles` 接口。
- 前端新增"平台角色"页,复用现有权限矩阵组件(参照 `RoleView.vue`)。

### A3 平台账号管理
- 新增独立端点 `/api/v1/platform/users`(CRUD + 分配平台角色),**显式限定 `tenant_id=0`**(不复用公司人员端点)。
- 平台员工无 dept/branch 概念,表单仅:账号、姓名、手机、邮箱、初始密码、平台角色。
- 前端新增"平台人员"页(参照 `UserListView.vue`,去掉部门/分公司相关列与筛选)。

### A4 平台菜单动态化
- `UserInfoController.buildSuperAdminRoutes` 由硬编码改为**按平台用户权限生成**:
  - `platform:dashboard:view` → 工作台
  - `platform:company:view` → 企业管理(`/admin/companies`)
  - `platform:user:view` → 平台人员(`/admin/users`)
  - `platform:role:view` → 平台角色(`/admin/roles`)
  - `platform:audit:view` → 操作日志(`/admin/audit-logs`)
  - `platform:config:view` → 系统设置(`/admin/system-config`)
- 内置 owner(`is_super_admin=1`)默认拥有全部平台权限 → 看到全部菜单。
- 前端新增路由 `/admin/users`(平台人员)、`/admin/roles`(平台角色);`AppSidebar` 图标映射补两条。

### A5 内置 owner 保护
- 平台人员/角色管理中,对 `is_super_admin=1` 账号与 `super_admin` 角色:禁止删除、停用、改权限、改角色分配(后端校验 + 前端隐藏对应按钮)。

---

## 4. 需求 B — 公司管理员启用修复

### B1 建公司时自动配齐管理员角色
`CompanyService.create()` 在同一事务内,插入管理员用户后补三步(对照 `OrgTestSupport.seedCompanyAdmin`):
1. 建预设角色:`INSERT INTO role(tenant_id=companyId, name='企业管理员', code='company_admin', data_scope='all', is_preset=1, status=1)`。
2. 挂接:`INSERT INTO user_role(user_id=adminUserId, role_id)`。
3. 授权:`INSERT INTO role_permission(role_id, permission_id) SELECT role_id, id FROM permission WHERE tier='company'`(只授**公司级**权限)。

### B2 存量回填
- **V3 迁移**:为已存在但其管理员缺角色的公司,补建 `company_admin` 角色 + 挂接管理员 + 授予全部 company 权限。让当前测试公司立即可用。
- 回填以"该公司是否已有 `company_admin` 角色"为幂等判据,避免重复。

---

## 5. 数据模型变更(V3 迁移汇总)
1. `permission` 加 `tier` 列(默认 `company`)。
2. 插入 `platform` 模块的新权限点(tier=`platform`)。
3. 存量公司回填 `company_admin` 角色 + 挂接 + company 权限授予。

> 注:测试用 H2 迁移(`app/src/test/resources/db/migration/h2/`)需同步加 `tier` 列与平台权限,保证测试环境一致。

---

## 6. API 变更

| 方法 | 路径 | 守卫 | 说明 |
|------|------|------|------|
| 改 | `GET /api/v1/auth/userinfo` | 已登录 | 平台菜单改权限驱动 |
| 改 | `POST /api/v1/auth/login`(`AuthService`) | — | 公司状态校验仅对 `tenant_id!=0` |
| 改 | `GET .../permissions/grouped`(权限矩阵) | role:view 类 | 按调用者 tier 过滤 |
| 新 | `GET/POST/PUT/DELETE /api/v1/platform/users` | `platform:user:*` | 平台账号 CRUD(限 tenant_id=0) |
| 新 | `PUT /api/v1/platform/users/{id}/roles` | `platform:user:edit` | 分配平台角色 |
| 新 | `GET/POST/PUT/DELETE /api/v1/platform/roles` | `platform:role:*` | 平台角色 CRUD(委托 RoleService) |
| 改 | `POST /api/v1/companies`(`CompanyService.create`) | `platform:company:create`/`PERM_*` | 自动配齐 company_admin |

---

## 7. 前端变更
- 新路由:`/admin/users`(平台人员)、`/admin/roles`(平台角色),挂在 `MainLayout` 下。
- 新页面:`PlatformUserListView.vue`、`PlatformRoleView.vue`(复用现有列表/权限矩阵范式,数据走 `/platform/*`)。
- `AppSidebar` 图标映射补 `/admin/users`、`/admin/roles`。
- owner 保护:平台人员/角色页对内置 owner 账号、`super_admin` 角色隐藏删除/停用/改权限按钮。

---

## 8. 安全与边界
- 平台端点统一 `hasAnyAuthority('PERM_*', <具体平台权限>)`,owner 始终放行。
- 平台用户/角色端点强制 `tenant_id=0` 范围,杜绝越权操作公司数据。
- 内置 owner 不可被任何人删除/停用/降权(后端硬校验)。
- 公司管理员仅能获得 `tier='company'` 权限,无法触碰平台权限。

---

## 9. 测试策略
- **平台员工差异化**:建平台员工仅配 `platform:dashboard:view` → 登录只见工作台、无操作权;改配 `platform:company:*` → 可建/管公司。
- **owner 保护**:删除/停用 owner 账号或 `super_admin` 角色 → 拒绝。
- **平台人员隔离**:平台人员列表只返回 `tenant_id=0` 账号,不含任何公司员工。
- **公司管理员启用**:建公司 → 断言 admin 自动拥有 `company_admin` 角色 + 全部 company 权限;`userinfo` 含 `/company/users`;该账号能进人员管理。
- **存量回填**:回填迁移后,老公司管理员同样具备 company_admin。
- **权限 tier 过滤**:平台角色的权限矩阵只列 platform 权限,公司角色只列 company 权限。
- 前端:`npm run build` 通过。

---

## 10. 明确不做(YAGNI / 留待后续)
- §9.1 公司到期自动停用、§9.2 人员配额校验 —— 归入 **Phase 4-C**。
- 平台层不引入 dept/branch(平台无分支结构)。
- 跨公司人员聚合视图 / 超管直接管理某公司内部人员。
- 平台员工的数据范围(data_scope)细分 —— 平台层统一 `all`。

---

*设计文档版本：V1.0 | 日期：2026-06-30 | 阶段：Phase 4-B*
