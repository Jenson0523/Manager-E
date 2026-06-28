# Phase 2 — 主界面布局 + 基础管理模块 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the full main layout (dark sidebar + multi-tab content nav) and all five basic management modules (company, organization, user, role/permission, dashboard) on top of the Phase 1 auth foundation.

**Architecture:** Backend adds CRUD endpoints in the existing `core-company`, `core-org`, `core-rbac`, and `app` modules following the established `BaseEntity`/`Result`/`TenantContext` patterns; MyBatis-Plus `TenantLineInnerInterceptor` auto-isolates tenant data and a new `PaginationInnerInterceptor` powers list paging. Frontend adds a `MainLayout` shell (sidebar/header/tabbar) driven by `useAuthStore().routes`, a `useTabStore` (Pinia + sessionStorage) for multi-tab navigation, and one view per module.

**Tech Stack:** Spring Boot 3.2.5, MyBatis-Plus 3.5.7, Spring Security (method security), JJWT 0.12.5, MySQL/H2(test); Vue 3 + TypeScript + Vite + Element Plus + Pinia + Vue Router 4.

---

## Conventions & Decisions

- **Java packages:** `com.tengyei.company.*` (core-company), `com.tengyei.org.*` (core-org), `com.tengyei.rbac.*` (core-rbac). Sub-packages: `entity`, `mapper`, `service`, `controller`, `dto`. Component scan (`scanBasePackages="com.tengyei"`) and `@MapperScan("com.tengyei.**.mapper")` already pick these up.
- **Authorities:** `JwtAuthFilter` registers permissions as `PERM_<code>` (e.g. `PERM_user:create`). Method security uses `@PreAuthorize("hasAuthority('PERM_user:create')")`. Super admin carries permission `*` → authority `PERM_*`; super-admin-only endpoints check `@PreAuthorize("hasAuthority('PERM_*')")`.
- **Tenant isolation:** For company-scoped inserts, set `tenantId` explicitly from `TenantContext.getTenantId()` in the service. The interceptor skips injecting the column when it is already present, and adds the `WHERE tenant_id=?` clause on reads. `company`, `permission`, `role_permission`, `user_role` are in `IGNORE_TABLES` (no auto filter). Super admin (tenant 0) bypasses all tenant filters.
- **data_scope values:** `all` / `branch` / `dept` / `self` everywhere (the spec's stray "company" is corrected to `dept`/`branch`).
- **Pagination response:** `com.tengyei.common.response.PageResult<T>` with `records`, `total`, `current`, `size`.
- **Passwords:** `PasswordEncoder` (BCrypt-12) bean already exists in `SecurityConfig`. New-user initial passwords are encoded before insert.
- **Reserved table name:** `user` is mapped via `@TableName("user")` (matches existing JdbcTemplate usage which already works against MySQL & H2).

---

## File Structure

**Backend — common (shared):**
- Create `common/src/main/java/com/tengyei/common/response/PageResult.java` — generic paged response.

**Backend — core-company:**
- Modify `core-company/pom.xml` — add web, security, lombok deps.
- Create `core-company/src/main/java/com/tengyei/company/entity/Company.java`
- Create `core-company/src/main/java/com/tengyei/company/mapper/CompanyMapper.java`
- Create `core-company/src/main/java/com/tengyei/company/dto/{CompanyCreateDTO,CompanyUpdateDTO,CompanyVO}.java`
- Create `core-company/src/main/java/com/tengyei/company/service/CompanyService.java`
- Create `core-company/src/main/java/com/tengyei/company/controller/CompanyController.java`

**Backend — core-org:**
- Modify `core-org/pom.xml` — add web, security, lombok deps.
- Create `entity/{Dept,Branch,User}.java`, `mapper/{DeptMapper,BranchMapper,UserMapper}.java`
- Create `dto/*` for dept/branch/user create/update/VO/tree
- Create `service/{DeptService,BranchService,UserService}.java`
- Create `controller/{DeptController,BranchController,UserController}.java`

**Backend — core-rbac:**
- Modify `core-rbac/pom.xml` — add web, security, lombok deps.
- Create `entity/{Role,Permission}.java`, `mapper/{RoleMapper,PermissionMapper,RolePermissionMapper,UserRoleMapper}.java`
- Create `dto/*` and `service/{RoleService,PermissionService}.java`, `controller/{RoleController,PermissionController}.java`

**Backend — core-auth (modify):**
- Modify `core-auth/src/main/java/com/tengyei/auth/config/SecurityConfig.java` — add `@EnableMethodSecurity`.

**Backend — common (modify):**
- Modify `common/src/main/java/com/tengyei/common/config/MybatisPlusConfig.java` — add `PaginationInnerInterceptor`.

**Backend — app:**
- Create `app/src/main/java/com/tengyei/controller/DashboardController.java`
- Create `app/src/main/java/com/tengyei/service/DashboardService.java`
- Modify `app/src/main/java/com/tengyei/controller/UserInfoController.java` — align route paths with frontend.
- Create test classes under `app/src/test/java/com/tengyei/`.

**Frontend:**
- Modify `src/types/auth.ts`; create `src/types/{company,org,user,rbac,dashboard}.ts` and `src/types/common.ts`.
- Create `src/api/{company,org,user,rbac,dashboard}.ts`.
- Create `src/stores/tab.ts`.
- Modify `src/router/index.ts`.
- Create `src/layout/{MainLayout,AppSidebar,AppHeader,AppTabBar}.vue`.
- Create `src/views/dashboard/{SuperDashboard,CompanyDashboard}.vue` and `src/views/DashboardView.vue` (dispatcher).
- Create `src/views/company/CompanyListView.vue`, `src/views/org/OrgView.vue`, `src/views/user/UserListView.vue`, `src/views/role/RoleView.vue`.

---

## Task 1: Backend infrastructure — method security, pagination, PageResult, module deps

**Files:**
- Create: `tengyei-backend/common/src/main/java/com/tengyei/common/response/PageResult.java`
- Modify: `tengyei-backend/common/src/main/java/com/tengyei/common/config/MybatisPlusConfig.java`
- Modify: `tengyei-backend/core-auth/src/main/java/com/tengyei/auth/config/SecurityConfig.java`
- Modify: `tengyei-backend/core-company/pom.xml`, `tengyei-backend/core-org/pom.xml`, `tengyei-backend/core-rbac/pom.xml`

- [ ] **Step 1: Create `PageResult`**

```java
package com.tengyei.common.response;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Getter;

import java.util.List;
import java.util.function.Function;

@Getter
public class PageResult<T> {

    private final List<T> records;
    private final long total;
    private final long current;
    private final long size;

    private PageResult(List<T> records, long total, long current, long size) {
        this.records = records;
        this.total = total;
        this.current = current;
        this.size = size;
    }

    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        return new PageResult<>(records, total, current, size);
    }

    /** Map a MyBatis-Plus IPage of entities into a PageResult of VOs. */
    public static <E, V> PageResult<V> from(IPage<E> page, Function<E, V> mapper) {
        List<V> vos = page.getRecords().stream().map(mapper).toList();
        return new PageResult<>(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
```

- [ ] **Step 2: Add `PaginationInnerInterceptor` to `MybatisPlusConfig`**

Modify the `mybatisPlusInterceptor()` bean. The tenant interceptor MUST be added before the pagination interceptor. Add the imports and the new inner interceptor:

```java
package com.tengyei.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.tengyei.common.context.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Configuration
public class MybatisPlusConfig {

    private static final Set<String> IGNORE_TABLES = new HashSet<>(Arrays.asList(
        "company", "permission", "role_permission", "user_role",
        "module_registry", "login_log"
    ));

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tenantId = TenantContext.getTenantId();
                return new LongValue(tenantId != null ? tenantId : 0L);
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return TenantContext.isSuperAdmin() || IGNORE_TABLES.contains(tableName);
            }
        }));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
```

- [ ] **Step 3: Enable method security in `SecurityConfig`**

Add the `@EnableMethodSecurity` annotation (and its import) to the class. The rest of the file is unchanged.

Add import after the existing security imports:
```java
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
```

Change the class annotations from:
```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
```
to:
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
```

- [ ] **Step 4: Add web/security/lombok deps to the three business modules**

For EACH of `core-company/pom.xml`, `core-org/pom.xml`, `core-rbac/pom.xml`, replace the `<dependencies>` block so it reads (keep everything else in the file unchanged):

```xml
    <dependencies>
        <dependency>
            <groupId>com.tengyei</groupId>
            <artifactId>common</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
```

- [ ] **Step 5: Compile to verify wiring**

Run: `mvn -q -pl common,core-auth,core-company,core-org,core-rbac -am compile`
Expected: BUILD SUCCESS (no source errors; modules now resolve web/security/lombok).

- [ ] **Step 6: Commit**

```bash
git add tengyei-backend/common tengyei-backend/core-auth/src/main/java/com/tengyei/auth/config/SecurityConfig.java tengyei-backend/core-company/pom.xml tengyei-backend/core-org/pom.xml tengyei-backend/core-rbac/pom.xml
git commit -m "feat(backend): enable method security, pagination interceptor, PageResult, module deps"
```

---

## Task 2: core-company — Company management (CRUD + create-with-admin)

**Files:**
- Create: `core-company/src/main/java/com/tengyei/company/entity/Company.java`
- Create: `core-company/src/main/java/com/tengyei/company/mapper/CompanyMapper.java`
- Create: `core-company/src/main/java/com/tengyei/company/dto/CompanyCreateDTO.java`
- Create: `core-company/src/main/java/com/tengyei/company/dto/CompanyUpdateDTO.java`
- Create: `core-company/src/main/java/com/tengyei/company/dto/CompanyVO.java`
- Create: `core-company/src/main/java/com/tengyei/company/service/CompanyService.java`
- Create: `core-company/src/main/java/com/tengyei/company/controller/CompanyController.java`
- Test: `app/src/test/java/com/tengyei/CompanyControllerTest.java`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/tengyei/CompanyControllerTest.java`:

```java
package com.tengyei;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tengyei.auth.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class CompanyControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String superToken() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("superadmin");
        req.setPassword("Admin@2026");
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    @Test
    void super_admin_can_create_list_and_toggle_company() throws Exception {
        String token = superToken();
        String unique = "C" + System.currentTimeMillis();
        String body = """
            {"fullName":"%s 科技有限公司","shortName":"%s",
             "adminName":"张三","adminPhone":"13900000001",
             "adminUsername":"admin_%s","adminPassword":"Init@2026"}
            """.formatted(unique, unique, unique);

        MvcResult created = mockMvc.perform(post("/api/v1/companies")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andReturn();

        long id = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(get("/api/v1/companies?page=1&size=20")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isArray());

        mockMvc.perform(put("/api/v1/companies/" + id + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void company_creation_requires_super_admin_authority() throws Exception {
        // No token → 401 envelope from entry point
        mockMvc.perform(post("/api/v1/companies")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl app -am test -Dtest=CompanyControllerTest`
Expected: FAIL — endpoints `/api/v1/companies` return 404/401, classes not yet created.

- [ ] **Step 3: Create the `Company` entity**

`core-company/src/main/java/com/tengyei/company/entity/Company.java`:

```java
package com.tengyei.company.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tengyei.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@TableName("company")
public class Company extends BaseEntity {
    private String companyNo;
    private String fullName;
    private String shortName;
    private String creditCode;
    private String logoUrl;
    private String adminName;
    private String adminPhone;
    private String adminEmail;
    /** 0=待激活 1=启用 2=停用 */
    private Integer status;
    private LocalDate expireDate;
    private Integer maxUsers;
    private Integer maxBranches;
    private String remark;
}
```

- [ ] **Step 4: Create the `CompanyMapper`**

`core-company/src/main/java/com/tengyei/company/mapper/CompanyMapper.java`:

```java
package com.tengyei.company.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tengyei.company.entity.Company;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CompanyMapper extends BaseMapper<Company> {
}
```

- [ ] **Step 5: Create the DTOs**

`core-company/src/main/java/com/tengyei/company/dto/CompanyCreateDTO.java`:

```java
package com.tengyei.company.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompanyCreateDTO {
    @NotBlank(message = "企业全称不能为空")
    private String fullName;
    @NotBlank(message = "企业简称不能为空")
    private String shortName;
    private String creditCode;
    @NotBlank(message = "管理员姓名不能为空")
    private String adminName;
    @NotBlank(message = "管理员电话不能为空")
    private String adminPhone;
    private String adminEmail;
    @NotBlank(message = "初始管理员账号不能为空")
    private String adminUsername;
    @NotBlank(message = "初始管理员密码不能为空")
    private String adminPassword;
}
```

`core-company/src/main/java/com/tengyei/company/dto/CompanyUpdateDTO.java`:

```java
package com.tengyei.company.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompanyUpdateDTO {
    @NotBlank(message = "企业全称不能为空")
    private String fullName;
    @NotBlank(message = "企业简称不能为空")
    private String shortName;
    private String creditCode;
    private String adminName;
    private String adminPhone;
    private String adminEmail;
    private String remark;
}
```

`core-company/src/main/java/com/tengyei/company/dto/CompanyVO.java`:

```java
package com.tengyei.company.dto;

import com.tengyei.company.entity.Company;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CompanyVO {
    private Long id;
    private String companyNo;
    private String fullName;
    private String shortName;
    private String adminName;
    private String adminPhone;
    private Integer status;
    private LocalDateTime createdAt;

    public static CompanyVO from(Company c) {
        return CompanyVO.builder()
                .id(c.getId())
                .companyNo(c.getCompanyNo())
                .fullName(c.getFullName())
                .shortName(c.getShortName())
                .adminName(c.getAdminName())
                .adminPhone(c.getAdminPhone())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
```

- [ ] **Step 6: Create the `CompanyService`**

The create flow is transactional: insert company, then insert the initial admin user with `tenant_id = company.id`. Uses `JdbcTemplate` for the user insert (the `user` table lives in core-org; avoiding a cross-module entity dependency keeps the company module self-contained).

`core-company/src/main/java/com/tengyei/company/service/CompanyService.java`:

```java
package com.tengyei.company.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tengyei.common.exception.BusinessException;
import com.tengyei.common.response.PageResult;
import com.tengyei.company.dto.CompanyCreateDTO;
import com.tengyei.company.dto.CompanyUpdateDTO;
import com.tengyei.company.dto.CompanyVO;
import com.tengyei.company.entity.Company;
import com.tengyei.company.mapper.CompanyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyMapper companyMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public PageResult<CompanyVO> page(long page, long size, String keyword) {
        LambdaQueryWrapper<Company> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.like(Company::getFullName, keyword).or().like(Company::getShortName, keyword);
        }
        qw.orderByDesc(Company::getId);
        Page<Company> result = companyMapper.selectPage(new Page<>(page, size), qw);
        return PageResult.from(result, CompanyVO::from);
    }

    public CompanyVO detail(Long id) {
        Company c = companyMapper.selectById(id);
        if (c == null) throw new BusinessException(404, "企业不存在");
        return CompanyVO.from(c);
    }

    @Transactional
    public Long create(CompanyCreateDTO dto) {
        Long existing = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user WHERE username = ?", Long.class, dto.getAdminUsername());
        if (existing != null && existing > 0) {
            throw new BusinessException(409, "管理员账号已存在");
        }

        Company c = new Company();
        c.setFullName(dto.getFullName());
        c.setShortName(dto.getShortName());
        c.setCreditCode(dto.getCreditCode());
        c.setAdminName(dto.getAdminName());
        c.setAdminPhone(dto.getAdminPhone());
        c.setAdminEmail(dto.getAdminEmail());
        c.setStatus(1); // 创建即启用
        c.setCompanyNo("PENDING");
        companyMapper.insert(c);

        Long companyId = c.getId();
        c.setCompanyNo("E" + String.format("%06d", companyId));
        companyMapper.updateById(c);

        String encoded = passwordEncoder.encode(dto.getAdminPassword());
        jdbcTemplate.update(
            "INSERT INTO user (tenant_id, user_no, username, password, real_name, phone, email, " +
            "is_super_admin, status, pwd_reset_required, is_deleted, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, 0, 1, 1, 0, NOW(), NOW())",
            companyId, "U" + companyId + "-0001", dto.getAdminUsername(), encoded,
            dto.getAdminName(), dto.getAdminPhone(), dto.getAdminEmail());

        return companyId;
    }

    public void update(Long id, CompanyUpdateDTO dto) {
        Company c = companyMapper.selectById(id);
        if (c == null) throw new BusinessException(404, "企业不存在");
        c.setFullName(dto.getFullName());
        c.setShortName(dto.getShortName());
        c.setCreditCode(dto.getCreditCode());
        c.setAdminName(dto.getAdminName());
        c.setAdminPhone(dto.getAdminPhone());
        c.setAdminEmail(dto.getAdminEmail());
        c.setRemark(dto.getRemark());
        companyMapper.updateById(c);
    }

    public void changeStatus(Long id, Integer status) {
        Company c = companyMapper.selectById(id);
        if (c == null) throw new BusinessException(404, "企业不存在");
        if (status == null || (status != 1 && status != 2)) {
            throw new BusinessException(422, "状态值无效");
        }
        c.setStatus(status);
        companyMapper.updateById(c);
    }
}
```

- [ ] **Step 7: Create the `CompanyController`**

`core-company/src/main/java/com/tengyei/company/controller/CompanyController.java`:

```java
package com.tengyei.company.controller;

import com.tengyei.common.response.PageResult;
import com.tengyei.common.response.Result;
import com.tengyei.company.dto.CompanyCreateDTO;
import com.tengyei.company.dto.CompanyUpdateDTO;
import com.tengyei.company.dto.CompanyVO;
import com.tengyei.company.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_*')")
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    public Result<PageResult<CompanyVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(companyService.page(page, size, keyword));
    }

    @GetMapping("/{id}")
    public Result<CompanyVO> detail(@PathVariable Long id) {
        return Result.ok(companyService.detail(id));
    }

    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody CompanyCreateDTO dto) {
        Long id = companyService.create(dto);
        return Result.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CompanyUpdateDTO dto) {
        companyService.update(id, dto);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        companyService.changeStatus(id, body.get("status"));
        return Result.ok();
    }
}
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `mvn -q -pl app -am test -Dtest=CompanyControllerTest`
Expected: PASS (2 tests green).

- [ ] **Step 9: Commit**

```bash
git add tengyei-backend/core-company app/src/test/java/com/tengyei/CompanyControllerTest.java
git commit -m "feat(core-company): company CRUD with transactional initial-admin creation"
```

---

## Task 3: core-org — Department tree management

**Files:**
- Create: `core-org/src/main/java/com/tengyei/org/entity/Dept.java`
- Create: `core-org/src/main/java/com/tengyei/org/mapper/DeptMapper.java`
- Create: `core-org/src/main/java/com/tengyei/org/dto/DeptSaveDTO.java`
- Create: `core-org/src/main/java/com/tengyei/org/dto/DeptTreeVO.java`
- Create: `core-org/src/main/java/com/tengyei/org/service/DeptService.java`
- Create: `core-org/src/main/java/com/tengyei/org/controller/DeptController.java`
- Test: `app/src/test/java/com/tengyei/OrgTestSupport.java` (shared helper, created here)
- Test: `app/src/test/java/com/tengyei/DeptControllerTest.java`

> **Tenant context for org/user/rbac tests:** these endpoints run as a *company admin* (tenant != 0). The shared helper `OrgTestSupport` seeds a fresh company + an admin user holding a preset role with all permissions, then logs in to obtain a token. Reuse it across Tasks 3–6.

- [ ] **Step 1: Create the shared test helper**

`app/src/test/java/com/tengyei/OrgTestSupport.java`:

```java
package com.tengyei;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tengyei.auth.dto.LoginRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Seeds a company + admin (all permissions) and returns a logged-in token. */
public final class OrgTestSupport {

    public static final String ADMIN_PWD_HASH =
        "$2a$12$ufBfS4xdJAYPymNVNHkCbepoD4vzNhw.A2pR942oNeIfzpZFNO.R2"; // Admin@2026

    private OrgTestSupport() {}

    public record Seeded(long tenantId, long adminUserId, long roleId, String username) {}

    public static Seeded seedCompanyAdmin(JdbcTemplate jdbc) {
        String suffix = String.valueOf(System.nanoTime());
        KeyHolder ck = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO company (company_no, full_name, short_name, admin_name, admin_phone, " +
                "status, is_deleted, created_at, updated_at) VALUES (?,?,?,?,?,1,0,NOW(),NOW())",
                Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, "E" + suffix.substring(suffix.length() - 6));
            ps.setString(2, "测试企业" + suffix);
            ps.setString(3, "测试" + suffix);
            ps.setString(4, "管理员");
            ps.setString(5, "13900000000");
            return ps;
        }, ck);
        long tenantId = ck.getKey().longValue();

        String username = "admin_" + suffix;
        KeyHolder uk = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO user (tenant_id, user_no, username, password, real_name, phone, " +
                "is_super_admin, status, pwd_reset_required, is_deleted, created_at, updated_at) " +
                "VALUES (?,?,?,?,?,?,0,1,0,0,NOW(),NOW())",
                Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tenantId);
            ps.setString(2, "U" + tenantId + "-0001");
            ps.setString(3, username);
            ps.setString(4, ADMIN_PWD_HASH);
            ps.setString(5, "企业管理员");
            ps.setString(6, "13900000001");
            return ps;
        }, uk);
        long adminUserId = uk.getKey().longValue();

        KeyHolder rk = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO role (tenant_id, name, code, data_scope, is_preset, status, " +
                "is_deleted, created_at, updated_at) VALUES (?,?,?,?,1,1,0,NOW(),NOW())",
                Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tenantId);
            ps.setString(2, "企业管理员");
            ps.setString(3, "company_admin");
            ps.setString(4, "all");
            return ps;
        }, rk);
        long roleId = rk.getKey().longValue();

        jdbc.update("INSERT INTO user_role (user_id, role_id, created_at) VALUES (?,?,NOW())",
                adminUserId, roleId);
        // grant ALL permissions to this role
        jdbc.update(
            "INSERT INTO role_permission (role_id, permission_id, created_at) " +
            "SELECT ?, id, NOW() FROM permission", roleId);

        return new Seeded(tenantId, adminUserId, roleId, username);
    }

    public static String login(MockMvc mockMvc, ObjectMapper om, String username) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword("Admin@2026");
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(req)))
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }
}
```

- [ ] **Step 2: Write the failing test**

`app/src/test/java/com/tengyei/DeptControllerTest.java`:

```java
package com.tengyei;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class DeptControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    private String token;

    @BeforeEach
    void setup() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbcTemplate);
        token = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());
    }

    @Test
    void create_then_tree_returns_hierarchy() throws Exception {
        mockMvc.perform(post("/api/v1/depts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"研发部\",\"parentId\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/depts/tree")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].name").value("研发部"));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q -pl app -am test -Dtest=DeptControllerTest`
Expected: FAIL — `/api/v1/depts` not found.

- [ ] **Step 4: Create the `Dept` entity**

`core-org/src/main/java/com/tengyei/org/entity/Dept.java`:

```java
package com.tengyei.org.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tengyei.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("dept")
public class Dept extends BaseEntity {
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private Long tenantId;
    private String name;
    private String code;
    private Long parentId;
    private Long leaderId;
    private Integer sortOrder;
    private Integer status;
}
```

> Note: `tenantId` is populated explicitly in the service; the `INSERT` fill annotation is a harmless secondary guard (the MetaObjectHandler does not fill `tenantId`, so the explicit set in the service is authoritative).

- [ ] **Step 5: Create the `DeptMapper`**

`core-org/src/main/java/com/tengyei/org/mapper/DeptMapper.java`:

```java
package com.tengyei.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tengyei.org.entity.Dept;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeptMapper extends BaseMapper<Dept> {
}
```

- [ ] **Step 6: Create the DTOs**

`core-org/src/main/java/com/tengyei/org/dto/DeptSaveDTO.java`:

```java
package com.tengyei.org.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeptSaveDTO {
    @NotBlank(message = "部门名称不能为空")
    private String name;
    private String code;
    private Long parentId;
    private Long leaderId;
    private Integer sortOrder;
}
```

`core-org/src/main/java/com/tengyei/org/dto/DeptTreeVO.java`:

```java
package com.tengyei.org.dto;

import com.tengyei.org.entity.Dept;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class DeptTreeVO {
    private Long id;
    private String name;
    private String code;
    private Long parentId;
    private Long leaderId;
    private Integer sortOrder;
    private Integer status;
    private List<DeptTreeVO> children = new ArrayList<>();

    public static DeptTreeVO from(Dept d) {
        DeptTreeVO vo = new DeptTreeVO();
        vo.setId(d.getId());
        vo.setName(d.getName());
        vo.setCode(d.getCode());
        vo.setParentId(d.getParentId());
        vo.setLeaderId(d.getLeaderId());
        vo.setSortOrder(d.getSortOrder());
        vo.setStatus(d.getStatus());
        return vo;
    }
}
```

- [ ] **Step 7: Create the `DeptService`**

`core-org/src/main/java/com/tengyei/org/service/DeptService.java`:

```java
package com.tengyei.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.exception.BusinessException;
import com.tengyei.org.dto.DeptSaveDTO;
import com.tengyei.org.dto.DeptTreeVO;
import com.tengyei.org.entity.Dept;
import com.tengyei.org.mapper.DeptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeptService {

    private final DeptMapper deptMapper;

    public List<DeptTreeVO> tree() {
        List<Dept> all = deptMapper.selectList(
            new LambdaQueryWrapper<Dept>().orderByAsc(Dept::getSortOrder).orderByAsc(Dept::getId));
        Map<Long, DeptTreeVO> map = new LinkedHashMap<>();
        for (Dept d : all) map.put(d.getId(), DeptTreeVO.from(d));
        List<DeptTreeVO> roots = new ArrayList<>();
        for (Dept d : all) {
            DeptTreeVO node = map.get(d.getId());
            Long pid = d.getParentId();
            if (pid == null || pid == 0L || !map.containsKey(pid)) {
                roots.add(node);
            } else {
                map.get(pid).getChildren().add(node);
            }
        }
        return roots;
    }

    public Long create(DeptSaveDTO dto) {
        Dept d = new Dept();
        d.setTenantId(TenantContext.getTenantId());
        d.setName(dto.getName());
        d.setCode(dto.getCode());
        d.setParentId(dto.getParentId() != null ? dto.getParentId() : 0L);
        d.setLeaderId(dto.getLeaderId());
        d.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        d.setStatus(1);
        deptMapper.insert(d);
        return d.getId();
    }

    public void update(Long id, DeptSaveDTO dto) {
        Dept d = deptMapper.selectById(id);
        if (d == null) throw new BusinessException(404, "部门不存在");
        d.setName(dto.getName());
        d.setCode(dto.getCode());
        d.setLeaderId(dto.getLeaderId());
        if (dto.getSortOrder() != null) d.setSortOrder(dto.getSortOrder());
        deptMapper.updateById(d);
    }

    public void delete(Long id) {
        Long childCount = deptMapper.selectCount(
            new LambdaQueryWrapper<Dept>().eq(Dept::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException(409, "存在子部门，无法删除");
        }
        deptMapper.deleteById(id);
    }
}
```

- [ ] **Step 8: Create the `DeptController`**

`core-org/src/main/java/com/tengyei/org/controller/DeptController.java`:

```java
package com.tengyei.org.controller;

import com.tengyei.common.response.Result;
import com.tengyei.org.dto.DeptSaveDTO;
import com.tengyei.org.dto.DeptTreeVO;
import com.tengyei.org.service.DeptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/depts")
@RequiredArgsConstructor
public class DeptController {

    private final DeptService deptService;

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('PERM_dept:view')")
    public Result<List<DeptTreeVO>> tree() {
        return Result.ok(deptService.tree());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_dept:create')")
    public Result<Map<String, Long>> create(@Valid @RequestBody DeptSaveDTO dto) {
        return Result.ok(Map.of("id", deptService.create(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_dept:edit')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DeptSaveDTO dto) {
        deptService.update(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_dept:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return Result.ok();
    }
}
```

- [ ] **Step 9: Run the test to verify it passes**

Run: `mvn -q -pl app -am test -Dtest=DeptControllerTest`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add tengyei-backend/core-org/src/main/java/com/tengyei/org/entity/Dept.java tengyei-backend/core-org/src/main/java/com/tengyei/org/mapper/DeptMapper.java tengyei-backend/core-org/src/main/java/com/tengyei/org/dto/Dept*.java tengyei-backend/core-org/src/main/java/com/tengyei/org/service/DeptService.java tengyei-backend/core-org/src/main/java/com/tengyei/org/controller/DeptController.java app/src/test/java/com/tengyei/OrgTestSupport.java app/src/test/java/com/tengyei/DeptControllerTest.java
git commit -m "feat(core-org): department tree CRUD with tenant isolation"
```

---

## Task 4: core-org — Branch (分支机构) management

**Files:**
- Create: `core-org/src/main/java/com/tengyei/org/entity/Branch.java`
- Create: `core-org/src/main/java/com/tengyei/org/mapper/BranchMapper.java`
- Create: `core-org/src/main/java/com/tengyei/org/dto/BranchSaveDTO.java`
- Create: `core-org/src/main/java/com/tengyei/org/dto/BranchVO.java`
- Create: `core-org/src/main/java/com/tengyei/org/service/BranchService.java`
- Create: `core-org/src/main/java/com/tengyei/org/controller/BranchController.java`
- Test: `app/src/test/java/com/tengyei/BranchControllerTest.java`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/tengyei/BranchControllerTest.java`:

```java
package com.tengyei;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class BranchControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    private String token;

    @BeforeEach
    void setup() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbcTemplate);
        token = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());
    }

    @Test
    void create_then_page_lists_branch() throws Exception {
        mockMvc.perform(post("/api/v1/branches")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchNo\":\"BR001\",\"name\":\"上海分公司\",\"type\":\"independent\",\"phone\":\"02100000000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/branches?page=1&size=20")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].name").value("上海分公司"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl app -am test -Dtest=BranchControllerTest`
Expected: FAIL — `/api/v1/branches` not found.

- [ ] **Step 3: Create the `Branch` entity**

`core-org/src/main/java/com/tengyei/org/entity/Branch.java`:

```java
package com.tengyei.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tengyei.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("branch")
public class Branch extends BaseEntity {
    private Long tenantId;
    private String branchNo;
    private String name;
    /** independent | affiliated */
    private String type;
    private String province;
    private String city;
    private String district;
    private String address;
    private Long leaderId;
    private String phone;
    private Integer maxUsers;
    private Integer status;
}
```

- [ ] **Step 4: Create the `BranchMapper`**

`core-org/src/main/java/com/tengyei/org/mapper/BranchMapper.java`:

```java
package com.tengyei.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tengyei.org.entity.Branch;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BranchMapper extends BaseMapper<Branch> {
}
```

- [ ] **Step 5: Create the DTOs**

`core-org/src/main/java/com/tengyei/org/dto/BranchSaveDTO.java`:

```java
package com.tengyei.org.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BranchSaveDTO {
    @NotBlank(message = "机构编号不能为空")
    private String branchNo;
    @NotBlank(message = "机构名称不能为空")
    private String name;
    private String type;
    private String province;
    private String city;
    private String district;
    private String address;
    private Long leaderId;
    private String phone;
    private Integer maxUsers;
}
```

`core-org/src/main/java/com/tengyei/org/dto/BranchVO.java`:

```java
package com.tengyei.org.dto;

import com.tengyei.org.entity.Branch;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BranchVO {
    private Long id;
    private String branchNo;
    private String name;
    private String type;
    private Long leaderId;
    private String phone;
    private String city;
    private Integer status;

    public static BranchVO from(Branch b) {
        return BranchVO.builder()
                .id(b.getId())
                .branchNo(b.getBranchNo())
                .name(b.getName())
                .type(b.getType())
                .leaderId(b.getLeaderId())
                .phone(b.getPhone())
                .city(b.getCity())
                .status(b.getStatus())
                .build();
    }
}
```

- [ ] **Step 6: Create the `BranchService`**

`core-org/src/main/java/com/tengyei/org/service/BranchService.java`:

```java
package com.tengyei.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.exception.BusinessException;
import com.tengyei.common.response.PageResult;
import com.tengyei.org.dto.BranchSaveDTO;
import com.tengyei.org.dto.BranchVO;
import com.tengyei.org.entity.Branch;
import com.tengyei.org.mapper.BranchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchMapper branchMapper;

    public PageResult<BranchVO> page(long page, long size) {
        Page<Branch> result = branchMapper.selectPage(new Page<>(page, size),
            new LambdaQueryWrapper<Branch>().orderByDesc(Branch::getId));
        return PageResult.from(result, BranchVO::from);
    }

    public Long create(BranchSaveDTO dto) {
        Branch b = new Branch();
        b.setTenantId(TenantContext.getTenantId());
        apply(b, dto);
        b.setStatus(1);
        branchMapper.insert(b);
        return b.getId();
    }

    public void update(Long id, BranchSaveDTO dto) {
        Branch b = branchMapper.selectById(id);
        if (b == null) throw new BusinessException(404, "分支机构不存在");
        apply(b, dto);
        branchMapper.updateById(b);
    }

    public void changeStatus(Long id, Integer status) {
        Branch b = branchMapper.selectById(id);
        if (b == null) throw new BusinessException(404, "分支机构不存在");
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(422, "状态值无效");
        }
        b.setStatus(status);
        branchMapper.updateById(b);
    }

    private void apply(Branch b, BranchSaveDTO dto) {
        b.setBranchNo(dto.getBranchNo());
        b.setName(dto.getName());
        b.setType(dto.getType() != null ? dto.getType() : "independent");
        b.setProvince(dto.getProvince());
        b.setCity(dto.getCity());
        b.setDistrict(dto.getDistrict());
        b.setAddress(dto.getAddress());
        b.setLeaderId(dto.getLeaderId());
        b.setPhone(dto.getPhone());
        b.setMaxUsers(dto.getMaxUsers());
    }
}
```

- [ ] **Step 7: Create the `BranchController`**

`core-org/src/main/java/com/tengyei/org/controller/BranchController.java`:

```java
package com.tengyei.org.controller;

import com.tengyei.common.response.PageResult;
import com.tengyei.common.response.Result;
import com.tengyei.org.dto.BranchSaveDTO;
import com.tengyei.org.dto.BranchVO;
import com.tengyei.org.service.BranchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_branch:view')")
    public Result<PageResult<BranchVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(branchService.page(page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_branch:create')")
    public Result<Map<String, Long>> create(@Valid @RequestBody BranchSaveDTO dto) {
        return Result.ok(Map.of("id", branchService.create(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_branch:edit')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BranchSaveDTO dto) {
        branchService.update(id, dto);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PERM_branch:edit')")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        branchService.changeStatus(id, body.get("status"));
        return Result.ok();
    }
}
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `mvn -q -pl app -am test -Dtest=BranchControllerTest`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add tengyei-backend/core-org/src/main/java/com/tengyei/org/entity/Branch.java tengyei-backend/core-org/src/main/java/com/tengyei/org/mapper/BranchMapper.java tengyei-backend/core-org/src/main/java/com/tengyei/org/dto/Branch*.java tengyei-backend/core-org/src/main/java/com/tengyei/org/service/BranchService.java tengyei-backend/core-org/src/main/java/com/tengyei/org/controller/BranchController.java app/src/test/java/com/tengyei/BranchControllerTest.java
git commit -m "feat(core-org): branch CRUD with paging and status toggle"
```

---

## Task 5: core-org — User management (CRUD + role assignment + reset password)

**Files:**
- Create: `core-org/src/main/java/com/tengyei/org/entity/User.java`
- Create: `core-org/src/main/java/com/tengyei/org/mapper/UserMapper.java`
- Create: `core-org/src/main/java/com/tengyei/org/dto/UserCreateDTO.java`
- Create: `core-org/src/main/java/com/tengyei/org/dto/UserUpdateDTO.java`
- Create: `core-org/src/main/java/com/tengyei/org/dto/UserVO.java`
- Create: `core-org/src/main/java/com/tengyei/org/service/UserService.java`
- Create: `core-org/src/main/java/com/tengyei/org/controller/UserController.java`
- Test: `app/src/test/java/com/tengyei/UserControllerTest.java`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/tengyei/UserControllerTest.java`:

```java
package com.tengyei;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    private String token;
    private long roleId;

    @BeforeEach
    void setup() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbcTemplate);
        roleId = seeded.roleId();
        token = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());
    }

    @Test
    void create_assign_role_then_new_user_can_login() throws Exception {
        String uname = "staff_" + System.nanoTime();
        String body = """
            {"username":"%s","realName":"李四","phone":"13700000001",
             "password":"Staff@2026","roleIds":[%d]}
            """.formatted(uname, roleId);

        MvcResult created = mockMvc.perform(post("/api/v1/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long newId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(get("/api/v1/users?page=1&size=20&keyword=" + uname)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].username").value(uname));

        // reset password endpoint works
        mockMvc.perform(put("/api/v1/users/" + newId + "/reset-password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"New@2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl app -am test -Dtest=UserControllerTest`
Expected: FAIL — `/api/v1/users` not found.

- [ ] **Step 3: Create the `User` entity**

`core-org/src/main/java/com/tengyei/org/entity/User.java`:

```java
package com.tengyei.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tengyei.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("user")
public class User extends BaseEntity {
    private Long tenantId;
    private String userNo;
    private String username;
    private String password;
    private String realName;
    private String phone;
    private String email;
    private String avatarUrl;
    private Long deptId;
    private Long branchId;
    private Long leaderId;
    private LocalDate entryDate;
    private Integer isSuperAdmin;
    private Integer status;
    private Integer pwdResetRequired;
    private Integer loginFailCount;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
}
```

- [ ] **Step 4: Create the `UserMapper`**

`core-org/src/main/java/com/tengyei/org/mapper/UserMapper.java`:

```java
package com.tengyei.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tengyei.org.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
```

- [ ] **Step 5: Create the DTOs**

`core-org/src/main/java/com/tengyei/org/dto/UserCreateDTO.java`:

```java
package com.tengyei.org.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class UserCreateDTO {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "真实姓名不能为空")
    private String realName;
    @NotBlank(message = "手机号不能为空")
    private String phone;
    private String email;
    @NotBlank(message = "初始密码不能为空")
    private String password;
    private Long deptId;
    private Long branchId;
    private List<Long> roleIds;
}
```

`core-org/src/main/java/com/tengyei/org/dto/UserUpdateDTO.java`:

```java
package com.tengyei.org.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserUpdateDTO {
    @NotBlank(message = "真实姓名不能为空")
    private String realName;
    @NotBlank(message = "手机号不能为空")
    private String phone;
    private String email;
    private Long deptId;
    private Long branchId;
}
```

`core-org/src/main/java/com/tengyei/org/dto/UserVO.java`:

```java
package com.tengyei.org.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class UserVO {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private Long deptId;
    private Long branchId;
    private Integer status;
    private List<Long> roleIds;
    private List<String> roleNames;
}
```

- [ ] **Step 6: Create the `UserService`**

Role assignment uses JdbcTemplate against `user_role` (an IGNORE_TABLE, no tenant column). The page query joins roles per user for display.

`core-org/src/main/java/com/tengyei/org/service/UserService.java`:

```java
package com.tengyei.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.exception.BusinessException;
import com.tengyei.common.response.PageResult;
import com.tengyei.org.dto.UserCreateDTO;
import com.tengyei.org.dto.UserUpdateDTO;
import com.tengyei.org.dto.UserVO;
import com.tengyei.org.entity.User;
import com.tengyei.org.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public PageResult<UserVO> page(long page, long size, String keyword, Long deptId, Long roleId) {
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        qw.eq(User::getIsSuperAdmin, 0);
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getRealName, keyword)
                    .or().like(User::getPhone, keyword));
        }
        if (deptId != null) qw.eq(User::getDeptId, deptId);
        qw.orderByDesc(User::getId);
        Page<User> result = userMapper.selectPage(new Page<>(page, size), qw);

        List<UserVO> vos = new ArrayList<>();
        for (User u : result.getRecords()) {
            List<Long> roleIds = jdbcTemplate.queryForList(
                "SELECT role_id FROM user_role WHERE user_id = ?", Long.class, u.getId());
            List<String> roleNames = roleIds.isEmpty() ? List.of() :
                jdbcTemplate.queryForList(
                    "SELECT name FROM role WHERE id IN (" +
                    String.join(",", roleIds.stream().map(String::valueOf).toList()) + ")",
                    String.class);
            if (roleId != null && !roleIds.contains(roleId)) continue;
            vos.add(UserVO.builder()
                    .id(u.getId()).username(u.getUsername()).realName(u.getRealName())
                    .phone(u.getPhone()).email(u.getEmail()).deptId(u.getDeptId())
                    .branchId(u.getBranchId()).status(u.getStatus())
                    .roleIds(roleIds).roleNames(roleNames).build());
        }
        return PageResult.of(vos, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Transactional
    public Long create(UserCreateDTO dto) {
        Long count = userMapper.selectCount(
            new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        // username is globally unique; also check across tenants via raw query
        Long global = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user WHERE username = ?", Long.class, dto.getUsername());
        if ((count != null && count > 0) || (global != null && global > 0)) {
            throw new BusinessException(409, "用户名已存在");
        }
        Long tenantId = TenantContext.getTenantId();
        User u = new User();
        u.setTenantId(tenantId);
        u.setUsername(dto.getUsername());
        u.setPassword(passwordEncoder.encode(dto.getPassword()));
        u.setRealName(dto.getRealName());
        u.setPhone(dto.getPhone());
        u.setEmail(dto.getEmail());
        u.setDeptId(dto.getDeptId());
        u.setBranchId(dto.getBranchId());
        u.setIsSuperAdmin(0);
        u.setStatus(1);
        u.setPwdResetRequired(1);
        u.setLoginFailCount(0);
        userMapper.insert(u);
        u.setUserNo("U" + tenantId + "-" + String.format("%04d", u.getId()));
        userMapper.updateById(u);
        assignRoles(u.getId(), dto.getRoleIds());
        return u.getId();
    }

    public void update(Long id, UserUpdateDTO dto) {
        User u = requireUser(id);
        u.setRealName(dto.getRealName());
        u.setPhone(dto.getPhone());
        u.setEmail(dto.getEmail());
        u.setDeptId(dto.getDeptId());
        u.setBranchId(dto.getBranchId());
        userMapper.updateById(u);
    }

    public void changeStatus(Long id, Integer status) {
        User u = requireUser(id);
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(422, "状态值无效");
        }
        u.setStatus(status);
        userMapper.updateById(u);
    }

    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        requireUser(userId);
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id = ?", userId);
        if (roleIds != null) {
            for (Long rid : roleIds) {
                jdbcTemplate.update(
                    "INSERT INTO user_role (user_id, role_id, created_at) VALUES (?,?,NOW())",
                    userId, rid);
            }
        }
    }

    public void resetPassword(Long id, String newPassword) {
        User u = requireUser(id);
        if (!StringUtils.hasText(newPassword)) throw new BusinessException(422, "新密码不能为空");
        u.setPassword(passwordEncoder.encode(newPassword));
        u.setPwdResetRequired(1);
        u.setLoginFailCount(0);
        u.setLockedUntil(null);
        userMapper.updateById(u);
    }

    private User requireUser(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw new BusinessException(404, "用户不存在");
        return u;
    }
}
```

> Note: `resetPassword` sets `lockedUntil = null`. `updateById` ignores null fields by default in MyBatis-Plus, so the lock is not cleared by the entity update. If clearing the lock is required, the implementer should add an explicit `jdbcTemplate.update("UPDATE user SET locked_until = NULL WHERE id = ?", id)` after the `updateById` call. Include that line.

- [ ] **Step 7: Create the `UserController`**

`core-org/src/main/java/com/tengyei/org/controller/UserController.java`:

```java
package com.tengyei.org.controller;

import com.tengyei.common.response.PageResult;
import com.tengyei.common.response.Result;
import com.tengyei.org.dto.UserCreateDTO;
import com.tengyei.org.dto.UserUpdateDTO;
import com.tengyei.org.dto.UserVO;
import com.tengyei.org.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_user:view')")
    public Result<PageResult<UserVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Long roleId) {
        return Result.ok(userService.page(page, size, keyword, deptId, roleId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_user:create')")
    public Result<Map<String, Long>> create(@Valid @RequestBody UserCreateDTO dto) {
        return Result.ok(Map.of("id", userService.create(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_user:edit')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO dto) {
        userService.update(id, dto);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PERM_user:edit')")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        userService.changeStatus(id, body.get("status"));
        return Result.ok();
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('PERM_user:edit')")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        userService.assignRoles(id, body.get("roleIds"));
        return Result.ok();
    }

    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('PERM_user:reset_pwd')")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.resetPassword(id, body.get("password"));
        return Result.ok();
    }
}
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `mvn -q -pl app -am test -Dtest=UserControllerTest`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add tengyei-backend/core-org/src/main/java/com/tengyei/org/entity/User.java tengyei-backend/core-org/src/main/java/com/tengyei/org/mapper/UserMapper.java tengyei-backend/core-org/src/main/java/com/tengyei/org/dto/User*.java tengyei-backend/core-org/src/main/java/com/tengyei/org/service/UserService.java tengyei-backend/core-org/src/main/java/com/tengyei/org/controller/UserController.java app/src/test/java/com/tengyei/UserControllerTest.java
git commit -m "feat(core-org): user CRUD, role assignment, password reset"
```

---

## Task 6: core-rbac — Role & Permission management

**Files:**
- Create: `core-rbac/src/main/java/com/tengyei/rbac/entity/Role.java`
- Create: `core-rbac/src/main/java/com/tengyei/rbac/entity/Permission.java`
- Create: `core-rbac/src/main/java/com/tengyei/rbac/mapper/RoleMapper.java`
- Create: `core-rbac/src/main/java/com/tengyei/rbac/mapper/PermissionMapper.java`
- Create: `core-rbac/src/main/java/com/tengyei/rbac/dto/RoleSaveDTO.java`
- Create: `core-rbac/src/main/java/com/tengyei/rbac/dto/RoleVO.java`
- Create: `core-rbac/src/main/java/com/tengyei/rbac/dto/PermissionGroupVO.java`
- Create: `core-rbac/src/main/java/com/tengyei/rbac/service/RoleService.java`
- Create: `core-rbac/src/main/java/com/tengyei/rbac/service/PermissionService.java`
- Create: `core-rbac/src/main/java/com/tengyei/rbac/controller/RoleController.java`
- Create: `core-rbac/src/main/java/com/tengyei/rbac/controller/PermissionController.java`
- Test: `app/src/test/java/com/tengyei/RoleControllerTest.java`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/tengyei/RoleControllerTest.java`:

```java
package com.tengyei;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class RoleControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    private String token;

    @BeforeEach
    void setup() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbcTemplate);
        token = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());
    }

    @Test
    void permissions_grouped_by_module() throws Exception {
        mockMvc.perform(get("/api/v1/permissions")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].module").exists())
                .andExpect(jsonPath("$.data[0].permissions").isArray());
    }

    @Test
    void create_role_then_assign_permissions() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/roles")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"部门经理\",\"code\":\"dept_mgr\",\"dataScope\":\"dept\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long roleId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        Long permId = jdbcTemplate.queryForObject(
            "SELECT id FROM permission WHERE code = 'user:view'", Long.class);

        mockMvc.perform(put("/api/v1/roles/" + roleId + "/permissions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissionIds\":[" + permId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/roles/" + roleId + "/permissions")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value(permId.intValue()));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl app -am test -Dtest=RoleControllerTest`
Expected: FAIL — `/api/v1/roles` and `/api/v1/permissions` not found.

- [ ] **Step 3: Create the entities**

`core-rbac/src/main/java/com/tengyei/rbac/entity/Role.java`:

```java
package com.tengyei.rbac.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tengyei.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("role")
public class Role extends BaseEntity {
    private Long tenantId;
    private String name;
    private String code;
    private String description;
    /** all | branch | dept | self */
    private String dataScope;
    private Integer isPreset;
    private Integer status;
}
```

`core-rbac/src/main/java/com/tengyei/rbac/entity/Permission.java`:

```java
package com.tengyei.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("permission")
public class Permission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String module;
    private String code;
    private String name;
    private String description;
    private Integer sortOrder;
    private Integer status;
}
```

> `Permission` does NOT extend `BaseEntity` — the `permission` table has no `is_deleted`/`created_at`/`updated_at`/`tenant_id` columns.

- [ ] **Step 4: Create the mappers**

`core-rbac/src/main/java/com/tengyei/rbac/mapper/RoleMapper.java`:

```java
package com.tengyei.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tengyei.rbac.entity.Role;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}
```

`core-rbac/src/main/java/com/tengyei/rbac/mapper/PermissionMapper.java`:

```java
package com.tengyei.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tengyei.rbac.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}
```

- [ ] **Step 5: Create the DTOs**

`core-rbac/src/main/java/com/tengyei/rbac/dto/RoleSaveDTO.java`:

```java
package com.tengyei.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RoleSaveDTO {
    @NotBlank(message = "角色名称不能为空")
    private String name;
    @NotBlank(message = "角色编码不能为空")
    private String code;
    private String description;
    /** all | branch | dept | self */
    private String dataScope;
}
```

`core-rbac/src/main/java/com/tengyei/rbac/dto/RoleVO.java`:

```java
package com.tengyei.rbac.dto;

import com.tengyei.rbac.entity.Role;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RoleVO {
    private Long id;
    private String name;
    private String code;
    private String description;
    private String dataScope;
    private Integer isPreset;
    private Integer status;

    public static RoleVO from(Role r) {
        return RoleVO.builder()
                .id(r.getId()).name(r.getName()).code(r.getCode())
                .description(r.getDescription()).dataScope(r.getDataScope())
                .isPreset(r.getIsPreset()).status(r.getStatus()).build();
    }
}
```

`core-rbac/src/main/java/com/tengyei/rbac/dto/PermissionGroupVO.java`:

```java
package com.tengyei.rbac.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PermissionGroupVO {
    private String module;
    private List<Item> permissions;

    @Getter
    @Builder
    public static class Item {
        private Long id;
        private String code;
        private String name;
    }
}
```

- [ ] **Step 6: Create the `PermissionService`**

`core-rbac/src/main/java/com/tengyei/rbac/service/PermissionService.java`:

```java
package com.tengyei.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tengyei.rbac.dto.PermissionGroupVO;
import com.tengyei.rbac.entity.Permission;
import com.tengyei.rbac.mapper.PermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionMapper permissionMapper;

    public List<PermissionGroupVO> grouped() {
        List<Permission> all = permissionMapper.selectList(
            new LambdaQueryWrapper<Permission>()
                .eq(Permission::getStatus, 1)
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
}
```

- [ ] **Step 7: Create the `RoleService`**

`core-rbac/src/main/java/com/tengyei/rbac/service/RoleService.java`:

```java
package com.tengyei.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.exception.BusinessException;
import com.tengyei.rbac.dto.RoleSaveDTO;
import com.tengyei.rbac.dto.RoleVO;
import com.tengyei.rbac.entity.Role;
import com.tengyei.rbac.mapper.RoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleMapper roleMapper;
    private final JdbcTemplate jdbcTemplate;

    public List<RoleVO> list() {
        return roleMapper.selectList(
                new LambdaQueryWrapper<Role>().orderByAsc(Role::getId))
            .stream().map(RoleVO::from).toList();
    }

    public Long create(RoleSaveDTO dto) {
        Long dup = roleMapper.selectCount(
            new LambdaQueryWrapper<Role>().eq(Role::getCode, dto.getCode()));
        if (dup != null && dup > 0) throw new BusinessException(409, "角色编码已存在");
        Role r = new Role();
        r.setTenantId(TenantContext.getTenantId());
        r.setName(dto.getName());
        r.setCode(dto.getCode());
        r.setDescription(dto.getDescription());
        r.setDataScope(dto.getDataScope() != null ? dto.getDataScope() : "self");
        r.setIsPreset(0);
        r.setStatus(1);
        roleMapper.insert(r);
        return r.getId();
    }

    public void update(Long id, RoleSaveDTO dto) {
        Role r = requireRole(id);
        r.setName(dto.getName());
        r.setDescription(dto.getDescription());
        r.setDataScope(dto.getDataScope());
        roleMapper.updateById(r);
    }

    public void delete(Long id) {
        Role r = requireRole(id);
        if (r.getIsPreset() != null && r.getIsPreset() == 1) {
            throw new BusinessException(409, "预设角色不可删除");
        }
        Long inUse = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_role WHERE role_id = ?", Long.class, id);
        if (inUse != null && inUse > 0) throw new BusinessException(409, "角色已分配用户，无法删除");
        roleMapper.deleteById(id);
    }

    public List<Long> permissionIds(Long roleId) {
        requireRole(roleId);
        return jdbcTemplate.queryForList(
            "SELECT permission_id FROM role_permission WHERE role_id = ?", Long.class, roleId);
    }

    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        requireRole(roleId);
        jdbcTemplate.update("DELETE FROM role_permission WHERE role_id = ?", roleId);
        if (permissionIds != null) {
            for (Long pid : permissionIds) {
                jdbcTemplate.update(
                    "INSERT INTO role_permission (role_id, permission_id, created_at) VALUES (?,?,NOW())",
                    roleId, pid);
            }
        }
    }

    private Role requireRole(Long id) {
        Role r = roleMapper.selectById(id);
        if (r == null) throw new BusinessException(404, "角色不存在");
        return r;
    }
}
```

- [ ] **Step 8: Create the controllers**

`core-rbac/src/main/java/com/tengyei/rbac/controller/RoleController.java`:

```java
package com.tengyei.rbac.controller;

import com.tengyei.common.response.Result;
import com.tengyei.rbac.dto.RoleSaveDTO;
import com.tengyei.rbac.dto.RoleVO;
import com.tengyei.rbac.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_role:view')")
    public Result<List<RoleVO>> list() {
        return Result.ok(roleService.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_role:create')")
    public Result<Map<String, Long>> create(@Valid @RequestBody RoleSaveDTO dto) {
        return Result.ok(Map.of("id", roleService.create(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_role:edit')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RoleSaveDTO dto) {
        roleService.update(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_role:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.ok();
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('PERM_role:view')")
    public Result<List<Long>> permissionIds(@PathVariable Long id) {
        return Result.ok(roleService.permissionIds(id));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('PERM_role:edit')")
    public Result<Void> assignPermissions(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        roleService.assignPermissions(id, body.get("permissionIds"));
        return Result.ok();
    }
}
```

`core-rbac/src/main/java/com/tengyei/rbac/controller/PermissionController.java`:

```java
package com.tengyei.rbac.controller;

import com.tengyei.common.response.Result;
import com.tengyei.rbac.dto.PermissionGroupVO;
import com.tengyei.rbac.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_role:view')")
    public Result<List<PermissionGroupVO>> grouped() {
        return Result.ok(permissionService.grouped());
    }
}
```

- [ ] **Step 9: Run the test to verify it passes**

Run: `mvn -q -pl app -am test -Dtest=RoleControllerTest`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add tengyei-backend/core-rbac/src/main/java app/src/test/java/com/tengyei/RoleControllerTest.java
git commit -m "feat(core-rbac): role CRUD, permission matrix, role-permission assignment"
```

---

## Task 7: app — Dashboard stats API + align UserInfo route paths

**Files:**
- Create: `app/src/main/java/com/tengyei/service/DashboardService.java`
- Create: `app/src/main/java/com/tengyei/controller/DashboardController.java`
- Modify: `app/src/main/java/com/tengyei/controller/UserInfoController.java`
- Test: `app/src/test/java/com/tengyei/DashboardControllerTest.java`

> **Route path alignment:** the frontend router (Task 9) registers `/dashboard`, `/admin/companies`, `/company/org`, `/company/users`, `/company/roles`. `UserInfoController` must emit these exact paths so the sidebar links resolve.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/tengyei/DashboardControllerTest.java`:

```java
package com.tengyei;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tengyei.auth.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class DashboardControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void super_admin_dashboard_returns_company_stats() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("superadmin");
        req.setPassword("Admin@2026");
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        String token = objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        mockMvc.perform(get("/api/v1/dashboard/stats")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.scope").value("super"))
                .andExpect(jsonPath("$.data.companyTotal").isNumber());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl app -am test -Dtest=DashboardControllerTest`
Expected: FAIL — `/api/v1/dashboard/stats` not found.

- [ ] **Step 3: Create the `DashboardService`**

Uses `JdbcTemplate` (bypasses the tenant interceptor), so company-admin queries filter by `tenant_id` explicitly.

`app/src/main/java/com/tengyei/service/DashboardService.java`:

```java
package com.tengyei.service;

import com.tengyei.common.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final JdbcTemplate jdbcTemplate;

    public Map<String, Object> stats() {
        Map<String, Object> data = new HashMap<>();
        if (TenantContext.isSuperAdmin()) {
            data.put("scope", "super");
            data.put("companyTotal", count("SELECT COUNT(*) FROM company WHERE is_deleted = 0"));
            data.put("companyActive", count(
                "SELECT COUNT(*) FROM company WHERE is_deleted = 0 AND status = 1"));
            data.put("companyTodayNew", count(
                "SELECT COUNT(*) FROM company WHERE is_deleted = 0 AND created_at >= CURRENT_DATE"));
            data.put("userTotal", count(
                "SELECT COUNT(*) FROM user WHERE is_deleted = 0 AND is_super_admin = 0"));
            List<Map<String, Object>> recent = jdbcTemplate.queryForList(
                "SELECT id, company_no, full_name, short_name, status, created_at " +
                "FROM company WHERE is_deleted = 0 ORDER BY id DESC LIMIT 5");
            data.put("recentCompanies", recent);
        } else {
            Long tenantId = TenantContext.getTenantId();
            data.put("scope", "company");
            data.put("deptCount", count(
                "SELECT COUNT(*) FROM dept WHERE is_deleted = 0 AND tenant_id = ?", tenantId));
            data.put("branchCount", count(
                "SELECT COUNT(*) FROM branch WHERE is_deleted = 0 AND tenant_id = ?", tenantId));
            data.put("userCount", count(
                "SELECT COUNT(*) FROM user WHERE is_deleted = 0 AND is_super_admin = 0 AND tenant_id = ?",
                tenantId));
            data.put("todayLoginCount", count(
                "SELECT COUNT(*) FROM user WHERE is_deleted = 0 AND tenant_id = ? " +
                "AND last_login_at >= CURRENT_DATE", tenantId));
        }
        return data;
    }

    private long count(String sql, Object... args) {
        Long n = jdbcTemplate.queryForObject(sql, Long.class, args);
        return n != null ? n : 0L;
    }
}
```

- [ ] **Step 4: Create the `DashboardController`**

`app/src/main/java/com/tengyei/controller/DashboardController.java`:

```java
package com.tengyei.controller;

import com.tengyei.common.response.Result;
import com.tengyei.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(dashboardService.stats());
    }
}
```

- [ ] **Step 5: Align route paths in `UserInfoController`**

Replace the `buildSuperAdminRoutes()` and `buildCompanyRoutes()` methods in `app/src/main/java/com/tengyei/controller/UserInfoController.java` with:

```java
    private List<UserInfoVO.RouteVO> buildSuperAdminRoutes() {
        return List.of(
            route("/dashboard", "工作台"),
            route("/admin/companies", "企业管理")
        );
    }

    private List<UserInfoVO.RouteVO> buildCompanyRoutes(List<String> permissions) {
        List<UserInfoVO.RouteVO> routes = new ArrayList<>();
        routes.add(route("/dashboard", "工作台"));
        if (hasAny(permissions, "dept:view", "branch:view")) routes.add(route("/company/org", "组织管理"));
        if (hasAny(permissions, "user:view")) routes.add(route("/company/users", "人员管理"));
        if (hasAny(permissions, "role:view")) routes.add(route("/company/roles", "角色与权限"));
        return routes;
    }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn -q -pl app -am test -Dtest=DashboardControllerTest`
Expected: PASS.

- [ ] **Step 7: Run the full backend test suite (regression)**

Run: `mvn -q -pl app -am test`
Expected: All test classes green (Auth, Company, Dept, Branch, User, Role, Dashboard).

- [ ] **Step 8: Commit**

```bash
git add tengyei-backend/app/src/main/java/com/tengyei/service/DashboardService.java tengyei-backend/app/src/main/java/com/tengyei/controller/DashboardController.java tengyei-backend/app/src/main/java/com/tengyei/controller/UserInfoController.java app/src/test/java/com/tengyei/DashboardControllerTest.java
git commit -m "feat(app): dashboard stats API and aligned sidebar route paths"
```

---

## Task 8: Frontend data layer — types, API modules, tab store

**Files:**
- Create: `tengyei-frontend/src/types/common.ts`, `company.ts`, `org.ts`, `user.ts`, `rbac.ts`, `dashboard.ts`
- Create: `tengyei-frontend/src/api/company.ts`, `org.ts`, `user.ts`, `rbac.ts`, `dashboard.ts`
- Create: `tengyei-frontend/src/stores/tab.ts`

> All API functions follow the existing `src/api/request.ts` pattern, where the response interceptor already unwraps the `Result` envelope and returns `data.data`. Generic signature `request.get<never, T>(...)` types the unwrapped payload.

- [ ] **Step 1: Create shared types** — `tengyei-frontend/src/types/common.ts`

```ts
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export interface IdResult {
  id: number
}
```

- [ ] **Step 2: Create domain types**

`tengyei-frontend/src/types/company.ts`:

```ts
export interface CompanyVO {
  id: number
  companyNo: string
  fullName: string
  shortName: string
  adminName: string
  adminPhone: string
  status: number
  createdAt: string
}

export interface CompanyCreateDTO {
  fullName: string
  shortName: string
  creditCode?: string
  adminName: string
  adminPhone: string
  adminEmail?: string
  adminUsername: string
  adminPassword: string
}

export interface CompanyUpdateDTO {
  fullName: string
  shortName: string
  creditCode?: string
  adminName?: string
  adminPhone?: string
  adminEmail?: string
  remark?: string
}
```

`tengyei-frontend/src/types/org.ts`:

```ts
export interface DeptTreeVO {
  id: number
  name: string
  code?: string
  parentId: number
  leaderId?: number
  sortOrder: number
  status: number
  children: DeptTreeVO[]
}

export interface DeptSaveDTO {
  name: string
  code?: string
  parentId?: number
  leaderId?: number
  sortOrder?: number
}

export interface BranchVO {
  id: number
  branchNo: string
  name: string
  type: string
  leaderId?: number
  phone?: string
  city?: string
  status: number
}

export interface BranchSaveDTO {
  branchNo: string
  name: string
  type?: string
  province?: string
  city?: string
  district?: string
  address?: string
  leaderId?: number
  phone?: string
  maxUsers?: number
}
```

`tengyei-frontend/src/types/user.ts`:

```ts
export interface UserVO {
  id: number
  username: string
  realName: string
  phone: string
  email?: string
  deptId?: number
  branchId?: number
  status: number
  roleIds: number[]
  roleNames: string[]
}

export interface UserCreateDTO {
  username: string
  realName: string
  phone: string
  email?: string
  password: string
  deptId?: number
  branchId?: number
  roleIds?: number[]
}

export interface UserUpdateDTO {
  realName: string
  phone: string
  email?: string
  deptId?: number
  branchId?: number
}
```

`tengyei-frontend/src/types/rbac.ts`:

```ts
export interface RoleVO {
  id: number
  name: string
  code: string
  description?: string
  dataScope: string
  isPreset: number
  status: number
}

export interface RoleSaveDTO {
  name: string
  code: string
  description?: string
  dataScope?: string
}

export interface PermissionItem {
  id: number
  code: string
  name: string
}

export interface PermissionGroupVO {
  module: string
  permissions: PermissionItem[]
}
```

`tengyei-frontend/src/types/dashboard.ts`:

```ts
export interface RecentCompany {
  id: number
  companyNo: string
  fullName: string
  shortName: string
  status: number
  createdAt: string
}

export interface DashboardStats {
  scope: 'super' | 'company'
  companyTotal?: number
  companyActive?: number
  companyTodayNew?: number
  userTotal?: number
  recentCompanies?: RecentCompany[]
  deptCount?: number
  branchCount?: number
  userCount?: number
  todayLoginCount?: number
}
```

- [ ] **Step 3: Create API modules**

`tengyei-frontend/src/api/company.ts`:

```ts
import request from './request'
import type { PageResult, IdResult } from '@/types/common'
import type { CompanyVO, CompanyCreateDTO, CompanyUpdateDTO } from '@/types/company'

export const companyApi = {
  page: (params: { page: number; size: number; keyword?: string }) =>
    request.get<never, PageResult<CompanyVO>>('/v1/companies', { params }),
  detail: (id: number) => request.get<never, CompanyVO>(`/v1/companies/${id}`),
  create: (data: CompanyCreateDTO) => request.post<never, IdResult>('/v1/companies', data),
  update: (id: number, data: CompanyUpdateDTO) =>
    request.put<never, void>(`/v1/companies/${id}`, data),
  changeStatus: (id: number, status: number) =>
    request.put<never, void>(`/v1/companies/${id}/status`, { status }),
}
```

`tengyei-frontend/src/api/org.ts`:

```ts
import request from './request'
import type { PageResult, IdResult } from '@/types/common'
import type { DeptTreeVO, DeptSaveDTO, BranchVO, BranchSaveDTO } from '@/types/org'

export const deptApi = {
  tree: () => request.get<never, DeptTreeVO[]>('/v1/depts/tree'),
  create: (data: DeptSaveDTO) => request.post<never, IdResult>('/v1/depts', data),
  update: (id: number, data: DeptSaveDTO) => request.put<never, void>(`/v1/depts/${id}`, data),
  remove: (id: number) => request.delete<never, void>(`/v1/depts/${id}`),
}

export const branchApi = {
  page: (params: { page: number; size: number }) =>
    request.get<never, PageResult<BranchVO>>('/v1/branches', { params }),
  create: (data: BranchSaveDTO) => request.post<never, IdResult>('/v1/branches', data),
  update: (id: number, data: BranchSaveDTO) => request.put<never, void>(`/v1/branches/${id}`, data),
  changeStatus: (id: number, status: number) =>
    request.put<never, void>(`/v1/branches/${id}/status`, { status }),
}
```

`tengyei-frontend/src/api/user.ts`:

```ts
import request from './request'
import type { PageResult, IdResult } from '@/types/common'
import type { UserVO, UserCreateDTO, UserUpdateDTO } from '@/types/user'

export const userApi = {
  page: (params: {
    page: number
    size: number
    keyword?: string
    deptId?: number
    roleId?: number
  }) => request.get<never, PageResult<UserVO>>('/v1/users', { params }),
  create: (data: UserCreateDTO) => request.post<never, IdResult>('/v1/users', data),
  update: (id: number, data: UserUpdateDTO) => request.put<never, void>(`/v1/users/${id}`, data),
  changeStatus: (id: number, status: number) =>
    request.put<never, void>(`/v1/users/${id}/status`, { status }),
  assignRoles: (id: number, roleIds: number[]) =>
    request.put<never, void>(`/v1/users/${id}/roles`, { roleIds }),
  resetPassword: (id: number, password: string) =>
    request.put<never, void>(`/v1/users/${id}/reset-password`, { password }),
}
```

`tengyei-frontend/src/api/rbac.ts`:

```ts
import request from './request'
import type { IdResult } from '@/types/common'
import type { RoleVO, RoleSaveDTO, PermissionGroupVO } from '@/types/rbac'

export const roleApi = {
  list: () => request.get<never, RoleVO[]>('/v1/roles'),
  create: (data: RoleSaveDTO) => request.post<never, IdResult>('/v1/roles', data),
  update: (id: number, data: RoleSaveDTO) => request.put<never, void>(`/v1/roles/${id}`, data),
  remove: (id: number) => request.delete<never, void>(`/v1/roles/${id}`),
  permissionIds: (id: number) => request.get<never, number[]>(`/v1/roles/${id}/permissions`),
  assignPermissions: (id: number, permissionIds: number[]) =>
    request.put<never, void>(`/v1/roles/${id}/permissions`, { permissionIds }),
}

export const permissionApi = {
  grouped: () => request.get<never, PermissionGroupVO[]>('/v1/permissions'),
}
```

`tengyei-frontend/src/api/dashboard.ts`:

```ts
import request from './request'
import type { DashboardStats } from '@/types/dashboard'

export const dashboardApi = {
  stats: () => request.get<never, DashboardStats>('/v1/dashboard/stats'),
}
```

- [ ] **Step 4: Create the tab store** — `tengyei-frontend/src/stores/tab.ts`

```ts
import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface TabItem {
  path: string
  title: string
  closable: boolean
}

const STORAGE_KEY = 'open_tabs'
const HOME: TabItem = { path: '/dashboard', title: '工作台', closable: false }

function load(): TabItem[] {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as TabItem[]
      if (Array.isArray(parsed) && parsed.length > 0) return parsed
    }
  } catch {
    // ignore corrupt storage
  }
  return [{ ...HOME }]
}

export const useTabStore = defineStore('tab', () => {
  const tabs = ref<TabItem[]>(load())
  const activePath = ref<string>(tabs.value[0]?.path ?? HOME.path)

  function persist() {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(tabs.value))
  }

  function openTab(tab: TabItem) {
    if (!tabs.value.some((t) => t.path === tab.path)) {
      tabs.value.push(tab)
      persist()
    }
    activePath.value = tab.path
  }

  function closeTab(path: string): string {
    const idx = tabs.value.findIndex((t) => t.path === path)
    if (idx === -1) return activePath.value
    if (!tabs.value[idx].closable) return activePath.value
    tabs.value.splice(idx, 1)
    if (activePath.value === path) {
      const next = tabs.value[idx - 1] ?? tabs.value[idx] ?? tabs.value[0]
      activePath.value = next.path
    }
    persist()
    return activePath.value
  }

  function closeOthers(path: string) {
    tabs.value = tabs.value.filter((t) => t.path === path || !t.closable)
    activePath.value = path
    persist()
  }

  function closeAll(): string {
    tabs.value = tabs.value.filter((t) => !t.closable)
    activePath.value = tabs.value[0]?.path ?? HOME.path
    persist()
    return activePath.value
  }

  function setActive(path: string) {
    activePath.value = path
  }

  function reset() {
    tabs.value = [{ ...HOME }]
    activePath.value = HOME.path
    sessionStorage.removeItem(STORAGE_KEY)
  }

  return { tabs, activePath, openTab, closeTab, closeOthers, closeAll, setActive, reset }
})
```

- [ ] **Step 5: Type-check / build to verify**

Run: `npm --prefix tengyei-frontend run build`
Expected: BUILD SUCCESS — new types/api/store files compile; existing app unchanged.

- [ ] **Step 6: Commit**

```bash
git add tengyei-frontend/src/types tengyei-frontend/src/api tengyei-frontend/src/stores/tab.ts
git commit -m "feat(frontend): data layer — types, api modules, tab store"
```

---

## Task 9: Frontend shell — MainLayout, Sidebar, Header, TabBar, router

**Files:**
- Create: `tengyei-frontend/src/layout/MainLayout.vue`
- Create: `tengyei-frontend/src/layout/AppSidebar.vue`
- Create: `tengyei-frontend/src/layout/AppHeader.vue`
- Create: `tengyei-frontend/src/layout/AppTabBar.vue`
- Create stubs: `tengyei-frontend/src/views/company/CompanyListView.vue`, `src/views/org/OrgView.vue`, `src/views/user/UserListView.vue`, `src/views/role/RoleView.vue`
- Modify: `tengyei-frontend/src/router/index.ts`

> Element Plus components auto-import via the configured resolver, but **icons from `@element-plus/icons-vue` are NOT auto-registered** — import each icon explicitly in the component that uses it.

- [ ] **Step 1: Create the four stub views** (replaced with real implementations in Tasks 11–14)

`tengyei-frontend/src/views/company/CompanyListView.vue`:
```vue
<template>
  <div class="page-placeholder">企业管理（建设中）</div>
</template>
```

`tengyei-frontend/src/views/org/OrgView.vue`:
```vue
<template>
  <div class="page-placeholder">组织管理（建设中）</div>
</template>
```

`tengyei-frontend/src/views/user/UserListView.vue`:
```vue
<template>
  <div class="page-placeholder">人员管理（建设中）</div>
</template>
```

`tengyei-frontend/src/views/role/RoleView.vue`:
```vue
<template>
  <div class="page-placeholder">角色与权限（建设中）</div>
</template>
```

- [ ] **Step 2: Create `AppSidebar.vue`**

`tengyei-frontend/src/layout/AppSidebar.vue`:

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { HomeFilled, OfficeBuilding, Share, User, Lock } from '@element-plus/icons-vue'
import type { Component } from 'vue'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const iconMap: Record<string, Component> = {
  '/dashboard': HomeFilled,
  '/admin/companies': OfficeBuilding,
  '/company/org': Share,
  '/company/users': User,
  '/company/roles': Lock,
}

const menuRoutes = computed(() => auth.routes)
const activePath = computed(() => route.path)

function go(path: string) {
  if (route.path !== path) router.push(path)
}
</script>

<template>
  <aside class="sidebar">
    <div class="brand">
      <div class="brand-logo">腾</div>
      <span class="brand-text">腾飞企业管理</span>
    </div>
    <el-menu
      class="sidebar-menu"
      :default-active="activePath"
      background-color="transparent"
      text-color="#c9ced6"
      active-text-color="#ffffff"
      @select="go"
    >
      <el-menu-item v-for="r in menuRoutes" :key="r.path" :index="r.path">
        <el-icon v-if="iconMap[r.path]"><component :is="iconMap[r.path]" /></el-icon>
        <span>{{ r.name }}</span>
      </el-menu-item>
    </el-menu>
    <div class="sidebar-footer">v2.0</div>
  </aside>
</template>

<style scoped>
.sidebar {
  height: 100vh;
  background: var(--sidebar-bg, #0f1117);
  display: flex;
  flex-direction: column;
  border-right: 1px solid #1e2130;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 16px;
  border-bottom: 1px solid #1e2130;
}
.brand-logo {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--color-primary, #3b82f6);
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.brand-text {
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}
.sidebar-menu {
  flex: 1;
  border-right: none;
  padding-top: 8px;
}
.sidebar-footer {
  padding: 12px 16px;
  color: #5b6472;
  font-size: 12px;
  border-top: 1px solid #1e2130;
}
:deep(.el-menu-item.is-active) {
  background: rgba(59, 130, 246, 0.15) !important;
  border-left: 2px solid var(--color-primary, #3b82f6);
}
</style>
```

- [ ] **Step 3: Create `AppHeader.vue`**

`tengyei-frontend/src/layout/AppHeader.vue`:

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useTabStore } from '@/stores/tab'

const auth = useAuthStore()
const tabStore = useTabStore()
const route = useRoute()
const router = useRouter()

const currentTitle = computed(() => (route.meta.title as string) || '工作台')
const realName = computed(() => auth.userInfo?.realName ?? '用户')

function onCommand(command: string) {
  if (command === 'logout') handleLogout()
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确认退出登录？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  await auth.logout()
  tabStore.reset()
  router.push('/login')
}
</script>

<template>
  <header class="app-header">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item>首页</el-breadcrumb-item>
      <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
    </el-breadcrumb>
    <el-dropdown @command="onCommand">
      <span class="user-trigger">
        <el-avatar :size="28" class="user-avatar">{{ realName.charAt(0) }}</el-avatar>
        <span class="user-name">{{ realName }}</span>
      </span>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="logout">退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </header>
</template>

<style scoped>
.app-header {
  height: 56px;
  flex-shrink: 0;
  background: #fff;
  border-bottom: 1px solid #e4e7ec;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}
.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}
.user-avatar {
  background: var(--color-primary, #3b82f6);
  color: #fff;
}
.user-name {
  font-size: 14px;
  color: #1f2937;
}
</style>
```

- [ ] **Step 4: Create `AppTabBar.vue`**

`tengyei-frontend/src/layout/AppTabBar.vue`:

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { useTabStore } from '@/stores/tab'
import { Close } from '@element-plus/icons-vue'

const tabStore = useTabStore()

const emit = defineEmits<{
  select: [path: string]
  close: [path: string]
  'close-others': [path: string]
  'close-all': []
}>()

const ctxVisible = ref(false)
const ctxX = ref(0)
const ctxY = ref(0)
const ctxPath = ref('')

function openCtx(e: MouseEvent, path: string) {
  ctxVisible.value = true
  ctxX.value = e.clientX
  ctxY.value = e.clientY
  ctxPath.value = path
}

function closeCtx() {
  ctxVisible.value = false
}
</script>

<template>
  <div class="tabbar" @click="closeCtx">
    <div
      v-for="tab in tabStore.tabs"
      :key="tab.path"
      class="tab"
      :class="{ active: tab.path === tabStore.activePath }"
      @click="emit('select', tab.path)"
      @contextmenu.prevent="openCtx($event, tab.path)"
    >
      <span class="tab-title">{{ tab.title }}</span>
      <el-icon v-if="tab.closable" class="tab-close" @click.stop="emit('close', tab.path)">
        <Close />
      </el-icon>
    </div>

    <ul v-if="ctxVisible" class="tab-context" :style="{ left: ctxX + 'px', top: ctxY + 'px' }">
      <li @click="emit('close', ctxPath)">关闭当前</li>
      <li @click="emit('close-others', ctxPath)">关闭其他</li>
      <li @click="emit('close-all')">关闭全部</li>
    </ul>
  </div>
</template>

<style scoped>
.tabbar {
  height: 40px;
  flex-shrink: 0;
  background: #fff;
  border-bottom: 1px solid #e4e7ec;
  display: flex;
  align-items: flex-end;
  gap: 4px;
  padding: 0 12px;
}
.tab {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 12px;
  border: 1px solid #e4e7ec;
  border-bottom: none;
  border-radius: 6px 6px 0 0;
  background: #f5f7fa;
  font-size: 13px;
  color: #4b5563;
  cursor: pointer;
}
.tab.active {
  background: var(--color-primary, #3b82f6);
  color: #fff;
  border-color: var(--color-primary, #3b82f6);
}
.tab-close {
  font-size: 12px;
  border-radius: 50%;
}
.tab-close:hover {
  background: rgba(0, 0, 0, 0.15);
}
.tab-context {
  position: fixed;
  z-index: 3000;
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  list-style: none;
  padding: 4px 0;
  margin: 0;
  min-width: 120px;
}
.tab-context li {
  padding: 8px 16px;
  font-size: 13px;
  color: #374151;
  cursor: pointer;
}
.tab-context li:hover {
  background: #f3f4f6;
}
</style>
```

- [ ] **Step 5: Create `MainLayout.vue`**

`tengyei-frontend/src/layout/MainLayout.vue`:

```vue
<script setup lang="ts">
import { watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTabStore } from '@/stores/tab'
import AppSidebar from './AppSidebar.vue'
import AppHeader from './AppHeader.vue'
import AppTabBar from './AppTabBar.vue'

const route = useRoute()
const router = useRouter()
const tabStore = useTabStore()

watch(
  () => route.path,
  (path) => {
    if (path === '/login' || path === '/403') return
    const title = (route.meta.title as string) || '页面'
    const closable = path !== '/dashboard'
    tabStore.openTab({ path, title, closable })
  },
  { immediate: true },
)

function onSelect(path: string) {
  tabStore.setActive(path)
  if (route.path !== path) router.push(path)
}

function onClose(path: string) {
  const next = tabStore.closeTab(path)
  if (route.path !== next) router.push(next)
}

function onCloseOthers(path: string) {
  tabStore.closeOthers(path)
  if (route.path !== path) router.push(path)
}

function onCloseAll() {
  const next = tabStore.closeAll()
  if (route.path !== next) router.push(next)
}
</script>

<template>
  <div class="main-layout">
    <AppSidebar class="layout-sidebar" />
    <div class="layout-body">
      <AppHeader />
      <AppTabBar
        @select="onSelect"
        @close="onClose"
        @close-others="onCloseOthers"
        @close-all="onCloseAll"
      />
      <main class="layout-content">
        <RouterView v-slot="{ Component }">
          <keep-alive>
            <component :is="Component" />
          </keep-alive>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<style scoped>
.main-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}
.layout-sidebar {
  width: var(--sidebar-width, 220px);
  flex-shrink: 0;
}
.layout-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--content-bg, #f5f7fa);
}
.layout-content {
  flex: 1;
  overflow: auto;
  padding: 16px;
}
</style>
```

- [ ] **Step 6: Rewrite `src/router/index.ts`**

```ts
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/',
      component: () => import('@/layout/MainLayout.vue'),
      meta: { requiresAuth: true },
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '工作台' },
        },
        {
          path: 'admin/companies',
          name: 'Companies',
          component: () => import('@/views/company/CompanyListView.vue'),
          meta: { title: '企业管理' },
        },
        {
          path: 'company/org',
          name: 'Org',
          component: () => import('@/views/org/OrgView.vue'),
          meta: { title: '组织管理' },
        },
        {
          path: 'company/users',
          name: 'Users',
          component: () => import('@/views/user/UserListView.vue'),
          meta: { title: '人员管理' },
        },
        {
          path: 'company/roles',
          name: 'Roles',
          component: () => import('@/views/role/RoleView.vue'),
          meta: { title: '角色与权限' },
        },
      ],
    },
    { path: '/403', name: 'Forbidden', component: () => import('@/views/403View.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/403' },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.guestOnly && auth.isLoggedIn) return '/dashboard'
  if (to.meta.requiresAuth && !auth.isLoggedIn) return '/login'
  if (auth.isLoggedIn && !auth.userInfo) {
    try {
      await auth.fetchUserInfo()
    } catch {
      await auth.logout()
      return '/login'
    }
  }
  return true
})

export default router
```

- [ ] **Step 7: Build to verify**

Run: `npm --prefix tengyei-frontend run build`
Expected: BUILD SUCCESS — all router-referenced components resolve (stubs + existing views).

- [ ] **Step 8: Manual smoke test**

Start backend (`mvn -q -pl app -am spring-boot:run "-Dspring-boot.run.profiles=dev"`) and frontend (`npm --prefix tengyei-frontend run dev`). Log in as `superadmin` / `Admin@2026`. Verify: dark sidebar shows 工作台 + 企业管理; clicking 企业管理 opens a closable tab; the 工作台 tab cannot be closed; refresh keeps tabs (sessionStorage).

- [ ] **Step 9: Commit**

```bash
git add tengyei-frontend/src/layout tengyei-frontend/src/views/company tengyei-frontend/src/views/org tengyei-frontend/src/views/user tengyei-frontend/src/views/role tengyei-frontend/src/router/index.ts tengyei-frontend/src/components.d.ts
git commit -m "feat(frontend): main layout shell — sidebar, header, multi-tab bar, nested router"
```

---

## Task 10: Frontend — Dashboard views (super admin / company admin)

**Files:**
- Create: `tengyei-frontend/src/views/dashboard/SuperDashboard.vue`
- Create: `tengyei-frontend/src/views/dashboard/CompanyDashboard.vue`
- Modify: `tengyei-frontend/src/views/DashboardView.vue` (becomes a dispatcher)

- [ ] **Step 1: Create `SuperDashboard.vue`**

`tengyei-frontend/src/views/dashboard/SuperDashboard.vue`:

```vue
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { dashboardApi } from '@/api/dashboard'
import type { DashboardStats } from '@/types/dashboard'

const stats = ref<DashboardStats | null>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    stats.value = await dashboardApi.stats()
  } finally {
    loading.value = false
  }
})

const statusText = (s: number) => (s === 1 ? '启用' : s === 2 ? '停用' : '待激活')
</script>

<template>
  <div v-loading="loading" class="dashboard">
    <div class="stat-cards">
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">企业总数</div>
        <div class="stat-value">{{ stats?.companyTotal ?? 0 }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">活跃企业</div>
        <div class="stat-value">{{ stats?.companyActive ?? 0 }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">今日新增企业</div>
        <div class="stat-value">{{ stats?.companyTodayNew ?? 0 }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">总用户数</div>
        <div class="stat-value">{{ stats?.userTotal ?? 0 }}</div>
      </el-card>
    </div>

    <el-card class="recent-card" shadow="never">
      <template #header><span>最近注册企业</span></template>
      <el-table :data="stats?.recentCompanies ?? []" stripe>
        <el-table-column prop="companyNo" label="企业编号" width="140" />
        <el-table-column prop="fullName" label="企业全称" />
        <el-table-column prop="shortName" label="简称" width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ statusText(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}
.stat-card {
  border-radius: 10px;
}
.stat-label {
  color: #6b7280;
  font-size: 13px;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #1f2937;
}
.recent-card {
  border-radius: 10px;
}
</style>
```

- [ ] **Step 2: Create `CompanyDashboard.vue`**

`tengyei-frontend/src/views/dashboard/CompanyDashboard.vue`:

```vue
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { dashboardApi } from '@/api/dashboard'
import { useAuthStore } from '@/stores/auth'
import type { DashboardStats } from '@/types/dashboard'

const stats = ref<DashboardStats | null>(null)
const loading = ref(true)
const router = useRouter()
const auth = useAuthStore()

onMounted(async () => {
  try {
    stats.value = await dashboardApi.stats()
  } finally {
    loading.value = false
  }
})

interface Shortcut {
  title: string
  path: string
  perm: string
}
const shortcuts: Shortcut[] = [
  { title: '组织管理', path: '/company/org', perm: 'dept:view' },
  { title: '人员管理', path: '/company/users', perm: 'user:view' },
  { title: '角色与权限', path: '/company/roles', perm: 'role:view' },
]
</script>

<template>
  <div v-loading="loading" class="dashboard">
    <div class="stat-cards">
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">部门数</div>
        <div class="stat-value">{{ stats?.deptCount ?? 0 }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">分支机构</div>
        <div class="stat-value">{{ stats?.branchCount ?? 0 }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">人员总数</div>
        <div class="stat-value">{{ stats?.userCount ?? 0 }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">今日登录</div>
        <div class="stat-value">{{ stats?.todayLoginCount ?? 0 }}</div>
      </el-card>
    </div>

    <el-card class="shortcut-card" shadow="never">
      <template #header><span>快捷操作</span></template>
      <div class="shortcuts">
        <template v-for="s in shortcuts" :key="s.path">
          <div
            v-if="auth.hasPermission(s.perm)"
            class="shortcut"
            @click="router.push(s.path)"
          >
            {{ s.title }}
          </div>
        </template>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}
.stat-card {
  border-radius: 10px;
}
.stat-label {
  color: #6b7280;
  font-size: 13px;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #1f2937;
}
.shortcut-card {
  border-radius: 10px;
}
.shortcuts {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}
.shortcut {
  padding: 16px 28px;
  background: #f0f5ff;
  color: var(--color-primary, #3b82f6);
  border-radius: 8px;
  cursor: pointer;
  font-weight: 600;
}
.shortcut:hover {
  background: #e0ecff;
}
</style>
```

- [ ] **Step 3: Replace `DashboardView.vue` with a dispatcher**

`tengyei-frontend/src/views/DashboardView.vue`:

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import SuperDashboard from './dashboard/SuperDashboard.vue'
import CompanyDashboard from './dashboard/CompanyDashboard.vue'

const auth = useAuthStore()
const isSuper = computed(() => auth.isSuperAdmin)
</script>

<template>
  <SuperDashboard v-if="isSuper" />
  <CompanyDashboard v-else />
</template>
```

- [ ] **Step 4: Build to verify**

Run: `npm --prefix tengyei-frontend run build`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add tengyei-frontend/src/views/dashboard tengyei-frontend/src/views/DashboardView.vue
git commit -m "feat(frontend): role-aware dashboard with stat cards and shortcuts"
```

---

## Task 11: Frontend — Company management page (super admin)

**Files:**
- Modify (replace stub): `tengyei-frontend/src/views/company/CompanyListView.vue`

- [ ] **Step 1: Implement `CompanyListView.vue`**

```vue
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { companyApi } from '@/api/company'
import type { CompanyVO, CompanyCreateDTO } from '@/types/company'

const loading = ref(false)
const list = ref<CompanyVO[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '' })

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<CompanyCreateDTO>({
  fullName: '',
  shortName: '',
  adminName: '',
  adminPhone: '',
  adminUsername: '',
  adminPassword: '',
})
const rules: FormRules = {
  fullName: [{ required: true, message: '请输入企业全称', trigger: 'blur' }],
  shortName: [{ required: true, message: '请输入企业简称', trigger: 'blur' }],
  adminName: [{ required: true, message: '请输入管理员姓名', trigger: 'blur' }],
  adminPhone: [{ required: true, message: '请输入管理员电话', trigger: 'blur' }],
  adminUsername: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  adminPassword: [{ required: true, min: 6, message: '密码至少 6 位', trigger: 'blur' }],
}

async function fetchList() {
  loading.value = true
  try {
    const res = await companyApi.page({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
    })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  fetchList()
}

function openCreate() {
  Object.assign(form, {
    fullName: '',
    shortName: '',
    adminName: '',
    adminPhone: '',
    adminUsername: '',
    adminPassword: '',
  })
  dialogVisible.value = true
}

async function submitCreate() {
  if (!formRef.value) return
  await formRef.value.validate()
  await companyApi.create({ ...form })
  ElMessage.success('企业创建成功')
  dialogVisible.value = false
  fetchList()
}

async function toggleStatus(row: CompanyVO) {
  const next = row.status === 1 ? 2 : 1
  const action = next === 2 ? '停用' : '启用'
  await ElMessageBox.confirm(`确认${action}企业「${row.shortName}」？`, '提示', { type: 'warning' })
  await companyApi.changeStatus(row.id, next)
  ElMessage.success(`已${action}`)
  fetchList()
}

const statusText = (s: number) => (s === 1 ? '启用' : s === 2 ? '停用' : '待激活')
const statusType = (s: number) => (s === 1 ? 'success' : s === 2 ? 'info' : 'warning')

onMounted(fetchList)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索企业名称"
        clearable
        style="width: 240px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-button type="primary" @click="onSearch">搜索</el-button>
      <el-button type="primary" plain @click="openCreate">新增企业</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="companyNo" label="企业编号" width="130" />
      <el-table-column prop="fullName" label="企业全称" min-width="200" />
      <el-table-column prop="adminName" label="联系人" width="120" />
      <el-table-column prop="adminPhone" label="联系电话" width="140" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="toggleStatus(row)">
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      :current-page="query.page"
      :page-size="query.size"
      @current-change="(p: number) => { query.page = p; fetchList() }"
    />

    <el-dialog v-model="dialogVisible" title="新增企业" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="企业全称" prop="fullName">
          <el-input v-model="form.fullName" />
        </el-form-item>
        <el-form-item label="企业简称" prop="shortName">
          <el-input v-model="form.shortName" />
        </el-form-item>
        <el-form-item label="联系人姓名" prop="adminName">
          <el-input v-model="form.adminName" />
        </el-form-item>
        <el-form-item label="联系电话" prop="adminPhone">
          <el-input v-model="form.adminPhone" />
        </el-form-item>
        <el-form-item label="管理员账号" prop="adminUsername">
          <el-input v-model="form.adminUsername" />
        </el-form-item>
        <el-form-item label="管理员密码" prop="adminPassword">
          <el-input v-model="form.adminPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  background: #fff;
  border-radius: 10px;
  padding: 16px;
}
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
```

- [ ] **Step 2: Build to verify**

Run: `npm --prefix tengyei-frontend run build`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Manual smoke test**

As `superadmin`: open 企业管理, click 新增企业, fill the form, submit → row appears; toggle 停用/启用 works; keyword search filters.

- [ ] **Step 4: Commit**

```bash
git add tengyei-frontend/src/views/company/CompanyListView.vue
git commit -m "feat(frontend): company management page — list, create-with-admin, status toggle"
```

---

## Task 12: Frontend — Organization management page (dept tree + branch list)

**Files:**
- Modify (replace stub): `tengyei-frontend/src/views/org/OrgView.vue`

- [ ] **Step 1: Implement `OrgView.vue`**

```vue
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { deptApi, branchApi } from '@/api/org'
import type { DeptTreeVO, DeptSaveDTO, BranchVO, BranchSaveDTO } from '@/types/org'

/* ---------- Department tree ---------- */
const treeLoading = ref(false)
const deptTree = ref<DeptTreeVO[]>([])
const treeProps = { label: 'name', children: 'children' }

const deptDialog = ref(false)
const deptFormRef = ref<FormInstance>()
const deptEditingId = ref<number | null>(null)
const deptForm = reactive<DeptSaveDTO>({ name: '', parentId: 0, sortOrder: 0 })
const deptRules: FormRules = {
  name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
}

async function fetchTree() {
  treeLoading.value = true
  try {
    deptTree.value = await deptApi.tree()
  } finally {
    treeLoading.value = false
  }
}

function openDeptCreate(parent?: DeptTreeVO) {
  deptEditingId.value = null
  Object.assign(deptForm, { name: '', parentId: parent ? parent.id : 0, sortOrder: 0 })
  deptDialog.value = true
}

function openDeptEdit(node: DeptTreeVO) {
  deptEditingId.value = node.id
  Object.assign(deptForm, {
    name: node.name,
    code: node.code,
    parentId: node.parentId,
    sortOrder: node.sortOrder,
  })
  deptDialog.value = true
}

async function submitDept() {
  if (!deptFormRef.value) return
  await deptFormRef.value.validate()
  if (deptEditingId.value) {
    await deptApi.update(deptEditingId.value, { ...deptForm })
    ElMessage.success('部门已更新')
  } else {
    await deptApi.create({ ...deptForm })
    ElMessage.success('部门已创建')
  }
  deptDialog.value = false
  fetchTree()
}

async function removeDept(node: DeptTreeVO) {
  await ElMessageBox.confirm(`确认删除部门「${node.name}」？`, '提示', { type: 'warning' })
  await deptApi.remove(node.id)
  ElMessage.success('已删除')
  fetchTree()
}

/* ---------- Branch list ---------- */
const branchLoading = ref(false)
const branches = ref<BranchVO[]>([])
const branchTotal = ref(0)
const branchQuery = reactive({ page: 1, size: 20 })

const branchDialog = ref(false)
const branchFormRef = ref<FormInstance>()
const branchEditingId = ref<number | null>(null)
const branchForm = reactive<BranchSaveDTO>({ branchNo: '', name: '', type: 'independent' })
const branchRules: FormRules = {
  branchNo: [{ required: true, message: '请输入机构编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入机构名称', trigger: 'blur' }],
}

async function fetchBranches() {
  branchLoading.value = true
  try {
    const res = await branchApi.page({ page: branchQuery.page, size: branchQuery.size })
    branches.value = res.records
    branchTotal.value = res.total
  } finally {
    branchLoading.value = false
  }
}

function openBranchCreate() {
  branchEditingId.value = null
  Object.assign(branchForm, {
    branchNo: '',
    name: '',
    type: 'independent',
    city: '',
    phone: '',
  })
  branchDialog.value = true
}

function openBranchEdit(row: BranchVO) {
  branchEditingId.value = row.id
  Object.assign(branchForm, {
    branchNo: row.branchNo,
    name: row.name,
    type: row.type,
    city: row.city,
    phone: row.phone,
  })
  branchDialog.value = true
}

async function submitBranch() {
  if (!branchFormRef.value) return
  await branchFormRef.value.validate()
  if (branchEditingId.value) {
    await branchApi.update(branchEditingId.value, { ...branchForm })
    ElMessage.success('分支机构已更新')
  } else {
    await branchApi.create({ ...branchForm })
    ElMessage.success('分支机构已创建')
  }
  branchDialog.value = false
  fetchBranches()
}

async function toggleBranch(row: BranchVO) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 0 ? '停用' : '启用'
  await ElMessageBox.confirm(`确认${action}「${row.name}」？`, '提示', { type: 'warning' })
  await branchApi.changeStatus(row.id, next)
  ElMessage.success(`已${action}`)
  fetchBranches()
}

onMounted(() => {
  fetchTree()
  fetchBranches()
})
</script>

<template>
  <div class="org">
    <el-card class="dept-pane" shadow="never">
      <template #header>
        <div class="pane-head">
          <span>部门</span>
          <el-button link type="primary" @click="openDeptCreate()">新增根部门</el-button>
        </div>
      </template>
      <el-tree
        v-loading="treeLoading"
        :data="deptTree"
        :props="treeProps"
        node-key="id"
        default-expand-all
      >
        <template #default="{ data }">
          <span class="tree-node">
            <span>{{ data.name }}</span>
            <span class="tree-actions">
              <el-button link type="primary" @click.stop="openDeptCreate(data)">增</el-button>
              <el-button link type="primary" @click.stop="openDeptEdit(data)">改</el-button>
              <el-button link type="danger" @click.stop="removeDept(data)">删</el-button>
            </span>
          </span>
        </template>
      </el-tree>
    </el-card>

    <el-card class="branch-pane" shadow="never">
      <template #header>
        <div class="pane-head">
          <span>分支机构</span>
          <el-button type="primary" plain size="small" @click="openBranchCreate">新增分支机构</el-button>
        </div>
      </template>
      <el-table v-loading="branchLoading" :data="branches" stripe>
        <el-table-column prop="branchNo" label="机构编号" width="120" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="phone" label="联系电话" width="140" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openBranchEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="toggleBranch(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="branchTotal"
        :current-page="branchQuery.page"
        :page-size="branchQuery.size"
        @current-change="(p: number) => { branchQuery.page = p; fetchBranches() }"
      />
    </el-card>

    <el-dialog v-model="deptDialog" :title="deptEditingId ? '编辑部门' : '新增部门'" width="420px">
      <el-form ref="deptFormRef" :model="deptForm" :rules="deptRules" label-width="90px">
        <el-form-item label="部门名称" prop="name">
          <el-input v-model="deptForm.name" />
        </el-form-item>
        <el-form-item label="部门编码">
          <el-input v-model="deptForm.code" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="deptForm.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deptDialog = false">取消</el-button>
        <el-button type="primary" @click="submitDept">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="branchDialog"
      :title="branchEditingId ? '编辑分支机构' : '新增分支机构'"
      width="460px"
    >
      <el-form ref="branchFormRef" :model="branchForm" :rules="branchRules" label-width="90px">
        <el-form-item label="机构编号" prop="branchNo">
          <el-input v-model="branchForm.branchNo" />
        </el-form-item>
        <el-form-item label="机构名称" prop="name">
          <el-input v-model="branchForm.name" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="branchForm.type">
            <el-option label="独立机构" value="independent" />
            <el-option label="附属机构" value="affiliated" />
          </el-select>
        </el-form-item>
        <el-form-item label="所在城市">
          <el-input v-model="branchForm.city" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="branchForm.phone" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="branchDialog = false">取消</el-button>
        <el-button type="primary" @click="submitBranch">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.org {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 16px;
  align-items: start;
}
.dept-pane,
.branch-pane {
  border-radius: 10px;
}
.pane-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.tree-node {
  flex: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-right: 8px;
}
.tree-actions {
  opacity: 0;
}
.tree-node:hover .tree-actions {
  opacity: 1;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
```

- [ ] **Step 2: Build to verify**

Run: `npm --prefix tengyei-frontend run build`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Manual smoke test**

As a company admin (created via 企业管理): open 组织管理. Create a root department; it appears in the tree. Hover a node → 增/改/删 actions. Create a branch; it appears in the right list; 停用/启用 toggles.

- [ ] **Step 4: Commit**

```bash
git add tengyei-frontend/src/views/org/OrgView.vue
git commit -m "feat(frontend): organization page — department tree and branch list"
```

---

## Task 13: Frontend — User management page

**Files:**
- Modify (replace stub): `tengyei-frontend/src/views/user/UserListView.vue`

- [ ] **Step 1: Implement `UserListView.vue`**

```vue
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { userApi } from '@/api/user'
import { roleApi } from '@/api/rbac'
import type { UserVO, UserCreateDTO } from '@/types/user'
import type { RoleVO } from '@/types/rbac'

const loading = ref(false)
const list = ref<UserVO[]>([])
const total = ref(0)
const roles = ref<RoleVO[]>([])
const query = reactive({ page: 1, size: 20, keyword: '', roleId: undefined as number | undefined })

const createDialog = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive<UserCreateDTO>({
  username: '',
  realName: '',
  phone: '',
  password: '',
  roleIds: [],
})
const createRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  password: [{ required: true, min: 6, message: '密码至少 6 位', trigger: 'blur' }],
}

const roleDialog = ref(false)
const roleTargetId = ref<number | null>(null)
const roleSelection = ref<number[]>([])

async function fetchList() {
  loading.value = true
  try {
    const res = await userApi.page({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      roleId: query.roleId,
    })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function fetchRoles() {
  roles.value = await roleApi.list()
}

function onSearch() {
  query.page = 1
  fetchList()
}

function openCreate() {
  Object.assign(createForm, {
    username: '',
    realName: '',
    phone: '',
    email: '',
    password: '',
    roleIds: [],
  })
  createDialog.value = true
}

async function submitCreate() {
  if (!createFormRef.value) return
  await createFormRef.value.validate()
  await userApi.create({ ...createForm })
  ElMessage.success('用户已创建')
  createDialog.value = false
  fetchList()
}

function openRoleDialog(row: UserVO) {
  roleTargetId.value = row.id
  roleSelection.value = [...row.roleIds]
  roleDialog.value = true
}

async function submitRoles() {
  if (roleTargetId.value == null) return
  await userApi.assignRoles(roleTargetId.value, roleSelection.value)
  ElMessage.success('角色已更新')
  roleDialog.value = false
  fetchList()
}

async function toggleStatus(row: UserVO) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 0 ? '停用' : '启用'
  await ElMessageBox.confirm(`确认${action}用户「${row.realName}」？`, '提示', { type: 'warning' })
  await userApi.changeStatus(row.id, next)
  ElMessage.success(`已${action}`)
  fetchList()
}

async function resetPassword(row: UserVO) {
  const { value } = await ElMessageBox.prompt('请输入新密码（至少 6 位）', '重置密码', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputType: 'password',
    inputValidator: (v) => (!!v && v.length >= 6) || '密码至少 6 位',
  })
  await userApi.resetPassword(row.id, value)
  ElMessage.success('密码已重置')
}

onMounted(() => {
  fetchRoles()
  fetchList()
})
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="姓名 / 用户名 / 手机号"
        clearable
        style="width: 240px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-select
        v-model="query.roleId"
        placeholder="按角色筛选"
        clearable
        style="width: 180px"
        @change="onSearch"
      >
        <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
      </el-select>
      <el-button type="primary" @click="onSearch">搜索</el-button>
      <el-button type="primary" plain @click="openCreate">新增用户</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="realName" label="姓名" width="120" />
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column label="角色" min-width="160">
        <template #default="{ row }">
          <el-tag v-for="name in row.roleNames" :key="name" class="role-tag">{{ name }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openRoleDialog(row)">分配角色</el-button>
          <el-button link type="primary" @click="toggleStatus(row)">
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
          <el-button link type="primary" @click="resetPassword(row)">重置密码</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      :current-page="query.page"
      :page-size="query.size"
      @current-change="(p: number) => { query.page = p; fetchList() }"
    />

    <el-dialog v-model="createDialog" title="新增用户" width="480px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createForm.username" />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="createForm.realName" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="createForm.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="createForm.email" />
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="createForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="分配角色">
          <el-select v-model="createForm.roleIds" multiple style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialog" title="分配角色" width="420px">
      <el-checkbox-group v-model="roleSelection">
        <el-checkbox v-for="r in roles" :key="r.id" :value="r.id" :label="r.id">
          {{ r.name }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialog = false">取消</el-button>
        <el-button type="primary" @click="submitRoles">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  background: #fff;
  border-radius: 10px;
  padding: 16px;
}
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}
.role-tag {
  margin-right: 4px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
```

> Note: `el-checkbox` uses `:value` (Element Plus 2.6+) for the checked value; `:label` is kept for the visible text association. If the installed Element Plus version warns about `label`, the value binding via `:value` is authoritative.

- [ ] **Step 2: Build to verify**

Run: `npm --prefix tengyei-frontend run build`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Manual smoke test**

As a company admin: open 人员管理. Create a user with a role assigned → row shows role tag. 分配角色 dialog pre-checks current roles and saves. 停用/启用 and 重置密码 work. Verify the new user can log in with the initial password.

- [ ] **Step 4: Commit**

```bash
git add tengyei-frontend/src/views/user/UserListView.vue
git commit -m "feat(frontend): user management page — CRUD, role assignment, password reset"
```

---

## Task 14: Frontend — Role & permission page

**Files:**
- Modify (replace stub): `tengyei-frontend/src/views/role/RoleView.vue`

- [ ] **Step 1: Implement `RoleView.vue`**

```vue
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { roleApi, permissionApi } from '@/api/rbac'
import type { RoleVO, RoleSaveDTO, PermissionGroupVO } from '@/types/rbac'

const roles = ref<RoleVO[]>([])
const groups = ref<PermissionGroupVO[]>([])
const activeRole = ref<RoleVO | null>(null)
const checkedPerms = ref<number[]>([])
const permLoading = ref(false)
const savingPerms = ref(false)

const dialog = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)
const form = reactive<RoleSaveDTO>({ name: '', code: '', dataScope: 'self', description: '' })
const rules: FormRules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
}
const scopeOptions = [
  { label: '全部数据', value: 'all' },
  { label: '本分支', value: 'branch' },
  { label: '本部门', value: 'dept' },
  { label: '仅本人', value: 'self' },
]
const scopeLabel = (v: string) => scopeOptions.find((o) => o.value === v)?.label ?? v

async function fetchRoles() {
  roles.value = await roleApi.list()
  if (!activeRole.value && roles.value.length) selectRole(roles.value[0])
}

async function fetchGroups() {
  groups.value = await permissionApi.grouped()
}

async function selectRole(role: RoleVO) {
  activeRole.value = role
  permLoading.value = true
  try {
    checkedPerms.value = await roleApi.permissionIds(role.id)
  } finally {
    permLoading.value = false
  }
}

function togglePerm(id: number, checked: boolean) {
  if (checked) {
    if (!checkedPerms.value.includes(id)) checkedPerms.value.push(id)
  } else {
    checkedPerms.value = checkedPerms.value.filter((p) => p !== id)
  }
}

async function savePerms() {
  if (!activeRole.value) return
  savingPerms.value = true
  try {
    await roleApi.assignPermissions(activeRole.value.id, checkedPerms.value)
    ElMessage.success('权限已保存')
  } finally {
    savingPerms.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { name: '', code: '', dataScope: 'self', description: '' })
  dialog.value = true
}

function openEdit(role: RoleVO) {
  editingId.value = role.id
  Object.assign(form, {
    name: role.name,
    code: role.code,
    dataScope: role.dataScope,
    description: role.description,
  })
  dialog.value = true
}

async function submit() {
  if (!formRef.value) return
  await formRef.value.validate()
  if (editingId.value) {
    await roleApi.update(editingId.value, { ...form })
    ElMessage.success('角色已更新')
  } else {
    await roleApi.create({ ...form })
    ElMessage.success('角色已创建')
  }
  dialog.value = false
  fetchRoles()
}

async function removeRole(role: RoleVO) {
  await ElMessageBox.confirm(`确认删除角色「${role.name}」？`, '提示', { type: 'warning' })
  await roleApi.remove(role.id)
  ElMessage.success('已删除')
  if (activeRole.value?.id === role.id) activeRole.value = null
  fetchRoles()
}

onMounted(() => {
  fetchGroups()
  fetchRoles()
})
</script>

<template>
  <div class="rbac">
    <el-card class="role-pane" shadow="never">
      <template #header>
        <div class="pane-head">
          <span>角色</span>
          <el-button type="primary" plain size="small" @click="openCreate">新增角色</el-button>
        </div>
      </template>
      <ul class="role-list">
        <li
          v-for="role in roles"
          :key="role.id"
          class="role-item"
          :class="{ active: activeRole?.id === role.id }"
          @click="selectRole(role)"
        >
          <div class="role-info">
            <div class="role-name">{{ role.name }}</div>
            <div class="role-meta">{{ role.code }} · {{ scopeLabel(role.dataScope) }}</div>
          </div>
          <div class="role-ops">
            <el-button link type="primary" @click.stop="openEdit(role)">改</el-button>
            <el-button
              v-if="role.isPreset !== 1"
              link
              type="danger"
              @click.stop="removeRole(role)"
            >删</el-button>
          </div>
        </li>
      </ul>
    </el-card>

    <el-card v-loading="permLoading" class="perm-pane" shadow="never">
      <template #header>
        <div class="pane-head">
          <span>权限分配{{ activeRole ? `：${activeRole.name}` : '' }}</span>
          <el-button
            type="primary"
            size="small"
            :disabled="!activeRole"
            :loading="savingPerms"
            @click="savePerms"
          >保存</el-button>
        </div>
      </template>
      <div v-if="!activeRole" class="perm-empty">请选择左侧角色</div>
      <div v-for="g in groups" v-else :key="g.module" class="perm-group">
        <div class="perm-module">{{ g.module }}</div>
        <div class="perm-items">
          <el-checkbox
            v-for="p in g.permissions"
            :key="p.id"
            :model-value="checkedPerms.includes(p.id)"
            @update:model-value="(v) => togglePerm(p.id, !!v)"
          >{{ p.name }}</el-checkbox>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="dialog" :title="editingId ? '编辑角色' : '新增角色'" width="440px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="form.code" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="form.dataScope" style="width: 100%">
            <el-option v-for="o in scopeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
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
.rbac {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 16px;
  align-items: start;
}
.role-pane,
.perm-pane {
  border-radius: 10px;
}
.pane-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.role-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.role-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
}
.role-item:hover {
  background: #f5f7fa;
}
.role-item.active {
  background: #f0f5ff;
}
.role-name {
  font-weight: 600;
  color: #1f2937;
}
.role-meta {
  font-size: 12px;
  color: #9ca3af;
}
.role-ops {
  opacity: 0;
}
.role-item:hover .role-ops {
  opacity: 1;
}
.perm-empty {
  color: #9ca3af;
  padding: 24px 0;
  text-align: center;
}
.perm-group {
  margin-bottom: 16px;
}
.perm-module {
  font-weight: 600;
  color: #374151;
  margin-bottom: 8px;
  padding-bottom: 4px;
  border-bottom: 1px solid #f0f0f0;
}
.perm-items {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
}
</style>
```

- [ ] **Step 2: Build to verify**

Run: `npm --prefix tengyei-frontend run build`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Manual smoke test**

As a company admin: open 角色与权限. Create a role; select it → permission matrix loads its current grants. Check/uncheck permissions by module, click 保存 → success. Preset roles show no 删 button. Assign the role to a user (人员管理) and confirm that user sees only the permitted menus on next login.

- [ ] **Step 4: Commit**

```bash
git add tengyei-frontend/src/views/role/RoleView.vue
git commit -m "feat(frontend): role & permission page — role list and permission matrix"
```

---

## Final Verification

- [ ] **Step 1: Full backend test suite**

Run: `mvn -q -pl app -am test`
Expected: all classes green — Auth, Company, Dept, Branch, User, Role, Dashboard.

- [ ] **Step 2: Full frontend build**

Run: `npm --prefix tengyei-frontend run build`
Expected: BUILD SUCCESS, no type errors.

- [ ] **Step 3: End-to-end acceptance (against `dev` profile + MySQL + Redis)**

Start backend and frontend. Verify the spec's acceptance criteria:
1. Super admin sees 企业管理; can create a company and toggle 启用/停用.
2. The created company admin logs in and sees 组织管理 / 人员管理 / 角色与权限.
3. Creating a user + assigning a role → that user logs in and sees the matching menus.
4. Tabs open / switch / close correctly; 工作台 is non-closable.
5. Page refresh restores the open tabs (sessionStorage).
6. All lists support pagination and keyword search.

- [ ] **Step 4: Final code review**

Use `superpowers:requesting-code-review` for the whole Phase 2 diff before finishing the branch.

---

## Acceptance Criteria Coverage

| Spec criterion | Task(s) |
|---|---|
| 超管企业管理（增删改 + 启用/停用） | 2, 11 |
| 企业管理员组织/人员/角色三菜单 | 3, 4, 5, 6, 7, 12, 13, 14 |
| 新增用户分配角色后可登录看到对应菜单 | 5, 7, 13 |
| 标签页打开/切换/关闭 | 8, 9 |
| 刷新后标签恢复（sessionStorage） | 8, 9 |
| 列表分页 + 关键字搜索 | 2, 4, 5, 11, 12, 13 |
| 接口级权限（@PreAuthorize）+ 方法安全开启 | 1, 3, 4, 5, 6 |
| 数据隔离（租户拦截器 + 分页拦截器） | 1 |

