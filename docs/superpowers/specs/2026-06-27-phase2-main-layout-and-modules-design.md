# Phase 2 — 主界面布局 + 基础管理模块 设计文档

## 目标

在 Phase 1 认证基础之上，构建完整可用的企业管理系统 UI 框架和六个核心管理模块，使超级管理员和企业管理员均可执行日常操作。

## 架构概述

**前端：** Vue 3 + Element Plus，主框架组件 + 六个业务视图模块。  
**后端：** Spring Boot 在 `core-company`、`core-org`、`core-rbac` 三个已有模块中添加 CRUD 接口，统一走 `app` 模块注册路由。  
**权限：** 所有业务接口通过 JWT 中的 `permissions` 列表做接口级鉴权（`@PreAuthorize` 或自定义注解），数据隔离由 MyBatis-Plus `TenantLineInterceptor` 自动注入 `tenant_id`。

---

## 一、前端主框架

### 1.1 布局结构

```
MainLayout.vue
├── AppSidebar.vue      深色侧边栏 220px，品牌 logo + 动态菜单
├── AppHeader.vue       顶栏：面包屑 + 用户头像下拉（退出）
├── AppTabBar.vue       多标签栏：可关闭标签，右键菜单
└── <RouterView>        keep-alive 缓存已打开的标签页内容
```

### 1.2 AppSidebar

- 从 `useAuthStore().routes` 读取菜单列表（由 `/api/v1/auth/userinfo` 返回，已按角色过滤）
- 当前激活菜单项高亮（蓝色左边框 + 浅蓝背景）
- 顶部固定品牌区：Logo SVG + "腾飞企业管理"文字
- 底部固定：版本号

### 1.3 AppTabBar

- 状态存储于 `useTabStore()`（Pinia），同步写入 `sessionStorage` 实现刷新后恢复
- 打开新路由 → 追加标签；已存在 → 激活已有标签
- 每个标签可点 × 关闭（当前激活标签关闭后自动跳转左侧标签）
- 右键菜单：关闭当前 / 关闭其他 / 关闭全部
- 工作台（Dashboard）标签不可关闭

### 1.4 AppHeader

- 左侧：面包屑（当前路由层级）
- 右侧：用户真实姓名 + 头像下拉（个人信息入口、退出登录）

### 1.5 路由结构

```
/                       → 重定向 /dashboard
/login                  guestOnly
/403                    公开
/dashboard              requiresAuth，主框架内
/admin/companies        requiresAuth，仅超管
/company/org            requiresAuth
/company/users          requiresAuth
/company/roles          requiresAuth
/company/audit-log      requiresAuth
/company/settings       requiresAuth
```

所有 `/dashboard` 及以下路由使用 `MainLayout` 作为父路由组件。

---

## 二、业务模块

### 2.1 工作台（Dashboard）

**超级管理员视图：**
- 统计卡片：企业总数、活跃企业数、今日新增企业、总用户数
- 最近注册企业列表（最新 5 条）

**企业管理员视图：**
- 统计卡片：部门数、分支机构数、人员总数、今日登录人数
- 快捷操作入口卡片（跳转各管理页面）

数据来源：各模块汇总接口 `GET /api/v1/dashboard/stats`。

---

### 2.2 企业管理（仅超级管理员）

**页面：** `/admin/companies`

**功能：**
- 列表：名称、联系人、联系电话、状态（启用/停用）、创建时间、操作
- 搜索：按企业名称模糊搜索
- 新增企业（弹窗表单）：企业名称、简称、联系人姓名、电话、初始管理员账号、初始管理员密码。新增操作在一个事务中完成：插入 `company` + 以该公司 ID 为 `tenant_id` 创建初始管理员用户
- 编辑企业基础信息（弹窗）
- 启用 / 停用（状态切换，二次确认）
- 分页：每页 20 条

**后端接口：**
```
GET    /api/v1/companies?page=&size=&keyword=
POST   /api/v1/companies          新增（同时创建默认管理员账号）
PUT    /api/v1/companies/{id}     编辑基础信息
PUT    /api/v1/companies/{id}/status  启用/停用
GET    /api/v1/companies/{id}    详情
```

**数据库表：** `company`（已存在）

---

### 2.3 组织管理

**页面：** `/company/org`，左右布局（左：部门树，右：分支机构列表）

#### 部门管理（左侧树）

- 树形展示（el-tree），支持展开/收起
- 操作：新增部门（指定父部门）、编辑名称、删除（无子部门且无人员时允许删除）
- 拖拽排序（el-tree draggable）

**后端接口：**
```
GET    /api/v1/depts/tree         返回树形结构
POST   /api/v1/depts              新增
PUT    /api/v1/depts/{id}         编辑
DELETE /api/v1/depts/{id}         删除
PUT    /api/v1/depts/{id}/order   调整排序
```

#### 分支机构管理（右侧列表）

- 列表：机构编号、名称、负责人、联系电话、状态、操作
- 新增 / 编辑（弹窗）
- 启用 / 停用

**后端接口：**
```
GET    /api/v1/branches?page=&size=
POST   /api/v1/branches
PUT    /api/v1/branches/{id}
PUT    /api/v1/branches/{id}/status
```

**数据库表：** `dept`、`branch`（已存在）

---

### 2.4 人员管理

**页面：** `/company/users`

**功能：**
- 列表：姓名、用户名、手机号、部门、所属分支、角色、状态、操作
- 搜索：按姓名/用户名/手机号
- 筛选：按部门、按角色
- 新增用户（弹窗）：用户名、真实姓名、手机号、邮箱、初始密码、所属部门、所属分支机构（可选）、分配角色
- 编辑用户信息
- 分配角色（弹窗，多选 el-checkbox-group）
- 启用 / 停用 / 重置密码

**后端接口：**
```
GET    /api/v1/users?page=&size=&keyword=&deptId=&roleId=
POST   /api/v1/users
PUT    /api/v1/users/{id}
PUT    /api/v1/users/{id}/status
PUT    /api/v1/users/{id}/roles      分配角色（body: roleIds[]）
PUT    /api/v1/users/{id}/reset-password
```

**数据库表：** `user`、`user_role`（已存在）

---

### 2.5 角色与权限

**页面：** `/company/roles`，左右布局（左：角色列表，右：权限矩阵）

#### 角色列表（左侧）

- 列表：角色名称、角色编码、数据范围、描述、状态
- 新增 / 编辑角色（弹窗）：名称、编码、数据范围（all/company/branch/self）、描述
- 删除角色（无用户使用时允许删除）
- 点击角色 → 右侧展示该角色已分配的权限

#### 权限矩阵（右侧）

- 按模块分组展示所有权限（来自 `permission` 表）
- el-checkbox-group，勾选后保存
- 保存触发 `/api/v1/roles/{id}/permissions`

**后端接口：**
```
GET    /api/v1/roles?tenantId=
POST   /api/v1/roles
PUT    /api/v1/roles/{id}
DELETE /api/v1/roles/{id}
PUT    /api/v1/roles/{id}/permissions   body: permissionIds[]
GET    /api/v1/permissions              返回所有权限（按 module 分组）
```

**数据库表：** `role`、`role_permission`、`permission`（已存在）

---

## 三、后端模块分工

| 新接口位于 | 模块 |
|-----------|------|
| `/api/v1/companies/**` | `core-company` |
| `/api/v1/depts/**`, `/api/v1/branches/**` | `core-org` |
| `/api/v1/users/**` | `core-org`（用户归属于组织） |
| `/api/v1/roles/**`, `/api/v1/permissions` | `core-rbac` |
| `/api/v1/dashboard/stats` | `app`（汇总查询） |

每个模块新增：`Entity` → `Mapper` → `Service` → `Controller`，遵循 Phase 1 的 `BaseEntity`、`Result`、`TenantContext` 规范。

---

## 四、权限控制规范

- 接口层：`JwtAuthFilter` 已将 `permissions` 注入 `SecurityContextHolder`，使用 `@PreAuthorize("hasAuthority('PERM_user:create')")` 做方法级鉴权。需在 `SecurityConfig` 上添加 `@EnableMethodSecurity` 开启支持
- 数据层：MyBatis-Plus 租户拦截器自动隔离（无需手动加 `tenant_id` 条件）
- 前端：路由守卫已就绪；按钮级权限用 `useAuthStore().hasPermission('user:create')` 控制显示

---

## 五、不在本期范围内

- 审计日志查询页（记录已写入，查询 UI 延后）
- 公司设置页
- 用户个人信息修改
- 通知 / 消息中心
- 数据导入导出

---

## 六、验收标准

1. 超管登录后能看到企业管理菜单，可新增企业并启用/停用
2. 企业管理员登录后能看到组织/人员/角色三个菜单
3. 新增用户并分配角色后，该用户可正常登录并看到对应权限的菜单
4. 标签页可正常打开、切换、关闭
5. 刷新页面后标签列表恢复（sessionStorage 持久化）
6. 所有列表支持分页和关键字搜索
