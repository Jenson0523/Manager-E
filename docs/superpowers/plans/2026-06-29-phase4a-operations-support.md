# Phase 4-A: 运营支撑加固 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为腾飞企业管理系统新增 Excel 数据导出、批量操作、仪表盘图表三大运营支撑功能。

**Architecture:** 导出使用 EasyExcel 3.3.4 在后端生成 `.xlsx` 文件并以 binary stream 返回，前端用 axios responseType:blob + URL.createObjectURL 触发下载。批量操作在 UserController 新增两个 PUT 接口（/batch/status、/batch/roles），UserService 实现事务逻辑并加上限（200条/次）。仪表盘新增 `/api/v1/dashboard/chart-data` 接口返回趋势数据，前端按需引入 ECharts 渲染折线图+饼图。

**Tech Stack:** EasyExcel 3.3.4、Spring Boot 3.2.5、MyBatis-Plus 3.5.7、JdbcTemplate（直接 SQL 查询）、Vue 3、echarts（按需引入 tree-shaking）、axios blob download。

---

## File Map

### 新增文件

| 文件 | 职责 |
|------|------|
| `tengyei-backend/core-org/src/main/java/com/tengyei/org/dto/UserExportVO.java` | EasyExcel 注解 VO，人员导出列定义 |
| `tengyei-backend/app/src/main/java/com/tengyei/dto/AuditLogExportVO.java` | EasyExcel 注解 VO，操作日志导出列定义 |
| `tengyei-backend/app/src/main/java/com/tengyei/dto/LoginLogExportVO.java` | EasyExcel 注解 VO，登录日志导出列定义 |
| `tengyei-backend/core-org/src/main/java/com/tengyei/org/dto/BatchStatusDTO.java` | 批量变更状态请求体 |
| `tengyei-backend/core-org/src/main/java/com/tengyei/org/dto/BatchRolesDTO.java` | 批量分配角色请求体 |
| `tengyei-frontend/src/utils/download.ts` | blob 文件下载工具函数 |

### 修改文件

| 文件 | 改动 |
|------|------|
| `tengyei-backend/pom.xml` | 新增 easyexcel 版本属性和 dependencyManagement 条目 |
| `tengyei-backend/core-org/pom.xml` | 新增 easyexcel 依赖 |
| `tengyei-backend/app/pom.xml` | 新增 easyexcel 依赖 |
| `tengyei-backend/core-org/src/main/java/com/tengyei/org/service/UserService.java` | 新增 export()、batchChangeStatus()、batchAssignRoles() 方法 |
| `tengyei-backend/core-org/src/main/java/com/tengyei/org/controller/UserController.java` | 新增 export、/batch/status、/batch/roles 端点 |
| `tengyei-backend/app/src/main/java/com/tengyei/controller/AuditLogController.java` | 新增 export 端点 |
| `tengyei-backend/app/src/main/java/com/tengyei/controller/LoginLogController.java` | 新增 export 端点 |
| `tengyei-backend/app/src/main/java/com/tengyei/service/DashboardService.java` | 新增 chartData() 方法 |
| `tengyei-backend/app/src/main/java/com/tengyei/controller/DashboardController.java` | 新增 /chart-data 端点 |
| `tengyei-frontend/src/api/user.ts` | 新增 export、batchStatus、batchRoles 方法 |
| `tengyei-frontend/src/api/audit.ts` | 新增 auditApi.export、loginLogApi.export 方法 |
| `tengyei-frontend/src/api/dashboard.ts` | 新增 chartData 方法 |
| `tengyei-frontend/src/types/dashboard.ts` | 新增 ChartData 类型 |
| `tengyei-frontend/src/views/user/UserListView.vue` | 新增导出按钮、批量选择列、批量操作栏 |
| `tengyei-frontend/src/views/audit/AuditLogView.vue` | 新增导出按钮（操作日志+登录日志两个 tab） |
| `tengyei-frontend/src/views/dashboard/SuperDashboard.vue` | 新增折线图（人员增长趋势）+饼图（操作类型分布） |
| `tengyei-frontend/src/views/dashboard/CompanyDashboard.vue` | 新增折线图（登录活跃度）+饼图（部门人员分布） |

---

## Task 1: EasyExcel 依赖 + UserExportVO

**Files:**
- Modify: `tengyei-backend/pom.xml`
- Modify: `tengyei-backend/core-org/pom.xml`
- Modify: `tengyei-backend/app/pom.xml`
- Create: `tengyei-backend/core-org/src/main/java/com/tengyei/org/dto/UserExportVO.java`
- Create: `tengyei-backend/app/src/main/java/com/tengyei/dto/AuditLogExportVO.java`
- Create: `tengyei-backend/app/src/main/java/com/tengyei/dto/LoginLogExportVO.java`

- [ ] **Step 1: 在 parent pom.xml 的 `<properties>` 里添加 easyexcel 版本**

  在 `tengyei-backend/pom.xml` 的 `<properties>` 块中（`<jjwt.version>` 之后）追加：

  ```xml
  <easyexcel.version>3.3.4</easyexcel.version>
  ```

  再在 `<dependencyManagement><dependencies>` 中追加：

  ```xml
  <dependency>
      <groupId>com.alibaba</groupId>
      <artifactId>easyexcel</artifactId>
      <version>${easyexcel.version}</version>
  </dependency>
  ```

- [ ] **Step 2: 在 core-org/pom.xml 添加 easyexcel 依赖**

  在 `tengyei-backend/core-org/pom.xml` 的 `<dependencies>` 末尾追加：

  ```xml
  <dependency>
      <groupId>com.alibaba</groupId>
      <artifactId>easyexcel</artifactId>
  </dependency>
  ```

- [ ] **Step 3: 在 app/pom.xml 添加 easyexcel 依赖**

  在 `tengyei-backend/app/pom.xml` 的 `<dependencies>` 末尾追加（lombok 依赖之前）：

  ```xml
  <dependency>
      <groupId>com.alibaba</groupId>
      <artifactId>easyexcel</artifactId>
  </dependency>
  ```

- [ ] **Step 4: 创建 UserExportVO**

  创建文件 `tengyei-backend/core-org/src/main/java/com/tengyei/org/dto/UserExportVO.java`：

  ```java
  package com.tengyei.org.dto;

  import com.alibaba.excel.annotation.ExcelProperty;
  import lombok.Data;

  @Data
  public class UserExportVO {
      @ExcelProperty("姓名")
      private String realName;

      @ExcelProperty("账号")
      private String username;

      @ExcelProperty("手机号")
      private String phone;

      @ExcelProperty("邮箱")
      private String email;

      @ExcelProperty("部门")
      private String deptName;

      @ExcelProperty("角色")
      private String roles;

      @ExcelProperty("状态")
      private String status;

      @ExcelProperty("创建时间")
      private String createdAt;
  }
  ```

- [ ] **Step 5: 创建 AuditLogExportVO**

  创建文件 `tengyei-backend/app/src/main/java/com/tengyei/dto/AuditLogExportVO.java`：

  ```java
  package com.tengyei.dto;

  import com.alibaba.excel.annotation.ExcelProperty;
  import lombok.Data;

  @Data
  public class AuditLogExportVO {
      @ExcelProperty("操作人")
      private String userName;

      @ExcelProperty("模块")
      private String module;

      @ExcelProperty("操作类型")
      private String actionType;

      @ExcelProperty("操作描述")
      private String description;

      @ExcelProperty("IP地址")
      private String ipAddress;

      @ExcelProperty("结果")
      private String result;

      @ExcelProperty("错误信息")
      private String errorMsg;

      @ExcelProperty("时间")
      private String createdAt;
  }
  ```

- [ ] **Step 6: 创建 LoginLogExportVO**

  创建文件 `tengyei-backend/app/src/main/java/com/tengyei/dto/LoginLogExportVO.java`：

  ```java
  package com.tengyei.dto;

  import com.alibaba.excel.annotation.ExcelProperty;
  import lombok.Data;

  @Data
  public class LoginLogExportVO {
      @ExcelProperty("账号")
      private String username;

      @ExcelProperty("登录方式")
      private String loginType;

      @ExcelProperty("IP地址")
      private String ipAddress;

      @ExcelProperty("结果")
      private String result;

      @ExcelProperty("失败原因")
      private String failReason;

      @ExcelProperty("时间")
      private String createdAt;
  }
  ```

- [ ] **Step 7: 验证编译通过**

  在 `tengyei-backend` 目录执行（PowerShell，Windows）：
  ```powershell
  cd tengyei-backend; mvn compile -q
  ```
  预期：无 ERROR，BUILD SUCCESS。

- [ ] **Step 8: Commit**

  ```bash
  git add tengyei-backend/pom.xml \
    tengyei-backend/core-org/pom.xml \
    tengyei-backend/app/pom.xml \
    tengyei-backend/core-org/src/main/java/com/tengyei/org/dto/UserExportVO.java \
    tengyei-backend/app/src/main/java/com/tengyei/dto/AuditLogExportVO.java \
    tengyei-backend/app/src/main/java/com/tengyei/dto/LoginLogExportVO.java
  git commit -m "feat: add EasyExcel dependency and export VO classes"
  ```

---

## Task 2: 人员导出后端（UserService.export + UserController）

**Files:**
- Modify: `tengyei-backend/core-org/src/main/java/com/tengyei/org/service/UserService.java`
- Modify: `tengyei-backend/core-org/src/main/java/com/tengyei/org/controller/UserController.java`

- [ ] **Step 1: 在 UserService 添加 export() 方法**

  在 `UserService.java` 的 `changeStatus()` 方法之前插入（需要先在 import 区加 EasyExcel 相关 import）：

  在文件顶部 import 区追加（如未有）：
  ```java
  import com.tengyei.org.dto.UserExportVO;
  import org.springframework.util.StringUtils;
  ```

  在 `changeStatus()` 之前添加方法：

  ```java
  public List<UserExportVO> export(String keyword, Long deptId) {
      List<Object> params = new ArrayList<>();
      StringBuilder sql = new StringBuilder(
          "SELECT u.id, u.username, u.real_name, u.phone, u.email, " +
          "u.status, u.created_at, d.name AS dept_name " +
          "FROM `user` u LEFT JOIN dept d ON d.id = u.dept_id " +
          "WHERE u.is_super_admin = 0 AND u.is_deleted = 0");

      if (!TenantContext.isSuperAdmin()) {
          sql.append(" AND u.tenant_id = ?");
          params.add(TenantContext.getTenantId());
      }
      if (StringUtils.hasText(keyword)) {
          sql.append(" AND (u.real_name LIKE ? OR u.username LIKE ? OR u.phone LIKE ?)");
          String kw = "%" + keyword + "%";
          params.add(kw); params.add(kw); params.add(kw);
      }
      if (deptId != null) {
          sql.append(" AND u.dept_id = ?");
          params.add(deptId);
      }
      sql.append(" ORDER BY u.id LIMIT 5000");

      List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

      return rows.stream().map(row -> {
          Long userId = ((Number) row.get("id")).longValue();
          List<String> roleNames = jdbcTemplate.queryForList(
              "SELECT r.name FROM role r JOIN user_role ur ON ur.role_id = r.id WHERE ur.user_id = ?",
              String.class, userId);

          UserExportVO vo = new UserExportVO();
          vo.setRealName((String) row.get("real_name"));
          vo.setUsername((String) row.get("username"));
          vo.setPhone((String) row.get("phone"));
          vo.setEmail((String) row.get("email"));
          vo.setDeptName((String) row.get("dept_name"));
          vo.setRoles(String.join(", ", roleNames));
          vo.setStatus(Integer.valueOf(1).equals(row.get("status")) ? "启用" : "停用");
          Object ca = row.get("created_at");
          vo.setCreatedAt(ca != null ? ca.toString() : "");
          return vo;
      }).toList();
  }
  ```

  注意：`export` 方法需要 import `java.util.Map`（已有）和 `List`（已有）。

- [ ] **Step 2: 在 UserController 添加 export 端点**

  在 `UserController.java` 顶部 import 区追加：
  ```java
  import com.alibaba.excel.EasyExcel;
  import com.tengyei.org.dto.UserExportVO;
  import jakarta.servlet.http.HttpServletResponse;
  import java.net.URLEncoder;
  import java.nio.charset.StandardCharsets;
  import java.time.LocalDate;
  ```

  在 `page()` 方法之后追加：

  ```java
  @GetMapping("/export")
  @PreAuthorize("hasAuthority('PERM_user:view')")
  public void export(
          @RequestParam(required = false) String keyword,
          @RequestParam(required = false) Long deptId,
          HttpServletResponse response) throws Exception {
      List<UserExportVO> data = userService.export(keyword, deptId);
      String fileName = URLEncoder.encode("人员列表_" + LocalDate.now(), StandardCharsets.UTF_8)
          .replace("+", "%20");
      response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
      response.setCharacterEncoding("UTF-8");
      response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
      response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
      EasyExcel.write(response.getOutputStream(), UserExportVO.class)
          .sheet("人员列表")
          .doWrite(data);
  }
  ```

  注意：`List` 已在 import 里，`UserExportVO` 刚才加入。

- [ ] **Step 3: 验证编译通过**

  ```powershell
  cd tengyei-backend; mvn compile -q
  ```
  预期：BUILD SUCCESS。

- [ ] **Step 4: Commit**

  ```bash
  git add tengyei-backend/core-org/src/main/java/com/tengyei/org/service/UserService.java \
    tengyei-backend/core-org/src/main/java/com/tengyei/org/controller/UserController.java
  git commit -m "feat: add user list export endpoint (EasyExcel, max 5000 rows)"
  ```

---

## Task 3: 操作日志 & 登录日志导出后端

**Files:**
- Modify: `tengyei-backend/app/src/main/java/com/tengyei/controller/AuditLogController.java`
- Modify: `tengyei-backend/app/src/main/java/com/tengyei/controller/LoginLogController.java`

- [ ] **Step 1: 在 AuditLogController 添加 export 端点**

  在 `AuditLogController.java` 顶部 import 区追加（现有 import 基础上）：
  ```java
  import com.alibaba.excel.EasyExcel;
  import com.tengyei.dto.AuditLogExportVO;
  import jakarta.servlet.http.HttpServletResponse;
  import java.net.URLEncoder;
  import java.nio.charset.StandardCharsets;
  ```

  在 `list()` 方法之后追加：

  ```java
  @GetMapping("/export")
  @PreAuthorize("hasAuthority('PERM_*') or hasAuthority('PERM_log:view')")
  public void export(
          @RequestParam(name = "module", required = false) String module,
          @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
          @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
          HttpServletResponse response) throws Exception {

      boolean isSuperAdmin = TenantContext.isSuperAdmin();
      Long tenantId = TenantContext.getTenantId();

      List<Object> params = new ArrayList<>();
      StringBuilder where = new StringBuilder("WHERE 1=1");

      if (!isSuperAdmin) { where.append(" AND tenant_id = ?"); params.add(tenantId); }
      if (module != null && !module.isBlank()) { where.append(" AND module = ?"); params.add(module); }
      if (startDate != null) { where.append(" AND created_at >= ?"); params.add(startDate.atStartOfDay()); }
      if (endDate != null) { where.append(" AND created_at < ?"); params.add(endDate.plusDays(1).atStartOfDay()); }

      List<Map<String, Object>> rows = jdbcTemplate.queryForList(
          "SELECT user_name, module, action_type, description, ip_address, result, error_msg, created_at " +
          "FROM audit_log " + where + " ORDER BY created_at DESC LIMIT 5000",
          params.toArray());

      List<AuditLogExportVO> data = rows.stream().map(row -> {
          AuditLogExportVO vo = new AuditLogExportVO();
          vo.setUserName((String) row.get("user_name"));
          vo.setModule((String) row.get("module"));
          vo.setActionType((String) row.get("action_type"));
          vo.setDescription((String) row.get("description"));
          vo.setIpAddress((String) row.get("ip_address"));
          Object res = row.get("result");
          vo.setResult(Integer.valueOf(1).equals(res) ? "成功" : "失败");
          vo.setErrorMsg((String) row.get("error_msg"));
          Object ca = row.get("created_at");
          vo.setCreatedAt(ca != null ? ca.toString() : "");
          return vo;
      }).toList();

      String fileName = URLEncoder.encode("操作日志_" + LocalDate.now(), StandardCharsets.UTF_8)
          .replace("+", "%20");
      response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
      response.setCharacterEncoding("UTF-8");
      response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
      response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
      EasyExcel.write(response.getOutputStream(), AuditLogExportVO.class)
          .sheet("操作日志").doWrite(data);
  }
  ```

- [ ] **Step 2: 在 LoginLogController 添加 export 端点**

  在 `LoginLogController.java` 顶部 import 区追加：
  ```java
  import com.alibaba.excel.EasyExcel;
  import com.tengyei.dto.LoginLogExportVO;
  import jakarta.servlet.http.HttpServletResponse;
  import java.net.URLEncoder;
  import java.nio.charset.StandardCharsets;
  ```

  在 `list()` 方法之后追加：

  ```java
  @GetMapping("/export")
  @PreAuthorize("hasAuthority('PERM_*') or hasAuthority('PERM_log:view')")
  public void export(
          @RequestParam(name = "username", required = false) String username,
          @RequestParam(name = "result", required = false) Integer result,
          @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
          @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
          HttpServletResponse response) throws Exception {

      boolean isSuperAdmin = TenantContext.isSuperAdmin();
      Long tenantId = TenantContext.getTenantId();

      List<Object> params = new ArrayList<>();
      StringBuilder where = new StringBuilder("WHERE 1=1");

      if (!isSuperAdmin) { where.append(" AND tenant_id = ?"); params.add(tenantId); }
      if (username != null && !username.isBlank()) { where.append(" AND username LIKE ?"); params.add("%" + username + "%"); }
      if (result != null) { where.append(" AND result = ?"); params.add(result); }
      if (startDate != null) { where.append(" AND created_at >= ?"); params.add(startDate.atStartOfDay()); }
      if (endDate != null) { where.append(" AND created_at < ?"); params.add(endDate.plusDays(1).atStartOfDay()); }

      List<Map<String, Object>> rows = jdbcTemplate.queryForList(
          "SELECT username, login_type, ip_address, result, fail_reason, created_at " +
          "FROM login_log " + where + " ORDER BY created_at DESC LIMIT 5000",
          params.toArray());

      List<LoginLogExportVO> data = rows.stream().map(row -> {
          LoginLogExportVO vo = new LoginLogExportVO();
          vo.setUsername((String) row.get("username"));
          vo.setLoginType((String) row.get("login_type"));
          vo.setIpAddress((String) row.get("ip_address"));
          Object res = row.get("result");
          vo.setResult(Integer.valueOf(1).equals(res) ? "成功" : "失败");
          vo.setFailReason((String) row.get("fail_reason"));
          Object ca = row.get("created_at");
          vo.setCreatedAt(ca != null ? ca.toString() : "");
          return vo;
      }).toList();

      String fileName = URLEncoder.encode("登录日志_" + LocalDate.now(), StandardCharsets.UTF_8)
          .replace("+", "%20");
      response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
      response.setCharacterEncoding("UTF-8");
      response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
      response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
      EasyExcel.write(response.getOutputStream(), LoginLogExportVO.class)
          .sheet("登录日志").doWrite(data);
  }
  ```

- [ ] **Step 3: 验证编译通过**

  ```powershell
  cd tengyei-backend; mvn compile -q
  ```
  预期：BUILD SUCCESS。

- [ ] **Step 4: Commit**

  ```bash
  git add tengyei-backend/app/src/main/java/com/tengyei/controller/AuditLogController.java \
    tengyei-backend/app/src/main/java/com/tengyei/controller/LoginLogController.java
  git commit -m "feat: add audit log and login log export endpoints"
  ```

---

## Task 4: 前端下载工具 + API 方法

**Files:**
- Create: `tengyei-frontend/src/utils/download.ts`
- Modify: `tengyei-frontend/src/api/user.ts`
- Modify: `tengyei-frontend/src/api/audit.ts`

- [ ] **Step 1: 创建 download.ts 工具函数**

  创建文件 `tengyei-frontend/src/utils/download.ts`：

  ```typescript
  import request from '@/api/request'
  import type { AxiosResponse } from 'axios'

  /**
   * 通过 axios blob 请求下载 Excel 文件。
   * request.ts 拦截器对非 JSON 响应返回完整 AxiosResponse，
   * 所以需要 as unknown as AxiosResponse<Blob> 转型。
   */
  export async function downloadExcel(
    url: string,
    params: Record<string, unknown>,
    filename: string
  ): Promise<void> {
    const res = (await request.get(url, {
      params,
      responseType: 'blob',
    })) as unknown as AxiosResponse<Blob>
    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const href = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = href
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(href)
  }
  ```

- [ ] **Step 2: 在 api/user.ts 添加 export、batchStatus、batchRoles 方法**

  读取现有 `tengyei-frontend/src/api/user.ts`，在文件顶部 import 区添加：
  ```typescript
  import { downloadExcel } from '@/utils/download'
  ```

  在 `userApi` 对象末尾（`resetPassword` 之后）追加：

  ```typescript
  export: (params: { keyword?: string; deptId?: number }) =>
    downloadExcel('/v1/users/export', params as Record<string, unknown>, `人员列表_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '')}.xlsx`),

  batchStatus: (ids: number[], status: number) =>
    request.put<never, void>('/v1/users/batch/status', { ids, status }),

  batchRoles: (ids: number[], roleIds: number[]) =>
    request.put<never, void>('/v1/users/batch/roles', { ids, roleIds }),
  ```

- [ ] **Step 3: 在 api/audit.ts 添加 export 方法**

  在 `audit.ts` 顶部 import 区添加：
  ```typescript
  import { downloadExcel } from '@/utils/download'
  ```

  在 `auditApi` 对象末尾追加：
  ```typescript
  export: (params: { module?: string; startDate?: string; endDate?: string }) =>
    downloadExcel('/v1/audit-logs/export', params as Record<string, unknown>, `操作日志_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '')}.xlsx`),
  ```

  在 `loginLogApi` 对象末尾追加：
  ```typescript
  export: (params: { username?: string; result?: number; startDate?: string; endDate?: string }) =>
    downloadExcel('/v1/login-logs/export', params as Record<string, unknown>, `登录日志_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '')}.xlsx`),
  ```

- [ ] **Step 4: 类型检查**

  ```powershell
  cd tengyei-frontend; npx vue-tsc --noEmit 2>&1 | head -30
  ```
  预期：无 error（warning 可接受）。

- [ ] **Step 5: Commit**

  ```bash
  git add tengyei-frontend/src/utils/download.ts \
    tengyei-frontend/src/api/user.ts \
    tengyei-frontend/src/api/audit.ts
  git commit -m "feat: add frontend download utility and export API methods"
  ```

---

## Task 5: 前端导出按钮（UserListView + AuditLogView）

**Files:**
- Modify: `tengyei-frontend/src/views/user/UserListView.vue`
- Modify: `tengyei-frontend/src/views/audit/AuditLogView.vue`

- [ ] **Step 1: 在 UserListView 添加导出按钮**

  在 `UserListView.vue` 的 `<script setup>` 区：

  找到 `import { userApi } from '@/api/user'` 这行，这已经包含 userApi（其中现在有 export 方法）。

  在 `fetchDepts()` 函数之后添加导出函数：

  ```typescript
  const exporting = ref(false)
  async function exportList() {
    exporting.value = true
    try {
      await userApi.export({
        keyword: query.keyword || undefined,
        deptId: query.deptId,
      })
    } finally {
      exporting.value = false
    }
  }
  ```

  在 `<template>` 的 toolbar 区，找到「搜索」按钮之后、「新增用户」按钮之前，插入：

  ```html
  <el-button :loading="exporting" @click="exportList">导出</el-button>
  ```

- [ ] **Step 2: 在 AuditLogView 添加导出按钮**

  在 `AuditLogView.vue` 的 `<script setup>` 区：

  在 `auditApi` import 行确认已有 auditApi 和 loginLogApi（它们现在都有 export 方法）。

  在 `onSearch()` 函数之后添加：

  ```typescript
  const exporting = ref(false)
  async function exportAudit() {
    exporting.value = true
    try {
      await auditApi.export({
        module: query.module || undefined,
        startDate: query.startDate || undefined,
        endDate: query.endDate || undefined,
      })
    } finally {
      exporting.value = false
    }
  }

  const loginExporting = ref(false)
  async function exportLogin() {
    loginExporting.value = true
    try {
      await loginLogApi.export({
        username: loginQuery.username || undefined,
        result: loginQuery.result,
        startDate: loginQuery.startDate || undefined,
        endDate: loginQuery.endDate || undefined,
      })
    } finally {
      loginExporting.value = false
    }
  }
  ```

  在操作日志 tab 的搜索重置按钮行之后插入导出按钮（在搜索栏工具行，紧跟重置按钮）：

  ```html
  <el-button :loading="exporting" @click="exportAudit">导出</el-button>
  ```

  在登录日志 tab 的搜索按钮之后插入：

  ```html
  <el-button :loading="loginExporting" @click="exportLogin">导出</el-button>
  ```

  注意：`ref` 已在 AuditLogView 的 import 中（`import { onMounted, reactive, ref } from 'vue'`），若没有 ref 需要补充。

- [ ] **Step 3: 类型检查**

  ```powershell
  cd tengyei-frontend; npx vue-tsc --noEmit 2>&1 | head -30
  ```
  预期：无 error。

- [ ] **Step 4: Commit**

  ```bash
  git add tengyei-frontend/src/views/user/UserListView.vue \
    tengyei-frontend/src/views/audit/AuditLogView.vue
  git commit -m "feat: add export buttons to user list and audit log views"
  ```

---

## Task 6: 批量操作后端（DTO + UserService + UserController）

**Files:**
- Create: `tengyei-backend/core-org/src/main/java/com/tengyei/org/dto/BatchStatusDTO.java`
- Create: `tengyei-backend/core-org/src/main/java/com/tengyei/org/dto/BatchRolesDTO.java`
- Modify: `tengyei-backend/core-org/src/main/java/com/tengyei/org/service/UserService.java`
- Modify: `tengyei-backend/core-org/src/main/java/com/tengyei/org/controller/UserController.java`

- [ ] **Step 1: 创建 BatchStatusDTO**

  创建文件 `tengyei-backend/core-org/src/main/java/com/tengyei/org/dto/BatchStatusDTO.java`：

  ```java
  package com.tengyei.org.dto;

  import jakarta.validation.constraints.NotEmpty;
  import jakarta.validation.constraints.NotNull;
  import lombok.Data;

  import java.util.List;

  @Data
  public class BatchStatusDTO {
      @NotEmpty(message = "ids 不能为空")
      private List<Long> ids;

      @NotNull(message = "status 不能为空")
      private Integer status;
  }
  ```

- [ ] **Step 2: 创建 BatchRolesDTO**

  创建文件 `tengyei-backend/core-org/src/main/java/com/tengyei/org/dto/BatchRolesDTO.java`：

  ```java
  package com.tengyei.org.dto;

  import jakarta.validation.constraints.NotEmpty;
  import jakarta.validation.constraints.NotNull;
  import lombok.Data;

  import java.util.List;

  @Data
  public class BatchRolesDTO {
      @NotEmpty(message = "ids 不能为空")
      private List<Long> ids;

      @NotNull(message = "roleIds 不能为空")
      private List<Long> roleIds;
  }
  ```

- [ ] **Step 3: 在 UserService 添加批量方法**

  在 `UserService.java` 的 `export()` 方法之后追加两个方法：

  ```java
  @Transactional
  public void batchChangeStatus(List<Long> ids, Integer status) {
      if (status == null || (status != 0 && status != 1)) {
          throw new BusinessException(422, "状态值无效");
      }
      Long tenantId = TenantContext.getTenantId();
      String inClause = String.join(",", ids.stream().map(String::valueOf).toList());
      if (!TenantContext.isSuperAdmin()) {
          jdbcTemplate.update(
              "UPDATE `user` SET status = ? WHERE id IN (" + inClause + ") AND tenant_id = ? AND is_deleted = 0",
              status, tenantId);
      } else {
          jdbcTemplate.update(
              "UPDATE `user` SET status = ? WHERE id IN (" + inClause + ") AND is_deleted = 0",
              status);
      }
  }

  @Transactional
  public void batchAssignRoles(List<Long> ids, List<Long> roleIds) {
      Long tenantId = TenantContext.getTenantId();
      for (Long userId : ids) {
          if (!TenantContext.isSuperAdmin()) {
              Long count = jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM `user` WHERE id = ? AND tenant_id = ? AND is_deleted = 0",
                  Long.class, userId, tenantId);
              if (count == null || count == 0) continue;
          }
          jdbcTemplate.update("DELETE FROM user_role WHERE user_id = ?", userId);
          for (Long roleId : roleIds) {
              jdbcTemplate.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
          }
      }
  }
  ```

  注意：`String.join` 和 stream 已有相关 import。

- [ ] **Step 4: 在 UserController 添加批量端点**

  在 `UserController.java` 的 import 区添加（如未有）：
  ```java
  import com.tengyei.org.dto.BatchStatusDTO;
  import com.tengyei.org.dto.BatchRolesDTO;
  import com.tengyei.common.exception.BusinessException;
  ```

  在 `export()` 端点之后追加：

  ```java
  @PutMapping("/batch/status")
  @PreAuthorize("hasAuthority('PERM_user:edit')")
  @Auditable(module = "人员管理", actionType = "BATCH_UPDATE", description = "批量变更人员状态")
  public Result<Void> batchStatus(@Valid @RequestBody BatchStatusDTO dto) {
      if (dto.getIds().size() > 200) {
          throw new BusinessException(422, "单次最多操作 200 条");
      }
      userService.batchChangeStatus(dto.getIds(), dto.getStatus());
      return Result.ok();
  }

  @PutMapping("/batch/roles")
  @PreAuthorize("hasAuthority('PERM_user:edit')")
  @Auditable(module = "人员管理", actionType = "BATCH_UPDATE", description = "批量分配角色")
  public Result<Void> batchRoles(@Valid @RequestBody BatchRolesDTO dto) {
      if (dto.getIds().size() > 200) {
          throw new BusinessException(422, "单次最多操作 200 条");
      }
      userService.batchAssignRoles(dto.getIds(), dto.getRoleIds());
      return Result.ok();
  }
  ```

- [ ] **Step 5: 验证编译通过**

  ```powershell
  cd tengyei-backend; mvn compile -q
  ```
  预期：BUILD SUCCESS。

- [ ] **Step 6: Commit**

  ```bash
  git add tengyei-backend/core-org/src/main/java/com/tengyei/org/dto/BatchStatusDTO.java \
    tengyei-backend/core-org/src/main/java/com/tengyei/org/dto/BatchRolesDTO.java \
    tengyei-backend/core-org/src/main/java/com/tengyei/org/service/UserService.java \
    tengyei-backend/core-org/src/main/java/com/tengyei/org/controller/UserController.java
  git commit -m "feat: add batch status and batch role assignment endpoints for users"
  ```

---

## Task 7: 前端批量操作 UI（UserListView）

**Files:**
- Modify: `tengyei-frontend/src/views/user/UserListView.vue`

- [ ] **Step 1: 添加 selectedIds 状态和批量角色对话框状态**

  在 `UserListView.vue` `<script setup>` 的 `const exporting = ref(false)` 之后添加：

  ```typescript
  /* ---- 批量操作 ---- */
  const selectedIds = ref<number[]>([])

  function onSelectionChange(rows: UserVO[]) {
    selectedIds.value = rows.map(r => r.id)
  }

  function clearSelection() {
    selectedIds.value = []
  }

  const batchRoleDialog = ref(false)
  const batchRoleIds = ref<number[]>([])

  function openBatchRoles() {
    batchRoleIds.value = []
    batchRoleDialog.value = true
  }

  async function submitBatchStatus(status: number) {
    if (!selectedIds.value.length) return
    const action = status === 1 ? '启用' : '停用'
    await ElMessageBox.confirm(`确认批量${action} ${selectedIds.value.length} 个用户？`, '提示', { type: 'warning' })
    await userApi.batchStatus(selectedIds.value, status)
    ElMessage.success(`已批量${action}`)
    clearSelection()
    fetchList()
  }

  async function submitBatchRoles() {
    if (!selectedIds.value.length) return
    await userApi.batchRoles(selectedIds.value, batchRoleIds.value)
    ElMessage.success('角色已批量分配')
    batchRoleDialog.value = false
    clearSelection()
    fetchList()
  }
  ```

- [ ] **Step 2: 在 el-table 添加选择列**

  在 `<el-table>` 开头（`<el-table-column prop="realName"` 之前）插入：

  ```html
  <el-table-column type="selection" width="50" />
  ```

  在 `<el-table>` 标签上添加事件：

  ```html
  <el-table v-loading="loading" :data="list" stripe @selection-change="onSelectionChange">
  ```

- [ ] **Step 3: 在 toolbar 和表格之间插入批量操作栏**

  在 toolbar `</div>` 之后、`<el-table>` 之前插入：

  ```html
  <div v-if="selectedIds.length" class="batch-bar">
    <span>已选 {{ selectedIds.length }} 条</span>
    <el-button size="small" type="success" @click="submitBatchStatus(1)">批量启用</el-button>
    <el-button size="small" type="warning" @click="submitBatchStatus(0)">批量停用</el-button>
    <el-button size="small" type="primary" @click="openBatchRoles">批量分配角色</el-button>
    <el-button size="small" @click="clearSelection">取消选择</el-button>
  </div>
  ```

- [ ] **Step 4: 添加批量分配角色对话框**

  在角色分配对话框（`roleDialog`）之后追加：

  ```html
  <el-dialog v-model="batchRoleDialog" title="批量分配角色" width="420px">
    <p style="margin-bottom: 12px; color: #6b7280">将为已选 {{ selectedIds.length }} 个用户统一分配以下角色：</p>
    <el-checkbox-group v-model="batchRoleIds">
      <el-checkbox v-for="r in roles" :key="r.id" :value="r.id" style="display: block; margin: 6px 0">
        {{ r.name }}
      </el-checkbox>
    </el-checkbox-group>
    <template #footer>
      <el-button @click="batchRoleDialog = false">取消</el-button>
      <el-button type="primary" @click="submitBatchRoles">确定</el-button>
    </template>
  </el-dialog>
  ```

- [ ] **Step 5: 在 `<style scoped>` 中添加批量操作栏样式**

  在已有的 `.pager` 样式之后追加：

  ```css
  .batch-bar {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    background: #eff6ff;
    border-radius: 6px;
    margin-bottom: 12px;
    font-size: 13px;
    color: #374151;
  }
  ```

- [ ] **Step 6: 类型检查**

  ```powershell
  cd tengyei-frontend; npx vue-tsc --noEmit 2>&1 | head -30
  ```
  预期：无 error。

- [ ] **Step 7: Commit**

  ```bash
  git add tengyei-frontend/src/views/user/UserListView.vue
  git commit -m "feat: add batch selection and batch operations UI to user list"
  ```

---

## Task 8: 仪表盘图表数据 API（后端）

**Files:**
- Modify: `tengyei-backend/app/src/main/java/com/tengyei/service/DashboardService.java`
- Modify: `tengyei-backend/app/src/main/java/com/tengyei/controller/DashboardController.java`

- [ ] **Step 1: 在 DashboardService 添加 chartData() 方法**

  在 `DashboardService.java` 的 `count()` 方法之前，追加 `chartData()` 方法：

  ```java
  public Map<String, Object> chartData() {
      Map<String, Object> data = new HashMap<>();
      if (TenantContext.isSuperAdmin()) {
          // 近30天新增用户趋势（按天）
          data.put("userTrend", jdbcTemplate.queryForList(
              "SELECT DATE(created_at) AS `date`, COUNT(*) AS `count` " +
              "FROM `user` " +
              "WHERE is_deleted = 0 AND is_super_admin = 0 " +
              "AND created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
              "GROUP BY DATE(created_at) ORDER BY DATE(created_at)"));
          // 近30天操作类型分布
          data.put("actionDist", jdbcTemplate.queryForList(
              "SELECT action_type AS name, COUNT(*) AS value " +
              "FROM audit_log " +
              "WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
              "GROUP BY action_type ORDER BY value DESC"));
      } else {
          Long tenantId = TenantContext.getTenantId();
          // 近7天登录活跃度（按天）
          data.put("loginTrend", jdbcTemplate.queryForList(
              "SELECT DATE(created_at) AS `date`, COUNT(*) AS `count` " +
              "FROM login_log " +
              "WHERE tenant_id = ? " +
              "AND created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
              "GROUP BY DATE(created_at) ORDER BY DATE(created_at)",
              tenantId));
          // 部门人员分布
          data.put("deptDist", jdbcTemplate.queryForList(
              "SELECT d.name AS name, COUNT(u.id) AS value " +
              "FROM dept d LEFT JOIN `user` u ON u.dept_id = d.id AND u.is_deleted = 0 " +
              "WHERE d.tenant_id = ? AND d.is_deleted = 0 " +
              "GROUP BY d.id, d.name ORDER BY value DESC",
              tenantId));
      }
      return data;
  }
  ```

- [ ] **Step 2: 在 DashboardController 添加 chart-data 端点**

  读取 `DashboardController.java`，在 `stats()` 端点之后追加：

  ```java
  @GetMapping("/chart-data")
  public Result<Map<String, Object>> chartData() {
      return Result.ok(dashboardService.chartData());
  }
  ```

- [ ] **Step 3: 验证编译通过**

  ```powershell
  cd tengyei-backend; mvn compile -q
  ```
  预期：BUILD SUCCESS。

- [ ] **Step 4: Commit**

  ```bash
  git add tengyei-backend/app/src/main/java/com/tengyei/service/DashboardService.java \
    tengyei-backend/app/src/main/java/com/tengyei/controller/DashboardController.java
  git commit -m "feat: add dashboard chart-data endpoint with trend and distribution queries"
  ```

---

## Task 9: 前端数据类型 + API（dashboard.ts + types/dashboard.ts）

**Files:**
- Modify: `tengyei-frontend/src/types/dashboard.ts`
- Modify: `tengyei-frontend/src/api/dashboard.ts`

- [ ] **Step 1: 更新 types/dashboard.ts 添加 ChartData 类型**

  读取 `tengyei-frontend/src/types/dashboard.ts`，在现有 `DashboardStats` 类型之后追加：

  ```typescript
  export interface TrendItem {
    date: string
    count: number
  }

  export interface DistItem {
    name: string
    value: number
  }

  export interface SuperChartData {
    userTrend: TrendItem[]
    actionDist: DistItem[]
  }

  export interface CompanyChartData {
    loginTrend: TrendItem[]
    deptDist: DistItem[]
  }
  ```

- [ ] **Step 2: 在 dashboard.ts 添加 chartData API 方法**

  在 `dashboardApi` 对象末尾追加：

  ```typescript
  chartData: () =>
    request.get<never, SuperChartData | CompanyChartData>('/v1/dashboard/chart-data'),
  ```

  在文件顶部 import 区添加类型导入：
  ```typescript
  import type { DashboardStats, SuperChartData, CompanyChartData } from '@/types/dashboard'
  ```

- [ ] **Step 3: 类型检查**

  ```powershell
  cd tengyei-frontend; npx vue-tsc --noEmit 2>&1 | head -30
  ```
  预期：无 error。

- [ ] **Step 4: Commit**

  ```bash
  git add tengyei-frontend/src/types/dashboard.ts \
    tengyei-frontend/src/api/dashboard.ts
  git commit -m "feat: add chart data types and API method for dashboard"
  ```

---

## Task 10: 前端 ECharts 图表（SuperDashboard + CompanyDashboard）

**Files:**
- Modify: `tengyei-frontend/src/views/dashboard/SuperDashboard.vue`
- Modify: `tengyei-frontend/src/views/dashboard/CompanyDashboard.vue`

- [ ] **Step 1: 安装 echarts**

  ```powershell
  cd tengyei-frontend; npm install echarts
  ```
  预期：echarts 出现在 package.json dependencies 中。

- [ ] **Step 2: 重写 SuperDashboard.vue**

  完整替换 `tengyei-frontend/src/views/dashboard/SuperDashboard.vue` 内容：

  ```vue
  <script setup lang="ts">
  import { onMounted, onUnmounted, ref } from 'vue'
  import { useRouter } from 'vue-router'
  import { dashboardApi } from '@/api/dashboard'
  import type { DashboardStats, SuperChartData, TrendItem, DistItem } from '@/types/dashboard'
  import * as echarts from 'echarts/core'
  import { LineChart, PieChart } from 'echarts/charts'
  import { GridComponent, TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components'
  import { CanvasRenderer } from 'echarts/renderers'

  echarts.use([LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent, CanvasRenderer])

  const stats = ref<DashboardStats | null>(null)
  const loading = ref(true)
  const router = useRouter()

  const lineRef = ref<HTMLDivElement>()
  const pieRef = ref<HTMLDivElement>()
  let lineChart: echarts.ECharts | null = null
  let pieChart: echarts.ECharts | null = null

  function initLineChart(trend: TrendItem[]) {
    if (!lineRef.value) return
    lineChart = echarts.init(lineRef.value)
    lineChart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: trend.map(t => t.date), axisLabel: { rotate: 30 } },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{ name: '新增用户', type: 'line', smooth: true, data: trend.map(t => t.count), itemStyle: { color: '#1d4ed8' } }],
    })
  }

  function initPieChart(dist: DistItem[]) {
    if (!pieRef.value) return
    pieChart = echarts.init(pieRef.value)
    pieChart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, type: 'scroll' },
      series: [{
        name: '操作类型',
        type: 'pie',
        radius: ['40%', '70%'],
        data: dist.map(d => ({ name: d.name, value: d.value })),
      }],
    })
  }

  function resize() {
    lineChart?.resize()
    pieChart?.resize()
  }

  onMounted(async () => {
    try {
      stats.value = await dashboardApi.stats()
      const chartData = await dashboardApi.chartData() as SuperChartData
      initLineChart(chartData.userTrend ?? [])
      initPieChart(chartData.actionDist ?? [])
    } finally {
      loading.value = false
    }
    window.addEventListener('resize', resize)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', resize)
    lineChart?.dispose()
    pieChart?.dispose()
  })

  const statusText = (s: number) => (s === 1 ? '启用' : s === 2 ? '停用' : '待激活')
  </script>

  <template>
    <div v-loading="loading" class="dashboard">
      <div class="stat-cards">
        <el-card class="stat-card clickable" shadow="never" @click="router.push('/admin/companies')">
          <div class="stat-label">企业总数</div>
          <div class="stat-value">{{ stats?.companyTotal ?? 0 }}</div>
        </el-card>
        <el-card class="stat-card clickable" shadow="never" @click="router.push('/admin/companies')">
          <div class="stat-label">活跃企业</div>
          <div class="stat-value">{{ stats?.companyActive ?? 0 }}</div>
        </el-card>
        <el-card class="stat-card clickable" shadow="never" @click="router.push('/admin/companies')">
          <div class="stat-label">今日新增企业</div>
          <div class="stat-value">{{ stats?.companyTodayNew ?? 0 }}</div>
        </el-card>
        <el-card class="stat-card clickable" shadow="never" @click="router.push('/admin/companies')">
          <div class="stat-label">总用户数</div>
          <div class="stat-value">{{ stats?.userTotal ?? 0 }}</div>
        </el-card>
      </div>

      <div class="charts-row">
        <el-card shadow="never" class="chart-card">
          <template #header><span>近30天新增用户趋势</span></template>
          <div ref="lineRef" class="chart" />
        </el-card>
        <el-card shadow="never" class="chart-card">
          <template #header><span>近30天操作类型分布</span></template>
          <div ref="pieRef" class="chart" />
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
  .stat-card { border-radius: 10px; }
  .stat-card.clickable {
    cursor: pointer;
    transition: box-shadow 0.2s, transform 0.2s;
  }
  .stat-card.clickable:hover {
    box-shadow: 0 2px 12px rgba(59, 130, 246, 0.2);
    transform: translateY(-1px);
  }
  .stat-label { color: #6b7280; font-size: 13px; margin-bottom: 8px; }
  .stat-value { font-size: 28px; font-weight: 700; color: #1f2937; }
  .charts-row {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
    margin-bottom: 16px;
  }
  .chart-card { border-radius: 10px; }
  .chart { height: 280px; width: 100%; }
  .recent-card { border-radius: 10px; }
  </style>
  ```

- [ ] **Step 3: 重写 CompanyDashboard.vue**

  完整替换 `tengyei-frontend/src/views/dashboard/CompanyDashboard.vue` 内容：

  ```vue
  <script setup lang="ts">
  import { onMounted, onUnmounted, ref } from 'vue'
  import { useRouter } from 'vue-router'
  import { dashboardApi } from '@/api/dashboard'
  import { useAuthStore } from '@/stores/auth'
  import type { DashboardStats, CompanyChartData, TrendItem, DistItem } from '@/types/dashboard'
  import * as echarts from 'echarts/core'
  import { LineChart, PieChart } from 'echarts/charts'
  import { GridComponent, TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components'
  import { CanvasRenderer } from 'echarts/renderers'

  echarts.use([LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent, CanvasRenderer])

  const stats = ref<DashboardStats | null>(null)
  const loading = ref(true)
  const router = useRouter()
  const auth = useAuthStore()

  const lineRef = ref<HTMLDivElement>()
  const pieRef = ref<HTMLDivElement>()
  let lineChart: echarts.ECharts | null = null
  let pieChart: echarts.ECharts | null = null

  function initLineChart(trend: TrendItem[]) {
    if (!lineRef.value) return
    lineChart = echarts.init(lineRef.value)
    lineChart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: trend.map(t => t.date), axisLabel: { rotate: 30 } },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{ name: '登录次数', type: 'line', smooth: true, data: trend.map(t => t.count), itemStyle: { color: '#10b981' } }],
    })
  }

  function initPieChart(dist: DistItem[]) {
    if (!pieRef.value) return
    pieChart = echarts.init(pieRef.value)
    pieChart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, type: 'scroll' },
      series: [{
        name: '部门人数',
        type: 'pie',
        radius: ['40%', '70%'],
        data: dist.map(d => ({ name: d.name, value: d.value })),
      }],
    })
  }

  function resize() {
    lineChart?.resize()
    pieChart?.resize()
  }

  onMounted(async () => {
    try {
      stats.value = await dashboardApi.stats()
      const chartData = await dashboardApi.chartData() as CompanyChartData
      initLineChart(chartData.loginTrend ?? [])
      initPieChart(chartData.deptDist ?? [])
    } finally {
      loading.value = false
    }
    window.addEventListener('resize', resize)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', resize)
    lineChart?.dispose()
    pieChart?.dispose()
  })

  interface Shortcut {
    title: string
    path: string
    perm: string
  }
  const shortcuts: Shortcut[] = [
    { title: '组织管理', path: '/company/org', perm: 'PERM_dept:view' },
    { title: '人员管理', path: '/company/users', perm: 'PERM_user:view' },
    { title: '角色与权限', path: '/company/roles', perm: 'PERM_role:view' },
  ]
  </script>

  <template>
    <div v-loading="loading" class="dashboard">
      <div class="stat-cards">
        <el-card class="stat-card clickable" shadow="never" @click="router.push('/company/org')">
          <div class="stat-label">部门数</div>
          <div class="stat-value">{{ stats?.deptCount ?? 0 }}</div>
        </el-card>
        <el-card class="stat-card clickable" shadow="never" @click="router.push('/company/org')">
          <div class="stat-label">分支机构</div>
          <div class="stat-value">{{ stats?.branchCount ?? 0 }}</div>
        </el-card>
        <el-card class="stat-card clickable" shadow="never" @click="router.push('/company/users')">
          <div class="stat-label">人员总数</div>
          <div class="stat-value">{{ stats?.userCount ?? 0 }}</div>
        </el-card>
        <el-card class="stat-card clickable" shadow="never" @click="router.push('/company/users')">
          <div class="stat-label">今日登录</div>
          <div class="stat-value">{{ stats?.todayLoginCount ?? 0 }}</div>
        </el-card>
      </div>

      <div class="charts-row">
        <el-card shadow="never" class="chart-card">
          <template #header><span>近7天登录活跃度</span></template>
          <div ref="lineRef" class="chart" />
        </el-card>
        <el-card shadow="never" class="chart-card">
          <template #header><span>部门人员分布</span></template>
          <div ref="pieRef" class="chart" />
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
  .stat-card { border-radius: 10px; }
  .stat-card.clickable {
    cursor: pointer;
    transition: box-shadow 0.2s, transform 0.2s;
  }
  .stat-card.clickable:hover {
    box-shadow: 0 2px 12px rgba(59, 130, 246, 0.2);
    transform: translateY(-1px);
  }
  .stat-label { color: #6b7280; font-size: 13px; margin-bottom: 8px; }
  .stat-value { font-size: 28px; font-weight: 700; color: #1f2937; }
  .charts-row {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
    margin-bottom: 16px;
  }
  .chart-card { border-radius: 10px; }
  .chart { height: 280px; width: 100%; }
  .shortcut-card { border-radius: 10px; }
  .shortcuts { display: flex; gap: 16px; flex-wrap: wrap; }
  .shortcut {
    padding: 16px 28px;
    background: #f0f5ff;
    color: var(--color-primary, #3b82f6);
    border-radius: 8px;
    cursor: pointer;
    font-weight: 600;
  }
  .shortcut:hover { background: #e0ecff; }
  </style>
  ```

- [ ] **Step 4: 类型检查**

  ```powershell
  cd tengyei-frontend; npx vue-tsc --noEmit 2>&1 | head -30
  ```
  预期：无 error。

- [ ] **Step 5: Commit**

  ```bash
  git add tengyei-frontend/src/views/dashboard/SuperDashboard.vue \
    tengyei-frontend/src/views/dashboard/CompanyDashboard.vue \
    tengyei-frontend/package.json \
    tengyei-frontend/package-lock.json
  git commit -m "feat: add ECharts line and pie charts to admin and company dashboards"
  ```

---

## Self-Review

**1. Spec coverage:**
- A1 Excel 导出 ✅ Task 1-5: 人员/操作日志/登录日志三个导出，5000行限制，带筛选参数
- A2 批量操作 ✅ Task 6-7: 批量启用/停用/分配角色，200条/次限制，浮动操作栏
- A3 仪表盘图表 ✅ Task 8-10: ECharts 按需引入，超管折线+饼图，企业折线+饼图，resize 响应式

**2. Placeholder scan:** 无 TBD/TODO/占位符。

**3. Type consistency:**
- `TrendItem`、`DistItem`、`SuperChartData`、`CompanyChartData` 在 Task 9 定义，在 Task 10 使用 ✅
- `UserExportVO` 在 Task 1 定义，在 Task 2 使用 ✅
- `BatchStatusDTO`、`BatchRolesDTO` 在 Task 6 定义，同 Task 使用 ✅
- `batchStatus`、`batchRoles` 在 Task 4 的 api/user.ts 定义，在 Task 7 前端调用 ✅
