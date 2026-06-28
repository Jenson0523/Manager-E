# 腾飞企业管理系统 — 技术设计文档

**项目名称**：腾飞多租户企业管理系统  
**版本**：V1.0  
**日期**：2026-06-26  
**团队**：2人 + AI 辅助（Claude + Workbuddy）  

---

## 1. 确认的技术决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| 前端框架 | Vue 3 + Element Plus + Vite | 国内企业后台生态最完善，中文文档丰富 |
| 后端框架 | Spring Boot 3（模块化单体） | 国内生态成熟，后续接 APP/小程序 SDK 完整 |
| 数据库 | MySQL 8.0（本地开发先用本地 MySQL） | 主流，MyBatis-Plus 多租户支持完善 |
| 缓存 | Redis 7 | Token 黑名单，{tenant_id}:key 命名隔离 |
| ORM | MyBatis-Plus | 自动 tenant_id 拦截注入 |
| 状态管理 | Pinia | Vue 3 官方推荐 |
| HTTP 客户端 | Axios（封装 JWT 自动刷新） | 标准选择 |
| 架构模式 | 模块化单体 → 未来按压力拆微服务 | 2人团队可驾驭，边界清晰可演进 |
| UI 风格 | 深色侧边栏 + 浅色内容区 + 蓝色主色 | 专业感强，与参考界面一脉相承 |

---

## 2. 系统架构

### 2.1 整体分层

```
┌─────────────────────────────────────────────────────────┐
│ 前端层（Vue 3 + Vite + Element Plus）                    │
│  超级管理后台 | 公司管理后台 | 分公司管理后台 | 员工端(预留) │
└─────────────────────┬───────────────────────────────────┘
                      │ HTTPS · REST API · JSON
┌─────────────────────┴───────────────────────────────────┐
│ Spring Boot 3 模块化单体                                  │
│  ┌──────────────────────────────────────────────────┐   │
│  │ 公共层：统一认证拦截 | 租户识别注入 | 权限校验 |    │   │
│  │         操作日志 AOP | 限流 | 全局异常处理        │   │
│  └──────────────────────────────────────────────────┘   │
│  ┌──────────┐ ┌─────────────┐ ┌──────────┐ ┌────────┐  │
│  │core-auth │ │core-company │ │ core-org │ │core-rbac│  │
│  └──────────┘ └─────────────┘ └──────────┘ └────────┘  │
│  ┌──────────┐ ┌─────────────────────────────────────┐   │
│  │  audit   │ │ ext-* 扩展模块（OA/CRM/业务，预留）  │   │
│  └──────────┘ └─────────────────────────────────────┘   │
└─────────────────────┬───────────────────────────────────┘
                      │ JDBC / MyBatis-Plus
┌─────────────────────┴───────────────────────────────────┐
│ 数据层                                                    │
│  MySQL 8.0（所有表含 tenant_id）| Redis 7 | OSS（预留）  │
└─────────────────────────────────────────────────────────┘
         ↑ 同一套 REST API
  APP（Flutter）| 小程序（uni-app）
```

### 2.2 后端模块职责

| 模块 | 职责 | 关键类 |
|------|------|--------|
| `core-auth` | 登录/登出、JWT 生成与验证、Token 黑名单、登录锁定、强制下线 | `AuthController`, `JwtUtil`, `TokenBlacklistService` |
| `core-company` | 租户管理（超级管理员使用）、公司 CRUD、有效期、配额、平台仪表板 | `CompanyController`, `CompanyService` |
| `core-org` | 部门树、分公司、人员管理、工号生成、批量操作、Excel 导出 | `DeptController`, `BranchController`, `UserController` |
| `core-rbac` | 角色 CRUD、权限点管理、角色-权限关联、用户-角色关联、权限树 | `RoleController`, `PermissionController` |
| `audit` | 操作日志记录（AOP 切面）、日志查询、导出、敏感操作快照 | `AuditLogAspect`, `AuditLogController` |
| `ext-*` | 预留扩展接口（模块注册、权限注入），V1.0 不实现具体业务 | `ModuleRegistry`（接口定义） |

### 2.3 前端目录结构

```
src/
├── api/              # 接口请求（按模块拆分）
│   ├── auth.ts
│   ├── company.ts
│   ├── org.ts
│   └── rbac.ts
├── layouts/
│   ├── AdminLayout.vue      # 超级管理后台布局
│   ├── CompanyLayout.vue    # 公司管理后台布局
│   └── BranchLayout.vue     # 分公司管理后台布局
├── views/
│   ├── admin/        # 超级管理后台页面
│   ├── company/      # 公司管理后台页面
│   ├── branch/       # 分公司管理后台页面
│   └── common/       # 登录、个人中心、403、404
├── stores/           # Pinia 状态（user, permission, app）
├── router/           # 动态路由（登录后 addRoute）
├── utils/            # axios 封装、权限指令、工具函数
└── styles/           # 主题变量、全局样式
```

---

## 3. UI 设计规范

### 3.1 配色系统

| 用途 | 色值 | 说明 |
|------|------|------|
| 侧边栏背景 | `#0f172a` | 深海蓝，导航底色 |
| 内容区背景 | `#f8fafc` | 极浅灰，主内容区 |
| 卡片/表格背景 | `#ffffff` | 纯白 |
| 主色（Primary） | `#1d4ed8` | 按钮、激活菜单、链接 |
| 成功色 | `#10b981` | 状态"正常"、成功提示 |
| 警告色 | `#f59e0b` | 即将到期、待处理 |
| 危险色 | `#ef4444` | 停用状态、删除操作 |
| 主文字 | `#0f172a` | 标题、重要内容 |
| 次要文字 | `#64748b` | 说明文字、占位符 |
| 边框 | `#e2e8f0` | 卡片边框、分隔线 |

### 3.2 字体与间距

- 字体：`Inter, PingFang SC, -apple-system, sans-serif`
- 圆角系统：`4px`（小）/ `8px`（卡片）/ `12px`（弹窗）
- 间距基准：`4px` 倍数（8 / 12 / 16 / 24 / 32px）
- 表格行高：48px，紧凑模式 40px
- 侧边栏宽度：220px（展开）/ 64px（折叠）

### 3.3 核心组件规范

- 所有列表页：搜索栏 + 操作按钮 + 表格 + 分页，统一布局
- 新增/编辑：优先用右侧抽屉（Drawer），复杂表单用弹窗（Dialog）
- 状态标签：统一用 `<el-tag>` 带圆角，正常=绿、停用=红、审核中=橙
- 空状态：统一插图 + 引导文字 + 操作按钮
- Loading：骨架屏（Skeleton），不用全屏 loading

---

## 4. RBAC 三级权限体系

### 4.1 角色层次

```
平台级：超级管理员（tenant_id=0，系统内置）
  └── 管理所有公司，不参与公司内部 RBAC

公司级：公司超级管理员（company_super_admin，预置不可删）
  └── scope:all，管全公司所有资源
      ├── 创建任意自定义角色
      └── 分公司管理员（branch_admin，公司自定义角色）
              └── scope:branch，仅管本分公司人员/角色/日志
```

### 4.2 数据范围（Data Scope）

| 范围编码 | 说明 | 典型角色 |
|----------|------|----------|
| `scope:all` | 全公司数据 | 公司超级管理员 |
| `scope:branch` | 仅本分公司 | 分公司管理员 |
| `scope:dept` | 仅本部门 | 部门主管 |
| `scope:self` | 仅本人 | 普通员工 |

MyBatis-Plus 拦截器根据 JWT 中的 `data_scope` + `branch_id` / `dept_id` 自动追加数据过滤条件。

### 4.3 JWT Payload

```json
{
  "tenant_id": 10001,
  "user_id": 5001,
  "branch_id": 201,
  "role_codes": ["branch_admin"],
  "permissions": ["user:view", "user:create", "user:edit"],
  "data_scope": "branch",
  "exp": 1782454800
}
```

Token 有效期 2 小时，过期前 30 分钟内可刷新。退出/强制下线后 Token 加入 Redis 黑名单。

### 4.4 V1.0 权限点清单

| 模块 | 权限点 |
|------|--------|
| 公司管理 | `company:view` `company:edit` |
| 部门管理 | `dept:view` `dept:create` `dept:edit` `dept:delete` |
| 分公司管理 | `branch:view` `branch:create` `branch:edit` `branch:delete` |
| 人员管理 | `user:view` `user:create` `user:edit` `user:delete` `user:reset_pwd` |
| 角色管理 | `role:view` `role:create` `role:edit` `role:delete` |
| 日志与设置 | `log:view` `log:export` `setting:view` `setting:edit` |

---

## 5. 数据库设计

### 5.1 租户隔离规则

- **所有业务表必须含 `tenant_id BIGINT NOT NULL` 字段并建索引**
- MyBatis-Plus `TenantLineInnerInterceptor` 自动追加 `WHERE tenant_id = ?`
- 超级管理员 `tenant_id = 0`，配置为不受拦截
- Redis Key 命名：`{tenant_id}:{module}:{key}`
- OSS 路径：`/{env}/{tenant_id}/{module}/{yyyy}/{mm}/{uuid}.{ext}`

### 5.2 核心表清单

| 表名 | 说明 | 含 tenant_id |
|------|------|:---:|
| `company` | 公司（租户）信息 | ❌（自身即租户标识） |
| `user` | 用户（含超级管理员） | ✅ |
| `dept` | 部门（树形，parent_id） | ✅ |
| `branch` | 分公司 | ✅ |
| `branch_dept` | 分公司-部门关联 | ✅ |
| `role` | 角色（含预置/自定义） | ✅ |
| `permission` | 权限点（平台级） | ❌ |
| `role_permission` | 角色-权限关联 | ❌ |
| `user_role` | 用户-角色关联 | ❌ |
| `audit_log` | 操作日志 | ✅ |
| `login_log` | 登录日志 | ✅ |
| `system_config` | 系统配置（0=平台级） | ✅ |
| `module_registry` | 扩展模块注册（预留） | ❌ |

### 5.3 关键字段约定

- 主键：全部用 `BIGINT AUTO_INCREMENT`（生产环境可换雪花 ID）
- 逻辑删除：`is_deleted TINYINT DEFAULT 0`，MyBatis-Plus 逻辑删除插件自动过滤
- 时间字段：`created_at` / `updated_at DATETIME NOT NULL`
- 状态字段：`status TINYINT`，含义随业务定义，注释说明
- 编号生成规则：公司 `C{yyyyMMdd}{3位流水}`，人员工号 `E{yyyyMMdd}{3位流水}`，分公司 `B{3位流水}`

---

## 6. API 规范

### 6.1 通用约定

| 项目 | 规范 |
|------|------|
| 前缀 | `/api/v1/` |
| 鉴权 | `Authorization: Bearer {jwt}` |
| 响应格式 | `{ "code": 0, "msg": "success", "data": {} }` |
| 分页参数 | `?page=1&size=20` |
| 分页响应 | `{ "total": 156, "page": 1, "size": 20, "list": [...] }` |
| 时间格式 | ISO 8601 + 东八区 |

### 6.2 错误码

| 码 | 含义 |
|----|------|
| 0 | 成功 |
| 401 | 未认证 / Token 过期 |
| 403 | 无权限 / 跨租户拒绝 |
| 404 | 资源不存在 |
| 409 | 数据冲突（重名等） |
| 422 | 参数校验失败 |
| 423 | 账号/公司被锁定或停用 |
| 500 | 服务器内部错误 |

### 6.3 核心接口路由

```
# 认证
POST   /api/v1/auth/login
POST   /api/v1/auth/logout
POST   /api/v1/auth/refresh
GET    /api/v1/auth/userinfo        ← 返回路由权限列表

# 超级管理员 — 公司管理
GET    /api/v1/admin/companies
POST   /api/v1/admin/companies
GET    /api/v1/admin/companies/{id}
PUT    /api/v1/admin/companies/{id}
PUT    /api/v1/admin/companies/{id}/status
DELETE /api/v1/admin/companies/{id}
GET    /api/v1/admin/dashboard

# 公司端 — 组织管理
GET    /api/v1/depts/tree
POST   /api/v1/depts
PUT    /api/v1/depts/{id}
DELETE /api/v1/depts/{id}
PUT    /api/v1/depts/{id}/move

GET    /api/v1/branches
POST   /api/v1/branches
PUT    /api/v1/branches/{id}
DELETE /api/v1/branches/{id}
PUT    /api/v1/branches/{id}/status

# 公司端 — 人员管理
GET    /api/v1/users
POST   /api/v1/users
PUT    /api/v1/users/{id}
DELETE /api/v1/users/{id}
PUT    /api/v1/users/{id}/status
PUT    /api/v1/users/{id}/reset-pwd
PUT    /api/v1/users/batch-role
GET    /api/v1/users/export

# 公司端 — 角色权限
GET    /api/v1/roles
POST   /api/v1/roles
GET    /api/v1/roles/{id}
PUT    /api/v1/roles/{id}
DELETE /api/v1/roles/{id}
GET    /api/v1/permissions/tree

# 日志
GET    /api/v1/logs/operation
GET    /api/v1/logs/operation/{id}
GET    /api/v1/logs/login
```

---

## 7. 安全设计

| 要求 | 实现方式 |
|------|----------|
| 密码存储 | BCrypt（strength=12），不可逆 |
| 连续失败锁定 | 5次失败锁定 15 分钟，记录 `login_fail_count` + `locked_until` |
| 首次登录 | `pwd_reset_required=1`，强制改密才能进入系统 |
| SQL 注入 | MyBatis-Plus 参数化查询，禁止 `${} ` 拼接 |
| XSS | 前端输出转义 + Spring 全局 XSS 过滤器 |
| CSRF | JWT 无状态，不依赖 Cookie，无 CSRF 风险 |
| 跨租户隔离 | 网关层校验 `tenant_id`，API 层二次校验，403 拒绝跨租户请求 |
| Token 黑名单 | 退出/停用后 Token 存入 Redis，有效期内拒绝访问 |

---

## 8. 扩展接口预留（V1.0 仅定义接口）

```java
// 模块注册接口（ext-* 模块实现此接口后自动接入权限树）
public interface ExtModuleProvider {
    ModuleInfo getModuleInfo();           // 模块基本信息
    List<PermissionDef> getPermissions(); // 模块权限点清单
    List<MenuDef> getMenuConfig();        // 模块菜单配置
}
```

后续接入 OA、CRM 等：实现 `ExtModuleProvider`，Spring Boot 自动扫描注册，无需修改核心代码。

预留 SSO 端点（`/api/sso/*`）、数据同步端点（`/api/sync/*`）、Webhook 端点、开放 API（`/api/open/v1/*`）。

---

## 9. 业务规则补充

### 9.1 公司有效期到期处理

- Spring Boot 定时任务（每天 02:00）扫描 `expire_date < NOW()` 且 `status=1` 的公司
- 自动将 `status` 置为 `2（停用）`，该公司所有用户 Token 加入黑名单
- 数据保留不删除，超级管理员可手动续期后重新启用
- V1.0 无宽限期，到期当天夜间即停用

### 9.2 人员/分公司配额校验

- 新增人员时检查 `current_count < max_users`（公司级 + 分公司级双重校验）
- 超额返回 `409` + 明确提示，前端展示当前用量/上限比例条

---

## 11. 明确不做（V1.0 Non-Goals）

- 移动端 App / 小程序（预留 API，具体开发后续立项）
- 多语言 / 国际化
- 数据报表 / BI
- 工作流 / OA 引擎
- 物理删除（均为逻辑删除）
- SSO 完整对接（仅预留接口）
- 自定义表单字段

---

## 12. 开发环境约定

| 项 | 配置 |
|----|------|
| 本地数据库 | MySQL 8.0 本地实例，数据库名 `tengyei_dev` |
| 本地缓存 | Redis 本地实例，默认 6379 端口 |
| 后端启动端口 | 8080 |
| 前端启动端口 | 5173（Vite 默认） |
| 前端代理 | Vite proxy `/api` → `http://localhost:8080` |
| 数据库迁移 | Flyway（SQL 版本化管理，后续生产迁移） |

---

*设计文档版本：V1.0 | 日期：2026-06-26*
