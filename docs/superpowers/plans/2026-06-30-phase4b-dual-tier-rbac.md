# Phase 4-B：双层 RBAC 补全 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让平台层支持差异化的角色/账号管理,并修复"新建公司管理员零权限"的 bug,使两层 RBAC 各自可用。

**Architecture:** 以 `tenant_id` 区分层级(0=平台,N=公司)。平台 RBAC 后端**独立实现**(放在 `app` 模块 `com.tengyei.platform` 包,JdbcTemplate 实现),所有查询/改动**显式限定 `tenant_id=0`** 并校验目标行属于平台层——因为 MyBatis 租户拦截器对 `tenant_id=0` 的调用者整体关闭(`TenantContext.isSuperAdmin()` 即 `tenant==0`),不能依赖自动隔离。权限点加 `tier` 列区分平台/公司权限。前端复用权限矩阵/列表 UI 范式,但平台为独立页面、独立接口。

**Tech Stack:** Spring Boot 3.2.5、Java 17、JdbcTemplate、Flyway(MySQL 生产 + H2 测试双套迁移)、Spring Security `@PreAuthorize`、MyBatis-Plus、Vue 3 + TS + Element Plus + Pinia。

**关键安全不变量:** 授权一律基于 `@PreAuthorize` 的具体权限,**不**用 `isSuperAdmin()` 做鉴权决策;平台端点用 `hasAnyAuthority('PERM_*', <具体平台权限>)`;内置 owner(`is_super_admin=1`)与 `super_admin` 角色不可被删/停用/降权。

---

## File Map

### 新增文件(后端)
| 文件 | 职责 |
|------|------|
| `tengyei-backend/app/src/main/resources/db/migration/V3__platform_rbac.sql` | 生产迁移:permission 加 tier、种平台权限、回填 company_admin |
| `tengyei-backend/app/src/test/resources/db/migration/h2/V3__platform_rbac.sql` | H2 测试迁移(同上,H2 语法) |
| `tengyei-backend/app/src/main/java/com/tengyei/platform/dto/PlatformRoleDTO.java` | 平台角色请求体 |
| `tengyei-backend/app/src/main/java/com/tengyei/platform/dto/PlatformRoleVO.java` | 平台角色返回体 |
| `tengyei-backend/app/src/main/java/com/tengyei/platform/dto/PlatformUserDTO.java` | 平台账号请求体 |
| `tengyei-backend/app/src/main/java/com/tengyei/platform/dto/PlatformUserVO.java` | 平台账号返回体 |
| `tengyei-backend/app/src/main/java/com/tengyei/platform/service/PlatformRbacService.java` | 平台角色+账号服务(tenant_id=0 显式限定 + owner 保护) |
| `tengyei-backend/app/src/main/java/com/tengyei/platform/controller/PlatformRoleController.java` | `/api/v1/platform/roles` |
| `tengyei-backend/app/src/main/java/com/tengyei/platform/controller/PlatformUserController.java` | `/api/v1/platform/users` |

### 修改文件(后端)
| 文件 | 改动 |
|------|------|
| `core-rbac/.../entity/Permission.java` | 加 `tier` 字段 |
| `core-rbac/.../service/PermissionService.java` | `grouped()` 按调用者层级过滤 tier |
| `core-auth/.../service/AuthService.java` | 公司状态校验仅 `tenant_id!=0` |
| `app/.../controller/UserInfoController.java` | 菜单按 `tenant_id==0` 走平台分支,平台菜单按权限生成 |
| `core-company/.../service/CompanyService.java` | create 自动建 company_admin 角色+挂接+授公司权限 |
| `core-company/.../controller/CompanyController.java` | 守卫开放给 `platform:company:*` |
| `app/src/test/java/com/tengyei/OrgTestSupport.java` | 授权改为只授 `tier='company'` 权限 |

### 新增/修改文件(前端)
| 文件 | 改动 |
|------|------|
| `tengyei-frontend/src/api/platform.ts` | 新增 platformRoleApi + platformUserApi |
| `tengyei-frontend/src/types/platform.ts` | 平台角色/账号类型 |
| `tengyei-frontend/src/views/platform/PlatformRoleView.vue` | 平台角色页(复用权限矩阵范式) |
| `tengyei-frontend/src/views/platform/PlatformUserListView.vue` | 平台人员页(复用列表范式,无部门/分公司) |
| `tengyei-frontend/src/router/index.ts` | 加 `/admin/users`、`/admin/roles` 路由 |
| `tengyei-frontend/src/layout/AppSidebar.vue` | 图标映射补两条 |

---

## Task 1：V3 迁移 — permission.tier + 平台权限 + 回填 company_admin

**Files:**
- Create: `tengyei-backend/app/src/main/resources/db/migration/V3__platform_rbac.sql`
- Create: `tengyei-backend/app/src/test/resources/db/migration/h2/V3__platform_rbac.sql`

- [ ] **Step 1: 写生产迁移(MySQL)**

`tengyei-backend/app/src/main/resources/db/migration/V3__platform_rbac.sql`:
```sql
-- 1. permission 加 tier 列（区分平台/公司权限）
ALTER TABLE permission ADD COLUMN tier VARCHAR(20) NOT NULL DEFAULT 'company' COMMENT 'platform/company';

-- 2. 新增平台层权限点
INSERT INTO permission (module, code, name, tier, sort_order) VALUES
('platform', 'platform:dashboard:view', '查看平台看板', 'platform', 100),
('platform', 'platform:company:view',   '查看企业',     'platform', 110),
('platform', 'platform:company:create', '新建企业',     'platform', 111),
('platform', 'platform:company:edit',   '编辑企业',     'platform', 112),
('platform', 'platform:company:disable','停用/删除企业','platform', 113),
('platform', 'platform:user:view',      '查看平台账号', 'platform', 120),
('platform', 'platform:user:create',    '新建平台账号', 'platform', 121),
('platform', 'platform:user:edit',      '编辑平台账号', 'platform', 122),
('platform', 'platform:user:delete',    '删除平台账号', 'platform', 123),
('platform', 'platform:user:reset_pwd', '重置平台账号密码','platform', 124),
('platform', 'platform:role:view',      '查看平台角色', 'platform', 130),
('platform', 'platform:role:create',    '新建平台角色', 'platform', 131),
('platform', 'platform:role:edit',      '编辑平台角色', 'platform', 132),
('platform', 'platform:role:delete',    '删除平台角色', 'platform', 133),
('platform', 'platform:audit:view',     '查看操作日志', 'platform', 140),
('platform', 'platform:audit:export',   '导出操作日志', 'platform', 141),
('platform', 'platform:config:view',    '查看系统设置', 'platform', 150),
('platform', 'platform:config:edit',    '修改系统设置', 'platform', 151);

-- 3. 回填存量公司：为缺 company_admin 角色的公司补建角色
INSERT INTO role (tenant_id, name, code, data_scope, is_preset, status, is_deleted, created_at, updated_at)
SELECT c.id, '企业管理员', 'company_admin', 'all', 1, 1, 0, NOW(), NOW()
FROM company c
WHERE c.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM role r WHERE r.tenant_id = c.id AND r.code = 'company_admin');

-- 4. 把 company_admin 角色挂到该公司管理员账号（user_no 形如 U{companyId}-0001）
INSERT INTO user_role (user_id, role_id, created_at)
SELECT u.id, r.id, NOW()
FROM role r
JOIN `user` u ON u.tenant_id = r.tenant_id AND u.is_super_admin = 0 AND u.is_deleted = 0
WHERE r.code = 'company_admin'
  AND u.user_no LIKE 'U%-0001'
  AND NOT EXISTS (SELECT 1 FROM user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

-- 5. 给所有 company_admin 角色授予全部 company 层权限
INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, NOW()
FROM role r
JOIN permission p ON p.tier = 'company'
WHERE r.code = 'company_admin'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
```

- [ ] **Step 2: 写 H2 测试迁移**

`tengyei-backend/app/src/test/resources/db/migration/h2/V3__platform_rbac.sql`(与生产相同,但 `user` 表不加反引号；H2 兼容 ALTER/INSERT...SELECT/NOT EXISTS):
```sql
ALTER TABLE permission ADD COLUMN tier VARCHAR(20) NOT NULL DEFAULT 'company';

INSERT INTO permission (module, code, name, tier, sort_order) VALUES
('platform', 'platform:dashboard:view', '查看平台看板', 'platform', 100),
('platform', 'platform:company:view',   '查看企业',     'platform', 110),
('platform', 'platform:company:create', '新建企业',     'platform', 111),
('platform', 'platform:company:edit',   '编辑企业',     'platform', 112),
('platform', 'platform:company:disable','停用/删除企业','platform', 113),
('platform', 'platform:user:view',      '查看平台账号', 'platform', 120),
('platform', 'platform:user:create',    '新建平台账号', 'platform', 121),
('platform', 'platform:user:edit',      '编辑平台账号', 'platform', 122),
('platform', 'platform:user:delete',    '删除平台账号', 'platform', 123),
('platform', 'platform:user:reset_pwd', '重置平台账号密码','platform', 124),
('platform', 'platform:role:view',      '查看平台角色', 'platform', 130),
('platform', 'platform:role:create',    '新建平台角色', 'platform', 131),
('platform', 'platform:role:edit',      '编辑平台角色', 'platform', 132),
('platform', 'platform:role:delete',    '删除平台角色', 'platform', 133),
('platform', 'platform:audit:view',     '查看操作日志', 'platform', 140),
('platform', 'platform:audit:export',   '导出操作日志', 'platform', 141),
('platform', 'platform:config:view',    '查看系统设置', 'platform', 150),
('platform', 'platform:config:edit',    '修改系统设置', 'platform', 151);

INSERT INTO role (tenant_id, name, code, data_scope, is_preset, status, is_deleted, created_at, updated_at)
SELECT c.id, '企业管理员', 'company_admin', 'all', 1, 1, 0, NOW(), NOW()
FROM company c
WHERE c.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM role r WHERE r.tenant_id = c.id AND r.code = 'company_admin');

INSERT INTO user_role (user_id, role_id, created_at)
SELECT u.id, r.id, NOW()
FROM role r
JOIN user u ON u.tenant_id = r.tenant_id AND u.is_super_admin = 0 AND u.is_deleted = 0
WHERE r.code = 'company_admin'
  AND u.user_no LIKE 'U%-0001'
  AND NOT EXISTS (SELECT 1 FROM user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, NOW()
FROM role r
JOIN permission p ON p.tier = 'company'
WHERE r.code = 'company_admin'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
```

- [ ] **Step 3: 编译 + 启动测试上下文验证迁移可解析**

Run: `cd tengyei-backend; mvn -q -pl app -am test -Dtest=AuthIntegrationTest`
Expected: 测试通过(H2 上 V1→V2→V3 迁移成功,permission 表含 tier 列与平台权限)。

- [ ] **Step 4: 提交**
```bash
git add tengyei-backend/app/src/main/resources/db/migration/V3__platform_rbac.sql tengyei-backend/app/src/test/resources/db/migration/h2/V3__platform_rbac.sql
git commit -m "feat: V3 migration - permission tier column, platform permissions, company_admin backfill"
```

---

## Task 2：Permission.tier 字段 + 权限分组按 tier 过滤

**Files:**
- Modify: `tengyei-backend/core-rbac/src/main/java/com/tengyei/rbac/entity/Permission.java`
- Modify: `tengyei-backend/core-rbac/src/main/java/com/tengyei/rbac/service/PermissionService.java`

- [ ] **Step 1: Permission 实体加 tier 字段**

在 `Permission.java` 现有字段后加(与现有 lombok `@Data`/`@TableName` 风格一致):
```java
    private String tier;
```

- [ ] **Step 2: PermissionService.grouped() 按调用者层级过滤**

`PermissionService.java` 改为根据 `TenantContext` 判定层级:平台调用者(tenant==0)只返回 `tier='platform'`,公司调用者只返回 `tier='company'`。完整替换 `grouped()`:
```java
    public List<PermissionGroupVO> grouped() {
        String tier = com.tengyei.common.context.TenantContext.isSuperAdmin() ? "platform" : "company";
        List<Permission> all = permissionMapper.selectList(
            new LambdaQueryWrapper<Permission>()
                .eq(Permission::getStatus, 1)
                .eq(Permission::getTier, tier)
                .orderByAsc(Permission::getSortOrder));
        Map<String, List<PermissionGroupVO.Item>> byModule = new LinkedHashMap<>();
        for (Permission p : all) {
            byModule.computeIfAbsent(p.getModule(), k -> new ArrayList<>())
                    .add(PermissionGroupVO.Item.builder()
                            .id(p.getId()).code(p.getCode()).name(p.getName()).build());
        }
        List<PermissionGroupVO> groups = new ArrayList<>();
        byModule.forEach((module, items) ->
            groups.add(PermissionGroupVO.builder().module(module).permissions(items).build()));
        return groups;
    }
```
> 注:`permission` 在 `IGNORE_TABLES` 中,不受租户拦截器影响,故此处显式按 `tier` 过滤是安全且必要的。

- [ ] **Step 3: 编译**

Run: `cd tengyei-backend; mvn -q -pl core-rbac -am compile`
Expected: BUILD SUCCESS。

- [ ] **Step 4: 提交**
```bash
git add tengyei-backend/core-rbac/src/main/java/com/tengyei/rbac/entity/Permission.java tengyei-backend/core-rbac/src/main/java/com/tengyei/rbac/service/PermissionService.java
git commit -m "feat: add tier to Permission and filter grouped permissions by caller tier"
```

---

## Task 3：AuthService — 公司状态校验仅对公司层

**Files:**
- Modify: `tengyei-backend/core-auth/src/main/java/com/tengyei/auth/service/AuthService.java:94-103`

- [ ] **Step 1: 修改公司状态校验条件**

把现有的:
```java
        // Check company status for non-super-admin
        if (!isSuperAdmin) {
```
改为(仅对公司层 `tenant_id!=0` 校验;平台员工 tenant_id=0 跳过):
```java
        // Check company status only for company-tier users (tenant_id != 0)
        if (!isSuperAdmin && tenantId != null && tenantId != 0L) {
```
> 说明:`isSuperAdmin` 这里读的是 DB `is_super_admin` 列(仅内置 owner=1)。平台员工 `is_super_admin=0` 但 `tenant_id=0`,新增的 `tenantId != 0L` 条件让其跳过公司校验,从而能正常登录;其权限走 else 分支按平台角色查得。

- [ ] **Step 2: 编译**

Run: `cd tengyei-backend; mvn -q -pl core-auth -am compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: 提交**
```bash
git add tengyei-backend/core-auth/src/main/java/com/tengyei/auth/service/AuthService.java
git commit -m "feat: skip company-status login check for platform-tier users"
```

---

## Task 4：UserInfoController — 平台菜单按权限生成

**Files:**
- Modify: `tengyei-backend/app/src/main/java/com/tengyei/controller/UserInfoController.java:48-83`

- [ ] **Step 1: 按层级(tenant==0)分支并实现平台菜单**

把 routes 构建分支由"按 is_super_admin 列"改为"按 tenant_id==0"。替换第 48-49 行:
```java
        boolean isPlatformTier = tenantId != null && tenantId == 0L;
        List<UserInfoVO.RouteVO> routes = isPlatformTier
                ? buildPlatformRoutes(permissions) : buildCompanyRoutes(permissions);
```

删除旧的 `buildSuperAdminRoutes()`,新增 `buildPlatformRoutes(...)`(按平台权限生成;内置 owner 持 `*` → 全部可见):
```java
    private List<UserInfoVO.RouteVO> buildPlatformRoutes(List<String> permissions) {
        boolean all = permissions != null && permissions.contains("*");
        List<UserInfoVO.RouteVO> routes = new ArrayList<>();
        if (all || has(permissions, "platform:dashboard:view")) routes.add(route("/dashboard", "工作台"));
        if (all || has(permissions, "platform:company:view"))   routes.add(route("/admin/companies", "企业管理"));
        if (all || has(permissions, "platform:user:view"))      routes.add(route("/admin/users", "平台人员"));
        if (all || has(permissions, "platform:role:view"))      routes.add(route("/admin/roles", "平台角色"));
        if (all || has(permissions, "platform:audit:view"))     routes.add(route("/admin/audit-logs", "操作日志"));
        if (all || has(permissions, "platform:config:view"))    routes.add(route("/admin/system-config", "系统设置"));
        return routes;
    }

    private boolean has(List<String> permissions, String code) {
        return permissions != null && permissions.contains(code);
    }
```
> 保留 `buildCompanyRoutes`、`hasAny`、`route`、`toInt` 不动。`isSuperAdmin`(DB 列)字段仍用于 VO 的 `isSuperAdmin` 标志,不再决定菜单分支。

- [ ] **Step 2: 编译**

Run: `cd tengyei-backend; mvn -q -pl app -am compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: 提交**
```bash
git add tengyei-backend/app/src/main/java/com/tengyei/controller/UserInfoController.java
git commit -m "feat: build platform menu from permissions, branch by tenant tier"
```

---

## Task 5：CompanyService.create — 自动配齐 company_admin

**Files:**
- Modify: `tengyei-backend/core-company/src/main/java/com/tengyei/company/service/CompanyService.java:75-91`

- [ ] **Step 1: 写失败测试**

`tengyei-backend/app/src/test/java/com/tengyei/CompanyAdminSeedTest.java`:
```java
package com.tengyei;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
class CompanyAdminSeedTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired com.tengyei.company.service.CompanyService companyService;

    @Test
    void createCompany_seedsCompanyAdminRoleWithAllCompanyPermissions() {
        com.tengyei.company.dto.CompanyCreateDTO dto = new com.tengyei.company.dto.CompanyCreateDTO();
        dto.setFullName("种子测试企业");
        dto.setShortName("种子");
        dto.setAdminName("管理员");
        dto.setAdminUsername("seed_admin_" + System.nanoTime());
        dto.setAdminPassword("Admin@2026");
        Long companyId = companyService.create(dto);

        Long roleCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM role WHERE tenant_id = ? AND code = 'company_admin'",
            Long.class, companyId);
        assertEquals(1L, roleCount, "应自动建 company_admin 角色");

        Long permCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM role_permission rp JOIN role r ON r.id = rp.role_id " +
            "WHERE r.tenant_id = ? AND r.code = 'company_admin'", Long.class, companyId);
        Long companyPerms = jdbc.queryForObject(
            "SELECT COUNT(*) FROM permission WHERE tier = 'company'", Long.class);
        assertEquals(companyPerms, permCount, "company_admin 应拥有全部公司权限");

        Long urCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_role ur JOIN user u ON u.id = ur.user_id " +
            "WHERE u.tenant_id = ? AND u.is_super_admin = 0", Long.class, companyId);
        assertEquals(1L, urCount, "管理员应挂到 company_admin 角色");
    }
}
```
> 注:`CompanyCreateDTO` 的 setter 名以现有字段为准(`fullName/shortName/adminName/adminUsername/adminPassword`);如缺 `adminEmail/adminPhone` 非必填可不设。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd tengyei-backend; mvn -q -pl app -am test -Dtest=CompanyAdminSeedTest`
Expected: FAIL(`roleCount` 为 0,因 create 未建角色)。

- [ ] **Step 3: 实现 — create 末尾补建角色+挂接+授权**

在 `CompanyService.create()` 插入管理员用户后(现有第 78-88 行的 `jdbcTemplate.update("INSERT INTO ...user...")` 之后),返回前加:
```java
        // 取刚插入的管理员 userId
        Long adminUserId = jdbcTemplate.queryForObject(
            "SELECT id FROM `user` WHERE username = ? AND is_deleted = 0",
            Long.class, dto.getAdminUsername());

        // 建预设 company_admin 角色
        org.springframework.jdbc.support.GeneratedKeyHolder kh =
            new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            var ps = con.prepareStatement(
                "INSERT INTO role (tenant_id, name, code, data_scope, is_preset, status, " +
                "is_deleted, created_at, updated_at) VALUES (?, '企业管理员', 'company_admin', 'all', 1, 1, 0, NOW(), NOW())",
                java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, companyId);
            return ps;
        }, kh);
        Long roleId = kh.getKey().longValue();

        // 挂接管理员
        jdbcTemplate.update(
            "INSERT INTO user_role (user_id, role_id, created_at) VALUES (?, ?, NOW())",
            adminUserId, roleId);

        // 授予全部 company 层权限
        jdbcTemplate.update(
            "INSERT INTO role_permission (role_id, permission_id, created_at) " +
            "SELECT ?, id, NOW() FROM permission WHERE tier = 'company'", roleId);
```
> 方法已带 `@Transactional`,角色/挂接/授权与公司+管理员插入同事务。

- [ ] **Step 4: 运行测试确认通过**

Run: `cd tengyei-backend; mvn -q -pl app -am test -Dtest=CompanyAdminSeedTest`
Expected: PASS。

- [ ] **Step 5: 提交**
```bash
git add tengyei-backend/core-company/src/main/java/com/tengyei/company/service/CompanyService.java tengyei-backend/app/src/test/java/com/tengyei/CompanyAdminSeedTest.java
git commit -m "feat: auto-create company_admin role with all company permissions on company creation"
```

---

## Task 6：CompanyController — 守卫开放给平台 company 权限

**Files:**
- Modify: `tengyei-backend/core-company/src/main/java/com/tengyei/company/controller/CompanyController.java`

- [ ] **Step 1: 逐端点改守卫**

把各方法的 `@PreAuthorize` 由 `hasAuthority('PERM_*')` 改为 `hasAnyAuthority(...)`,让内置 owner 与持平台权限的平台员工均可访问:
- `page`、`detail`:`@PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:company:view')")`
- `create`:`@PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:company:create')")`
- `update`:`@PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:company:edit')")`
- `changeStatus`、`delete`:`@PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:company:disable')")`

- [ ] **Step 2: 编译**

Run: `cd tengyei-backend; mvn -q -pl core-company -am compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: 提交**
```bash
git add tengyei-backend/core-company/src/main/java/com/tengyei/company/controller/CompanyController.java
git commit -m "feat: open company endpoints to platform company permissions"
```

---

## Task 7：平台 RBAC 服务 + 角色 DTO(tenant_id=0 + owner 保护)

**Files:**
- Create: `tengyei-backend/app/src/main/java/com/tengyei/platform/dto/PlatformRoleDTO.java`
- Create: `tengyei-backend/app/src/main/java/com/tengyei/platform/dto/PlatformRoleVO.java`
- Create: `tengyei-backend/app/src/main/java/com/tengyei/platform/service/PlatformRbacService.java`

- [ ] **Step 1: PlatformRoleDTO**
```java
package com.tengyei.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PlatformRoleDTO {
    @NotBlank(message = "角色名称不能为空")
    private String name;
    @NotBlank(message = "角色编码不能为空")
    private String code;
    private String description;
}
```

- [ ] **Step 2: PlatformRoleVO**
```java
package com.tengyei.platform.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlatformRoleVO {
    private Long id;
    private String name;
    private String code;
    private String description;
    private Integer isPreset;
    private Integer status;
}
```

- [ ] **Step 3: PlatformRbacService(角色部分)**

所有查询/改动**显式 `tenant_id=0`**;`super_admin` 角色受保护。
```java
package com.tengyei.platform.service;

import com.tengyei.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tengyei.platform.dto.PlatformRoleDTO;
import com.tengyei.platform.dto.PlatformRoleVO;

import java.sql.Statement;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlatformRbacService {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    /* ===================== 平台角色 ===================== */

    public List<PlatformRoleVO> listRoles() {
        return jdbc.query(
            "SELECT id, name, code, description, is_preset, status FROM role " +
            "WHERE tenant_id = 0 AND is_deleted = 0 ORDER BY id",
            (rs, i) -> PlatformRoleVO.builder()
                .id(rs.getLong("id")).name(rs.getString("name")).code(rs.getString("code"))
                .description(rs.getString("description")).isPreset(rs.getInt("is_preset"))
                .status(rs.getInt("status")).build());
    }

    public Long createRole(PlatformRoleDTO dto) {
        Long dup = jdbc.queryForObject(
            "SELECT COUNT(*) FROM role WHERE tenant_id = 0 AND code = ?", Long.class, dto.getCode());
        if (dup != null && dup > 0) throw new BusinessException(409, "角色编码已存在");
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                "INSERT INTO role (tenant_id, name, code, description, data_scope, is_preset, status, " +
                "is_deleted, created_at, updated_at) VALUES (0, ?, ?, ?, 'all', 0, 1, 0, NOW(), NOW())",
                Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, dto.getName());
            ps.setString(2, dto.getCode());
            ps.setString(3, dto.getDescription());
            return ps;
        }, kh);
        return kh.getKey().longValue();
    }

    public void updateRole(Long id, PlatformRoleDTO dto) {
        requirePlatformRole(id);
        jdbc.update("UPDATE role SET name = ?, description = ? WHERE id = ? AND tenant_id = 0",
            dto.getName(), dto.getDescription(), id);
    }

    public void deleteRole(Long id) {
        var r = requirePlatformRole(id);
        if ("super_admin".equals(r)) throw new BusinessException(409, "内置角色不可删除");
        Long inUse = jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_role WHERE role_id = ?", Long.class, id);
        if (inUse != null && inUse > 0) throw new BusinessException(409, "角色已分配账号，无法删除");
        jdbc.update("UPDATE role SET is_deleted = 1 WHERE id = ? AND tenant_id = 0", id);
    }

    public List<Long> rolePermissionIds(Long roleId) {
        requirePlatformRole(roleId);
        return jdbc.queryForList(
            "SELECT permission_id FROM role_permission WHERE role_id = ?", Long.class, roleId);
    }

    @Transactional
    public void assignRolePermissions(Long roleId, List<Long> permissionIds) {
        String code = requirePlatformRole(roleId);
        if ("super_admin".equals(code)) throw new BusinessException(409, "内置角色权限不可修改");
        jdbc.update("DELETE FROM role_permission WHERE role_id = ?", roleId);
        if (permissionIds != null) {
            for (Long pid : permissionIds) {
                // 只允许授予 platform 层权限
                Long ok = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM permission WHERE id = ? AND tier = 'platform'", Long.class, pid);
                if (ok != null && ok > 0) {
                    jdbc.update("INSERT INTO role_permission (role_id, permission_id, created_at) VALUES (?, ?, NOW())",
                        roleId, pid);
                }
            }
        }
    }

    /** 校验角色存在且属平台层，返回其 code */
    private String requirePlatformRole(Long id) {
        List<String> codes = jdbc.queryForList(
            "SELECT code FROM role WHERE id = ? AND tenant_id = 0 AND is_deleted = 0", String.class, id);
        if (codes.isEmpty()) throw new BusinessException(404, "平台角色不存在");
        return codes.get(0);
    }
}
```

- [ ] **Step 4: 编译**

Run: `cd tengyei-backend; mvn -q -pl app -am compile`
Expected: BUILD SUCCESS。

- [ ] **Step 5: 提交**
```bash
git add tengyei-backend/app/src/main/java/com/tengyei/platform/
git commit -m "feat: platform RBAC service for roles, scoped to tenant_id=0 with super_admin protection"
```

---

## Task 8：PlatformRoleController + 平台权限分组

**Files:**
- Create: `tengyei-backend/app/src/main/java/com/tengyei/platform/controller/PlatformRoleController.java`

- [ ] **Step 1: PlatformRoleController**

复用 `PermissionService.grouped()`(平台调用者 tenant=0 → 自动返回 platform 权限),角色 CRUD 走 `PlatformRbacService`。
```java
package com.tengyei.platform.controller;

import com.tengyei.common.annotation.Auditable;
import com.tengyei.common.response.Result;
import com.tengyei.platform.dto.PlatformRoleDTO;
import com.tengyei.platform.dto.PlatformRoleVO;
import com.tengyei.platform.service.PlatformRbacService;
import com.tengyei.rbac.dto.PermissionGroupVO;
import com.tengyei.rbac.service.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/roles")
@RequiredArgsConstructor
public class PlatformRoleController {

    private final PlatformRbacService service;
    private final PermissionService permissionService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:role:view')")
    public Result<List<PlatformRoleVO>> list() {
        return Result.ok(service.listRoles());
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:role:view')")
    public Result<List<PermissionGroupVO>> permissions() {
        return Result.ok(permissionService.grouped()); // 平台调用者 → platform 权限
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:role:create')")
    @Auditable(module = "平台角色", actionType = "CREATE", description = "新建平台角色")
    public Result<Map<String, Long>> create(@Valid @RequestBody PlatformRoleDTO dto) {
        return Result.ok(Map.of("id", service.createRole(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:role:edit')")
    @Auditable(module = "平台角色", actionType = "UPDATE", description = "编辑平台角色")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PlatformRoleDTO dto) {
        service.updateRole(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:role:delete')")
    @Auditable(module = "平台角色", actionType = "DELETE", description = "删除平台角色")
    public Result<Void> delete(@PathVariable Long id) {
        service.deleteRole(id);
        return Result.ok();
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:role:view')")
    public Result<List<Long>> rolePermissions(@PathVariable Long id) {
        return Result.ok(service.rolePermissionIds(id));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:role:edit')")
    @Auditable(module = "平台角色", actionType = "UPDATE", description = "配置平台角色权限")
    public Result<Void> assignPermissions(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        service.assignRolePermissions(id, body.get("permissionIds"));
        return Result.ok();
    }
}
```

- [ ] **Step 2: 编译**

Run: `cd tengyei-backend; mvn -q -pl app -am compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: 提交**
```bash
git add tengyei-backend/app/src/main/java/com/tengyei/platform/controller/PlatformRoleController.java
git commit -m "feat: platform role controller with platform-tier permission matrix"
```

---

## Task 9：平台账号 服务 + Controller(tenant_id=0 + owner 保护)

**Files:**
- Create: `tengyei-backend/app/src/main/java/com/tengyei/platform/dto/PlatformUserDTO.java`
- Create: `tengyei-backend/app/src/main/java/com/tengyei/platform/dto/PlatformUserVO.java`
- Modify: `tengyei-backend/app/src/main/java/com/tengyei/platform/service/PlatformRbacService.java`
- Create: `tengyei-backend/app/src/main/java/com/tengyei/platform/controller/PlatformUserController.java`

- [ ] **Step 1: PlatformUserDTO**
```java
package com.tengyei.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class PlatformUserDTO {
    @NotBlank(message = "账号不能为空")
    private String username;
    @NotBlank(message = "姓名不能为空")
    private String realName;
    private String phone;
    private String email;
    private String password;       // 仅新建必填
    private List<Long> roleIds;
}
```

- [ ] **Step 2: PlatformUserVO**
```java
package com.tengyei.platform.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class PlatformUserVO {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private Integer status;
    private Integer isSuperAdmin;
    private List<String> roleNames;
}
```

- [ ] **Step 3: PlatformRbacService 加账号方法**

追加到 `PlatformRbacService`(平台账号 = `tenant_id=0`;内置 owner `is_super_admin=1` 受保护):
```java
    /* ===================== 平台账号 ===================== */

    public List<PlatformUserVO> listUsers(String keyword) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, username, real_name, phone, email, status, is_super_admin FROM `user` " +
            "WHERE tenant_id = 0 AND is_deleted = 0");
        List<Object> params = new java.util.ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (username LIKE ? OR real_name LIKE ? OR phone LIKE ?)");
            String like = "%" + keyword + "%";
            params.add(like); params.add(like); params.add(like);
        }
        sql.append(" ORDER BY id");
        return jdbc.query(sql.toString(), params.toArray(), (rs, i) -> {
            long uid = rs.getLong("id");
            List<String> roleNames = jdbc.queryForList(
                "SELECT r.name FROM role r JOIN user_role ur ON ur.role_id = r.id WHERE ur.user_id = ?",
                String.class, uid);
            return PlatformUserVO.builder()
                .id(uid).username(rs.getString("username")).realName(rs.getString("real_name"))
                .phone(rs.getString("phone")).email(rs.getString("email")).status(rs.getInt("status"))
                .isSuperAdmin(rs.getInt("is_super_admin")).roleNames(roleNames).build();
        });
    }

    @Transactional
    public Long createUser(PlatformUserDTO dto) {
        if (dto.getPassword() == null || dto.getPassword().isBlank())
            throw new BusinessException(422, "初始密码不能为空");
        Long dup = jdbc.queryForObject(
            "SELECT COUNT(*) FROM `user` WHERE username = ? AND is_deleted = 0", Long.class, dto.getUsername());
        if (dup != null && dup > 0) throw new BusinessException(409, "账号已存在");
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                "INSERT INTO `user` (tenant_id, user_no, username, password, real_name, phone, email, " +
                "is_super_admin, status, pwd_reset_required, login_fail_count, is_deleted, created_at, updated_at) " +
                "VALUES (0, ?, ?, ?, ?, ?, ?, 0, 1, 1, 0, 0, NOW(), NOW())",
                Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, "P-" + System.nanoTime());
            ps.setString(2, dto.getUsername());
            ps.setString(3, passwordEncoder.encode(dto.getPassword()));
            ps.setString(4, dto.getRealName());
            ps.setString(5, dto.getPhone());
            ps.setString(6, dto.getEmail());
            return ps;
        }, kh);
        Long userId = kh.getKey().longValue();
        replaceUserRoles(userId, dto.getRoleIds());
        return userId;
    }

    @Transactional
    public void updateUser(Long id, PlatformUserDTO dto) {
        requirePlatformUser(id);
        jdbc.update("UPDATE `user` SET real_name = ?, phone = ?, email = ? WHERE id = ? AND tenant_id = 0",
            dto.getRealName(), dto.getPhone(), dto.getEmail(), id);
        if (dto.getRoleIds() != null) replaceUserRoles(id, dto.getRoleIds());
    }

    public void deleteUser(Long id) {
        requireNonOwner(id, "内置账号不可删除");
        jdbc.update("UPDATE `user` SET is_deleted = 1 WHERE id = ? AND tenant_id = 0", id);
    }

    public void changeUserStatus(Long id, Integer status) {
        requireNonOwner(id, "内置账号不可停用");
        if (status == null || (status != 0 && status != 1)) throw new BusinessException(422, "状态值无效");
        jdbc.update("UPDATE `user` SET status = ? WHERE id = ? AND tenant_id = 0", status, id);
    }

    public void resetUserPassword(Long id, String password) {
        requirePlatformUser(id);
        if (password == null || password.length() < 6) throw new BusinessException(422, "密码至少 6 位");
        jdbc.update("UPDATE `user` SET password = ? WHERE id = ? AND tenant_id = 0",
            passwordEncoder.encode(password), id);
    }

    @Transactional
    public void assignUserRoles(Long id, List<Long> roleIds) {
        requireNonOwner(id, "内置账号角色不可修改");
        replaceUserRoles(id, roleIds);
    }

    private void replaceUserRoles(Long userId, List<Long> roleIds) {
        jdbc.update("DELETE FROM user_role WHERE user_id = ?", userId);
        if (roleIds != null) {
            for (Long rid : roleIds) {
                Long ok = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM role WHERE id = ? AND tenant_id = 0 AND is_deleted = 0",
                    Long.class, rid);
                if (ok != null && ok > 0) {
                    jdbc.update("INSERT INTO user_role (user_id, role_id, created_at) VALUES (?, ?, NOW())",
                        userId, rid);
                }
            }
        }
    }

    private void requirePlatformUser(Long id) {
        Long c = jdbc.queryForObject(
            "SELECT COUNT(*) FROM `user` WHERE id = ? AND tenant_id = 0 AND is_deleted = 0", Long.class, id);
        if (c == null || c == 0) throw new BusinessException(404, "平台账号不存在");
    }

    private void requireNonOwner(Long id, String msg) {
        requirePlatformUser(id);
        Integer owner = jdbc.queryForObject(
            "SELECT is_super_admin FROM `user` WHERE id = ?", Integer.class, id);
        if (owner != null && owner == 1) throw new BusinessException(409, msg);
    }
```
> H2 测试中 `` `user` `` 反引号不被支持。但本服务在生产(MySQL)运行;**测试覆盖见 Task 9 Step 5 使用 MockMvc 走 HTTP**,H2 下 JdbcTemplate 同样需无反引号——**实现时 `user` 表一律不加反引号**(H2 与 MySQL 均接受,现有 `OrgTestSupport`/`UserInfoController` 已用无反引号写法)。请将上面所有 `` `user` `` 改为 `user`。

- [ ] **Step 4: PlatformUserController**
```java
package com.tengyei.platform.controller;

import com.tengyei.common.annotation.Auditable;
import com.tengyei.common.response.Result;
import com.tengyei.platform.dto.PlatformUserDTO;
import com.tengyei.platform.dto.PlatformUserVO;
import com.tengyei.platform.service.PlatformRbacService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/users")
@RequiredArgsConstructor
public class PlatformUserController {

    private final PlatformRbacService service;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:user:view')")
    public Result<List<PlatformUserVO>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(service.listUsers(keyword));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:user:create')")
    @Auditable(module = "平台账号", actionType = "CREATE", description = "新建平台账号")
    public Result<Map<String, Long>> create(@Valid @RequestBody PlatformUserDTO dto) {
        return Result.ok(Map.of("id", service.createUser(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:user:edit')")
    @Auditable(module = "平台账号", actionType = "UPDATE", description = "编辑平台账号")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PlatformUserDTO dto) {
        service.updateUser(id, dto);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:user:edit')")
    @Auditable(module = "平台账号", actionType = "UPDATE", description = "变更平台账号状态")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        service.changeUserStatus(id, body.get("status"));
        return Result.ok();
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:user:edit')")
    @Auditable(module = "平台账号", actionType = "UPDATE", description = "分配平台账号角色")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        service.assignUserRoles(id, body.get("roleIds"));
        return Result.ok();
    }

    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:user:reset_pwd')")
    @Auditable(module = "平台账号", actionType = "UPDATE", description = "重置平台账号密码")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        service.resetUserPassword(id, body.get("password"));
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_*','PERM_platform:user:delete')")
    @Auditable(module = "平台账号", actionType = "DELETE", description = "删除平台账号")
    public Result<Void> delete(@PathVariable Long id) {
        service.deleteUser(id);
        return Result.ok();
    }
}
```

- [ ] **Step 5: 写集成测试(owner 保护 + 隔离)**

`tengyei-backend/app/src/test/java/com/tengyei/PlatformRbacTest.java`:
```java
package com.tengyei;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformRbacTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;

    /** superadmin(is_super_admin=1, tenant 0)登录 token */
    private String ownerToken() throws Exception {
        return OrgTestSupport.login(mockMvc, om, "superadmin");
    }

    @Test
    void platformUserList_returnsOnlyTenantZero() throws Exception {
        // 先建一个公司(其用户 tenant!=0)
        OrgTestSupport.seedCompanyAdmin(jdbc);
        String token = ownerToken();
        mockMvc.perform(get("/api/v1/platform/users").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            // 平台账号里不应出现公司管理员(企业管理员)
            .andExpect(jsonPath("$.data[?(@.realName=='企业管理员')]").isEmpty());
    }

    @Test
    void cannotDeleteOwnerAccount() throws Exception {
        String token = ownerToken();
        Long ownerId = jdbc.queryForObject(
            "SELECT id FROM user WHERE username = 'superadmin'", Long.class);
        mockMvc.perform(delete("/api/v1/platform/users/" + ownerId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().is4xxClientError());
    }
}
```
> 前提已满足:h2 测试迁移 `V2__init_data.sql` 已种 `superadmin`(tenant_id=0, is_super_admin=1, 密码 `Admin@2026`),`OrgTestSupport.login` 即用该密码登录,无需额外插入。

- [ ] **Step 6: 运行测试**

Run: `cd tengyei-backend; mvn -q -pl app -am test -Dtest=PlatformRbacTest`
Expected: PASS。

- [ ] **Step 7: 提交**
```bash
git add tengyei-backend/app/src/main/java/com/tengyei/platform/ tengyei-backend/app/src/test/java/com/tengyei/PlatformRbacTest.java
git commit -m "feat: platform user management endpoints, tenant_id=0 scoped with owner protection"
```

---

## Task 10：OrgTestSupport 只授 company 权限

**Files:**
- Modify: `tengyei-backend/app/src/test/java/com/tengyei/OrgTestSupport.java:79-81`

- [ ] **Step 1: 改授权 SQL**

把:
```java
        jdbc.update(
            "INSERT INTO role_permission (role_id, permission_id, created_at) " +
            "SELECT ?, id, NOW() FROM permission", roleId);
```
改为(只授公司层权限,避免测试里公司管理员误得平台权限):
```java
        jdbc.update(
            "INSERT INTO role_permission (role_id, permission_id, created_at) " +
            "SELECT ?, id, NOW() FROM permission WHERE tier = 'company'", roleId);
```

- [ ] **Step 2: 跑现有公司侧测试确保未回归**

Run: `cd tengyei-backend; mvn -q -pl app -am test -Dtest=UserControllerTest,RoleControllerTest,DeptControllerTest,BranchControllerTest`
Expected: PASS(公司管理员仍持全部 company 权限,够用)。

- [ ] **Step 3: 提交**
```bash
git add tengyei-backend/app/src/test/java/com/tengyei/OrgTestSupport.java
git commit -m "test: seed company admin with company-tier permissions only"
```

---

## Task 11：前端 — 平台 API + 类型

**Files:**
- Create: `tengyei-frontend/src/types/platform.ts`
- Create: `tengyei-frontend/src/api/platform.ts`

- [ ] **Step 1: 类型定义**

`tengyei-frontend/src/types/platform.ts`:
```typescript
export interface PlatformRoleVO {
  id: number
  name: string
  code: string
  description?: string
  isPreset: number
  status: number
}

export interface PlatformRoleDTO {
  name: string
  code: string
  description?: string
}

export interface PlatformUserVO {
  id: number
  username: string
  realName: string
  phone?: string
  email?: string
  status: number
  isSuperAdmin: number
  roleNames: string[]
}

export interface PlatformUserDTO {
  username: string
  realName: string
  phone?: string
  email?: string
  password?: string
  roleIds?: number[]
}

export interface PermissionGroupVO {
  module: string
  permissions: { id: number; code: string; name: string }[]
}
```

- [ ] **Step 2: API**

`tengyei-frontend/src/api/platform.ts`(参照 `src/api/rbac.ts`/`src/api/user.ts` 的 `request` 用法):
```typescript
import request from './request'
import type {
  PlatformRoleVO, PlatformRoleDTO, PlatformUserVO, PlatformUserDTO, PermissionGroupVO,
} from '@/types/platform'

export const platformRoleApi = {
  list: () => request.get<never, PlatformRoleVO[]>('/v1/platform/roles'),
  permissions: () => request.get<never, PermissionGroupVO[]>('/v1/platform/roles/permissions'),
  create: (data: PlatformRoleDTO) => request.post<never, { id: number }>('/v1/platform/roles', data),
  update: (id: number, data: PlatformRoleDTO) => request.put<never, void>(`/v1/platform/roles/${id}`, data),
  remove: (id: number) => request.delete<never, void>(`/v1/platform/roles/${id}`),
  permissionIds: (id: number) => request.get<never, number[]>(`/v1/platform/roles/${id}/permissions`),
  assignPermissions: (id: number, permissionIds: number[]) =>
    request.put<never, void>(`/v1/platform/roles/${id}/permissions`, { permissionIds }),
}

export const platformUserApi = {
  list: (params: { keyword?: string }) =>
    request.get<never, PlatformUserVO[]>('/v1/platform/users', { params }),
  create: (data: PlatformUserDTO) => request.post<never, { id: number }>('/v1/platform/users', data),
  update: (id: number, data: PlatformUserDTO) => request.put<never, void>(`/v1/platform/users/${id}`, data),
  changeStatus: (id: number, status: number) =>
    request.put<never, void>(`/v1/platform/users/${id}/status`, { status }),
  assignRoles: (id: number, roleIds: number[]) =>
    request.put<never, void>(`/v1/platform/users/${id}/roles`, { roleIds }),
  resetPassword: (id: number, password: string) =>
    request.put<never, void>(`/v1/platform/users/${id}/reset-password`, { password }),
  remove: (id: number) => request.delete<never, void>(`/v1/platform/users/${id}`),
}
```

- [ ] **Step 3: 类型检查**

Run: `cd tengyei-frontend; npx vue-tsc --noEmit`
Expected: 无错误。

- [ ] **Step 4: 提交**
```bash
git add tengyei-frontend/src/types/platform.ts tengyei-frontend/src/api/platform.ts
git commit -m "feat: platform role and user API client + types"
```

---

## Task 12：前端 — 路由 + 侧边栏

**Files:**
- Modify: `tengyei-frontend/src/router/index.ts:48`
- Modify: `tengyei-frontend/src/layout/AppSidebar.vue:12-20`

- [ ] **Step 1: 加路由**

在 `router/index.ts` 的 `admin/system-config` 路由(第 49-54 行附近)之后、`company/org` 之前插入:
```typescript
        {
          path: 'admin/users',
          name: 'PlatformUsers',
          component: () => import('@/views/platform/PlatformUserListView.vue'),
          meta: { title: '平台人员' },
        },
        {
          path: 'admin/roles',
          name: 'PlatformRoles',
          component: () => import('@/views/platform/PlatformRoleView.vue'),
          meta: { title: '平台角色' },
        },
```

- [ ] **Step 2: 侧边栏图标映射补两条**

`AppSidebar.vue` 的 `iconMap`(第 12-20 行)加:
```typescript
  '/admin/users': User,
  '/admin/roles': Lock,
```
(`User`、`Lock` 已从 `@element-plus/icons-vue` import。)

- [ ] **Step 3: 类型检查(此时视图文件尚未建,允许 import 报错前先建占位)**

> 本步骤依赖 Task 13/14 的视图文件。建议与 Task 13、14 连续完成后统一 `npx vue-tsc --noEmit`。此处仅提交路由+侧边栏改动前,先创建两个最小占位组件以通过类型检查:
> `PlatformUserListView.vue` / `PlatformRoleView.vue` 先写 `<template><div /></template><script setup lang="ts"></script>`,Task 13/14 再填充。

- [ ] **Step 4: 提交**
```bash
git add tengyei-frontend/src/router/index.ts tengyei-frontend/src/layout/AppSidebar.vue
git commit -m "feat: add platform users/roles routes and sidebar icons"
```

---

## Task 13：前端 — 平台角色页

**Files:**
- Create: `tengyei-frontend/src/views/platform/PlatformRoleView.vue`

- [ ] **Step 1: 实现(复用 `RoleView.vue` 的左角色列表 + 右权限矩阵范式,数据走 platformRoleApi)**

完整文件:
```vue
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type CheckboxValueType } from 'element-plus'
import { platformRoleApi } from '@/api/platform'
import type { PlatformRoleVO, PlatformRoleDTO, PermissionGroupVO } from '@/types/platform'

const roles = ref<PlatformRoleVO[]>([])
const activeRole = ref<PlatformRoleVO | null>(null)
const permGroups = ref<PermissionGroupVO[]>([])
const checkedIds = ref<number[]>([])
const permLoading = ref(false)
const saving = ref(false)

async function fetchRoles() { roles.value = await platformRoleApi.list() }
async function fetchPermGroups() { permGroups.value = await platformRoleApi.permissions() }

function selectRole(role: PlatformRoleVO) {
  activeRole.value = role
  loadPermissions(role.id)
}
async function loadPermissions(roleId: number) {
  permLoading.value = true
  try { checkedIds.value = await platformRoleApi.permissionIds(roleId) }
  finally { permLoading.value = false }
}
function allIdsInGroup(g: PermissionGroupVO) { return g.permissions.map((p) => p.id) }
function groupChecked(g: PermissionGroupVO) { return allIdsInGroup(g).every((id) => checkedIds.value.includes(id)) }
function groupIndeterminate(g: PermissionGroupVO) {
  const ids = allIdsInGroup(g)
  const n = ids.filter((id) => checkedIds.value.includes(id)).length
  return n > 0 && n < ids.length
}
function toggleGroup(g: PermissionGroupVO, checked: boolean) {
  const ids = allIdsInGroup(g)
  checkedIds.value = checked
    ? [...new Set([...checkedIds.value, ...ids])]
    : checkedIds.value.filter((id) => !ids.includes(id))
}
async function savePermissions() {
  if (!activeRole.value) return
  saving.value = true
  try {
    await platformRoleApi.assignPermissions(activeRole.value.id, checkedIds.value)
    ElMessage.success('权限已保存')
  } finally { saving.value = false }
}

const roleDialog = ref(false)
const roleFormRef = ref<FormInstance>()
const editingId = ref<number | null>(null)
const roleForm = reactive<PlatformRoleDTO>({ name: '', code: '', description: '' })
const roleRules: FormRules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
}
function openCreate() {
  editingId.value = null
  Object.assign(roleForm, { name: '', code: '', description: '' })
  roleDialog.value = true
}
function openEdit(role: PlatformRoleVO) {
  editingId.value = role.id
  Object.assign(roleForm, { name: role.name, code: role.code, description: role.description })
  roleDialog.value = true
}
async function submitRole() {
  if (!roleFormRef.value) return
  await roleFormRef.value.validate()
  if (editingId.value) {
    await platformRoleApi.update(editingId.value, { ...roleForm })
    ElMessage.success('角色已更新')
  } else {
    await platformRoleApi.create({ ...roleForm })
    ElMessage.success('角色已创建')
  }
  roleDialog.value = false
  fetchRoles()
}
async function deleteRole(role: PlatformRoleVO) {
  await ElMessageBox.confirm(`确认删除平台角色「${role.name}」？`, '提示', { type: 'warning' })
  await platformRoleApi.remove(role.id)
  ElMessage.success('已删除')
  if (activeRole.value?.id === role.id) { activeRole.value = null; checkedIds.value = [] }
  fetchRoles()
}

const noActiveRole = computed(() => !activeRole.value)
onMounted(() => { fetchRoles(); fetchPermGroups() })
</script>

<template>
  <div class="role-view">
    <el-card class="role-pane" shadow="never">
      <template #header>
        <div class="pane-head">
          <span>平台角色</span>
          <el-button link type="primary" @click="openCreate">新增角色</el-button>
        </div>
      </template>
      <div
        v-for="role in roles" :key="role.id"
        :class="['role-item', { active: activeRole?.id === role.id }]"
        @click="selectRole(role)"
      >
        <div class="role-name">{{ role.name }}</div>
        <div class="role-meta">{{ role.code }}</div>
        <div class="role-actions">
          <el-button link type="primary" size="small" @click.stop="openEdit(role)">编辑</el-button>
          <el-button
            v-if="role.code !== 'super_admin'"
            link type="danger" size="small" @click.stop="deleteRole(role)"
          >删除</el-button>
        </div>
      </div>
    </el-card>

    <el-card class="perm-pane" shadow="never">
      <template #header>
        <div class="pane-head">
          <span>权限配置{{ activeRole ? ` — ${activeRole.name}` : '' }}</span>
          <el-button type="primary" size="small" :disabled="noActiveRole" :loading="saving" @click="savePermissions">保存</el-button>
        </div>
      </template>
      <el-empty v-if="noActiveRole" description="请先选择左侧角色" />
      <div v-else v-loading="permLoading">
        <div v-for="group in permGroups" :key="group.module" class="perm-group">
          <div class="group-header">
            <el-checkbox
              :model-value="groupChecked(group)"
              :indeterminate="groupIndeterminate(group)"
              @change="(v: CheckboxValueType) => toggleGroup(group, v as boolean)"
            ><strong>{{ group.module }}</strong></el-checkbox>
          </div>
          <el-checkbox-group v-model="checkedIds" class="perm-items">
            <el-checkbox v-for="perm in group.permissions" :key="perm.id" :value="perm.id">{{ perm.name }}</el-checkbox>
          </el-checkbox-group>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="roleDialog" :title="editingId ? '编辑平台角色' : '新增平台角色'" width="460px">
      <el-form ref="roleFormRef" :model="roleForm" :rules="roleRules" label-width="90px">
        <el-form-item label="角色名称" prop="name"><el-input v-model="roleForm.name" /></el-form-item>
        <el-form-item label="角色编码" prop="code"><el-input v-model="roleForm.code" :disabled="!!editingId" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="roleForm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialog = false">取消</el-button>
        <el-button type="primary" @click="submitRole">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.role-view { display: grid; grid-template-columns: 300px 1fr; gap: 16px; align-items: start; }
.role-pane, .perm-pane { border-radius: 10px; }
.pane-head { display: flex; justify-content: space-between; align-items: center; }
.role-item { padding: 10px 12px; border-radius: 6px; cursor: pointer; margin-bottom: 4px; border: 1px solid transparent; transition: background .15s; }
.role-item:hover { background: #f5f7fa; }
.role-item.active { background: #ecf5ff; border-color: #b3d8ff; }
.role-name { font-weight: 600; font-size: 14px; }
.role-meta { font-size: 12px; color: #909399; }
.role-actions { margin-top: 4px; }
.perm-group { margin-bottom: 20px; }
.group-header { margin-bottom: 8px; padding-bottom: 6px; border-bottom: 1px solid #ebeef5; }
.perm-items { display: flex; flex-wrap: wrap; gap: 8px 0; padding-left: 16px; }
.perm-items .el-checkbox { width: 200px; }
</style>
```

- [ ] **Step 2: 类型检查**

Run: `cd tengyei-frontend; npx vue-tsc --noEmit`
Expected: 无错误。

- [ ] **Step 3: 提交**
```bash
git add tengyei-frontend/src/views/platform/PlatformRoleView.vue
git commit -m "feat: platform role management view with permission matrix"
```

---

## Task 14：前端 — 平台人员页

**Files:**
- Create: `tengyei-frontend/src/views/platform/PlatformUserListView.vue`

- [ ] **Step 1: 实现(复用 `UserListView.vue` 范式,去掉部门/分公司,角色来自 platformRoleApi)**

完整文件:
```vue
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { platformUserApi, platformRoleApi } from '@/api/platform'
import type { PlatformUserVO, PlatformUserDTO, PlatformRoleVO } from '@/types/platform'

const loading = ref(false)
const list = ref<PlatformUserVO[]>([])
const query = reactive({ keyword: '' })
const roles = ref<PlatformRoleVO[]>([])

async function fetchList() {
  loading.value = true
  try { list.value = await platformUserApi.list({ keyword: query.keyword || undefined }) }
  finally { loading.value = false }
}
async function fetchRoles() { roles.value = await platformRoleApi.list() }
function onSearch() { fetchList() }

const dialog = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)
const form = reactive<PlatformUserDTO>({ username: '', realName: '', phone: '', email: '', password: '', roleIds: [] })
const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { username: '', realName: '', phone: '', email: '', password: '', roleIds: [] })
  dialog.value = true
}
function openEdit(row: PlatformUserVO) {
  editingId.value = row.id
  Object.assign(form, { username: row.username, realName: row.realName, phone: row.phone ?? '', email: row.email ?? '', password: '', roleIds: [] })
  dialog.value = true
}
async function submit() {
  if (!formRef.value) return
  await formRef.value.validate()
  if (editingId.value) {
    await platformUserApi.update(editingId.value, { ...form })
    ElMessage.success('已更新')
  } else {
    await platformUserApi.create({ ...form })
    ElMessage.success('已创建')
  }
  dialog.value = false
  fetchList()
}
async function toggleStatus(row: PlatformUserVO) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 0 ? '停用' : '启用'
  await ElMessageBox.confirm(`确认${action}账号「${row.realName}」？`, '提示', { type: 'warning' })
  await platformUserApi.changeStatus(row.id, next)
  ElMessage.success(`已${action}`)
  fetchList()
}
async function resetPassword(row: PlatformUserVO) {
  try {
    const { value } = await ElMessageBox.prompt(`为账号「${row.realName}」设置新密码`, '重置密码', {
      confirmButtonText: '确定', cancelButtonText: '取消',
      inputPattern: /.{6,}/, inputErrorMessage: '密码至少 6 位', inputType: 'password',
    })
    await platformUserApi.resetPassword(row.id, value)
    ElMessage.success('密码已重置')
  } catch { /* cancelled */ }
}
async function remove(row: PlatformUserVO) {
  await ElMessageBox.confirm(`确认删除账号「${row.realName}」？`, '提示', { type: 'warning' })
  await platformUserApi.remove(row.id)
  ElMessage.success('已删除')
  fetchList()
}

onMounted(() => { fetchList(); fetchRoles() })
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="搜索账号/姓名/手机" clearable style="width: 220px"
        @keyup.enter="onSearch" @clear="onSearch" />
      <el-button type="primary" @click="onSearch">搜索</el-button>
      <el-button type="primary" @click="openCreate">新增平台账号</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="realName" label="姓名" width="140" />
      <el-table-column prop="username" label="账号" width="160" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column label="角色" min-width="160">
        <template #default="{ row }">
          <el-tag v-for="n in (row as PlatformUserVO).roleNames" :key="n" size="small" style="margin-right: 4px">{{ n }}</el-tag>
          <el-tag v-if="(row as PlatformUserVO).isSuperAdmin === 1" type="danger" size="small">内置超管</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="(row as PlatformUserVO).status === 1 ? 'success' : 'info'">
            {{ (row as PlatformUserVO).status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <template v-if="(row as PlatformUserVO).isSuperAdmin !== 1">
            <el-button link type="primary" @click="openEdit(row as PlatformUserVO)">编辑</el-button>
            <el-button link type="primary" @click="resetPassword(row as PlatformUserVO)">重置密码</el-button>
            <el-button link type="primary" @click="toggleStatus(row as PlatformUserVO)">
              {{ (row as PlatformUserVO).status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="remove(row as PlatformUserVO)">删除</el-button>
          </template>
          <span v-else style="color: #909399">内置账号受保护</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="editingId ? '编辑平台账号' : '新增平台账号'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="账号" prop="username"><el-input v-model="form.username" :disabled="!!editingId" /></el-form-item>
        <el-form-item label="姓名" prop="realName"><el-input v-model="form.realName" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item v-if="!editingId" label="初始密码"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-form-item label="角色">
          <el-checkbox-group v-model="form.roleIds">
            <el-checkbox v-for="r in roles" :key="r.id" :value="r.id">{{ r.name }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { background: #fff; border-radius: 10px; padding: 16px; }
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; }
</style>
```

- [ ] **Step 2: 类型检查 + 构建**

Run: `cd tengyei-frontend; npx vue-tsc --noEmit; npm run build`
Expected: 无 TS 错误,构建成功。

- [ ] **Step 3: 提交**
```bash
git add tengyei-frontend/src/views/platform/PlatformUserListView.vue
git commit -m "feat: platform user management view"
```

---

## 最终验证

- [ ] **后端全量测试**

Run: `cd tengyei-backend; mvn -q test`
Expected: 全部通过。

- [ ] **前端构建**

Run: `cd tengyei-frontend; npm run build`
Expected: 成功。

- [ ] **手动冒烟(可选,需本地 MySQL+Redis)**
  1. 用 `superadmin` 登录 → 侧边栏含 平台人员/平台角色。
  2. 建平台角色"运营只读" → 只勾 `查看平台看板`。
  3. 建平台账号挂该角色 → 用其登录 → 只见工作台,无建公司入口。
  4. 改挂"含 platform:company:* 的角色" → 重登 → 可见并能建企业。
  5. 建一个公司 → 用该公司管理员账号登录 → 侧边栏含 组织/人员/角色,能进人员管理。
  6. 尝试删除 `superadmin` 账号 / `super_admin` 角色 → 被拒。

---

## Notes for Implementer
- **`user` 表反引号**:生产 MySQL 用 `` `user` ``;H2 测试不支持反引号。`PlatformRbacService` 在 H2 测试下运行,**所有 `user` 表引用一律不加反引号**(MySQL 也接受无反引号,本项目 `OrgTestSupport`/`UserInfoController` 已是无反引号写法)。`CompanyService` 现有代码用了 `` `user` `` 但它只在生产路径被 Task 5 测试覆盖——Task 5 的新 SQL 同样用无反引号以兼容 H2。
- **安全不变量**:任何平台端点都不得依赖 `isSuperAdmin()` 做授权;一律 `@PreAuthorize` 具体权限 + service 内 `tenant_id=0` 显式过滤。
- **owner 保护**:`is_super_admin=1` 账号与 `super_admin` 角色在 service 层硬校验,前端按钮隐藏只是辅助。
