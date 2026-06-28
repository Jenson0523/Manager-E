# Phase 1: 项目脚手架 + 数据库 + 认证体系 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建腾飞多租户企业管理系统的完整技术基座：Spring Boot 模块化单体后端 + Vue 3 前端，实现多租户数据库隔离、JWT 认证、登录/登出/权限加载全流程。

**Architecture:** Spring Boot 3 Maven 多模块项目（common / core-auth / core-company / core-org / core-rbac / audit / app），MyBatis-Plus TenantLineInnerInterceptor 自动注入 tenant_id，Flyway 管理数据库版本，Vue 3 + Pinia 动态路由按权限点加载菜单。

**Tech Stack:** Java 17, Spring Boot 3.2, MyBatis-Plus 3.5, Flyway 9, Redis (Lettuce), JWT (jjwt 0.12), Vue 3, Vite 5, Element Plus 2.x, Pinia, Axios, TypeScript

---

## 文件结构预览

```
E:/OneDrive/manager-software/
├── tengyei-backend/                    # 后端根目录
│   ├── pom.xml                         # 父 POM（dependencyManagement）
│   ├── common/                         # 共享工具、实体基类、常量
│   │   ├── pom.xml
│   │   └── src/main/java/com/tengyei/common/
│   │       ├── entity/BaseEntity.java   # 基础字段（id, tenant_id, created_at...）
│   │       ├── context/TenantContext.java # ThreadLocal 租户上下文
│   │       ├── response/Result.java     # 统一响应体
│   │       ├── exception/              # 全局异常
│   │       └── config/                 # MyBatis-Plus、Redis 公共配置
│   ├── core-auth/                      # 认证模块
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/java/com/tengyei/auth/
│   │       │   ├── controller/AuthController.java
│   │       │   ├── service/AuthService.java
│   │       │   ├── service/TokenService.java
│   │       │   ├── filter/JwtAuthFilter.java
│   │       │   ├── config/SecurityConfig.java
│   │       │   └── dto/             # LoginRequest, LoginResponse, UserInfoVO
│   │       └── test/java/com/tengyei/auth/
│   │           └── AuthControllerTest.java
│   ├── core-company/                   # 公司管理（Plan 2 填充）
│   ├── core-org/                       # 组织管理（Plan 3 填充）
│   ├── core-rbac/                      # 角色权限（Plan 4 填充）
│   ├── audit/                          # 审计日志（Plan 5 填充）
│   └── app/                            # 可运行的 Spring Boot 主应用
│       ├── pom.xml
│       └── src/main/
│           ├── java/com/tengyei/TengyeiApplication.java
│           └── resources/
│               ├── application.yml
│               ├── application-dev.yml
│               └── db/migration/       # Flyway SQL 文件
│                   ├── V1__create_tables.sql
│                   └── V2__init_data.sql
└── tengyei-frontend/                   # 前端根目录
    ├── package.json
    ├── vite.config.ts
    ├── tsconfig.json
    └── src/
        ├── main.ts
        ├── App.vue
        ├── api/auth.ts                 # 认证接口
        ├── utils/request.ts            # Axios 封装（JWT 自动刷新）
        ├── stores/auth.ts              # Pinia 认证 store
        ├── router/index.ts             # 路由 + 动态路由守卫
        ├── views/
        │   ├── login/LoginView.vue     # 登录页
        │   └── error/403View.vue
        ├── layouts/
        │   └── BlankLayout.vue         # 登录页布局
        └── styles/
            ├── variables.css           # CSS 变量（颜色系统）
            └── element-override.css    # Element Plus 主题覆盖
```

---

## Task 1: Spring Boot 父 POM + 模块骨架

**Files:**
- Create: `tengyei-backend/pom.xml`
- Create: `tengyei-backend/common/pom.xml`
- Create: `tengyei-backend/core-auth/pom.xml`
- Create: `tengyei-backend/core-company/pom.xml`
- Create: `tengyei-backend/core-org/pom.xml`
- Create: `tengyei-backend/core-rbac/pom.xml`
- Create: `tengyei-backend/audit/pom.xml`
- Create: `tengyei-backend/app/pom.xml`

- [ ] **Step 1: 创建项目目录结构**

```powershell
$base = "E:\OneDrive\manager-software\tengyei-backend"
New-Item -ItemType Directory -Force -Path @(
  "$base",
  "$base\common\src\main\java\com\tengyei\common\entity",
  "$base\common\src\main\java\com\tengyei\common\context",
  "$base\common\src\main\java\com\tengyei\common\response",
  "$base\common\src\main\java\com\tengyei\common\exception",
  "$base\common\src\main\java\com\tengyei\common\config",
  "$base\common\src\main\resources",
  "$base\common\src\test\java\com\tengyei\common",
  "$base\core-auth\src\main\java\com\tengyei\auth\controller",
  "$base\core-auth\src\main\java\com\tengyei\auth\service",
  "$base\core-auth\src\main\java\com\tengyei\auth\filter",
  "$base\core-auth\src\main\java\com\tengyei\auth\config",
  "$base\core-auth\src\main\java\com\tengyei\auth\dto",
  "$base\core-auth\src\test\java\com\tengyei\auth",
  "$base\core-company\src\main\java\com\tengyei\company",
  "$base\core-company\src\test\java\com\tengyei\company",
  "$base\core-org\src\main\java\com\tengyei\org",
  "$base\core-org\src\test\java\com\tengyei\org",
  "$base\core-rbac\src\main\java\com\tengyei\rbac",
  "$base\core-rbac\src\test\java\com\tengyei\rbac",
  "$base\audit\src\main\java\com\tengyei\audit",
  "$base\audit\src\test\java\com\tengyei\audit",
  "$base\app\src\main\java\com\tengyei",
  "$base\app\src\main\resources\db\migration"
)
```

- [ ] **Step 2: 写父 POM**

创建 `tengyei-backend/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.tengyei</groupId>
    <artifactId>tengyei-backend</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <modules>
        <module>common</module>
        <module>core-auth</module>
        <module>core-company</module>
        <module>core-org</module>
        <module>core-rbac</module>
        <module>audit</module>
        <module>app</module>
    </modules>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring-boot.version>3.2.5</spring-boot.version>
        <mybatis-plus.version>3.5.7</mybatis-plus.version>
        <jjwt.version>0.12.5</jjwt.version>
        <flyway.version>9.22.3</flyway.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>com.baomidou</groupId>
                <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
                <version>${mybatis-plus.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-api</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-impl</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-jackson</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <dependency>
                <groupId>com.tengyei</groupId>
                <artifactId>common</artifactId>
                <version>${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

- [ ] **Step 3: 写各模块 POM**

`tengyei-backend/common/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.tengyei</groupId>
        <artifactId>tengyei-backend</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>common</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

`tengyei-backend/core-auth/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.tengyei</groupId>
        <artifactId>tengyei-backend</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>core-auth</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.tengyei</groupId>
            <artifactId>common</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <runtime>true</runtime>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <runtime>true</runtime>
        </dependency>
    </dependencies>
</project>
```

对 `core-company`, `core-org`, `core-rbac`, `audit`，POM 内容如下（仅依赖 common）：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.tengyei</groupId>
        <artifactId>tengyei-backend</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <!-- artifactId 替换为对应模块名: core-company / core-org / core-rbac / audit -->
    <artifactId>core-company</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.tengyei</groupId>
            <artifactId>common</artifactId>
        </dependency>
    </dependencies>
</project>
```

`tengyei-backend/app/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.tengyei</groupId>
        <artifactId>tengyei-backend</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>app</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.tengyei</groupId>
            <artifactId>common</artifactId>
        </dependency>
        <dependency>
            <groupId>com.tengyei</groupId>
            <artifactId>core-auth</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.tengyei</groupId>
            <artifactId>core-company</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.tengyei</groupId>
            <artifactId>core-org</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.tengyei</groupId>
            <artifactId>core-rbac</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.tengyei</groupId>
            <artifactId>audit</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <version>8.0.33</version>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring-boot.version}</version>
                <executions>
                    <execution>
                        <goals><goal>repackage</goal></goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 4: 在 IntelliJ IDEA 或命令行验证 Maven 结构**

```powershell
cd "E:\OneDrive\manager-software\tengyei-backend"
mvn validate
```

期望输出：`BUILD SUCCESS`（无模块源码时 validate 仍通过）

- [ ] **Step 5: Commit**

```bash
cd "E:/OneDrive/manager-software"
git init
git add tengyei-backend/
git commit -m "feat: scaffold Spring Boot multi-module project structure"
```

---

## Task 2: Spring Boot 主应用 + 配置文件

**Files:**
- Create: `tengyei-backend/app/src/main/java/com/tengyei/TengyeiApplication.java`
- Create: `tengyei-backend/app/src/main/resources/application.yml`
- Create: `tengyei-backend/app/src/main/resources/application-dev.yml`

- [ ] **Step 1: 写主启动类**

`app/src/main/java/com/tengyei/TengyeiApplication.java`:
```java
package com.tengyei;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.tengyei")
@MapperScan("com.tengyei.**.mapper")
@EnableScheduling
public class TengyeiApplication {
    public static void main(String[] args) {
        SpringApplication.run(TengyeiApplication.class, args);
    }
}
```

- [ ] **Step 2: 写主配置文件**

`app/src/main/resources/application.yml`:
```yaml
spring:
  profiles:
    active: dev
  application:
    name: tengyei-backend
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: Asia/Shanghai

server:
  port: 8080
  servlet:
    context-path: /

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      logic-delete-field: isDeleted
      logic-delete-value: 1
      logic-not-delete-value: 0

tengyei:
  jwt:
    secret: "TengYei@2026#SecretKey!MustBe32CharsLong"
    expire-hours: 2
    refresh-before-minutes: 30
```

`app/src/main/resources/application-dev.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/tengyei_dev?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: root
    password: your_local_mysql_password   # 修改为本地 MySQL 密码
    driver-class-name: com.mysql.cj.jdbc.Driver
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
      timeout: 3000ms

logging:
  level:
    com.tengyei: DEBUG
```

- [ ] **Step 3: 在本地 MySQL 创建数据库**

```sql
CREATE DATABASE IF NOT EXISTS tengyei_dev
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

可用 MySQL Workbench 或命令行执行：
```powershell
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS tengyei_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

- [ ] **Step 4: Commit**

```bash
git add tengyei-backend/app/src/
git commit -m "feat: add Spring Boot main app and configuration"
```

---

## Task 3: 数据库 Schema（Flyway V1 迁移）

**Files:**
- Create: `tengyei-backend/app/src/main/resources/db/migration/V1__create_tables.sql`

- [ ] **Step 1: 写建表 SQL**

`app/src/main/resources/db/migration/V1__create_tables.sql`:

```sql
-- =============================================
-- 公司表（租户表）
-- =============================================
CREATE TABLE company (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_no VARCHAR(20) NOT NULL UNIQUE COMMENT '公司编号 C20260625001',
    full_name VARCHAR(100) NOT NULL COMMENT '公司全称',
    short_name VARCHAR(50) NOT NULL COMMENT '公司简称',
    credit_code VARCHAR(18) COMMENT '统一社会信用代码',
    logo_url VARCHAR(255) COMMENT 'Logo图片地址',
    admin_name VARCHAR(20) NOT NULL COMMENT '管理员姓名',
    admin_phone VARCHAR(20) NOT NULL COMMENT '管理员手机号',
    admin_email VARCHAR(100) COMMENT '管理员邮箱',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待激活 1启用 2停用',
    expire_date DATE COMMENT '有效期截止',
    max_users INT COMMENT '最大人员数 NULL=不限',
    max_branches INT COMMENT '最大分公司数 NULL=不限',
    remark VARCHAR(200) COMMENT '备注',
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公司/租户表';

-- =============================================
-- 部门表
-- =============================================
CREATE TABLE dept (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    name VARCHAR(50) NOT NULL COMMENT '部门名称',
    code VARCHAR(30) COMMENT '部门编码',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '上级部门ID 0=一级',
    leader_id BIGINT COMMENT '负责人用户ID',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

-- =============================================
-- 分公司表
-- =============================================
CREATE TABLE branch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    branch_no VARCHAR(20) NOT NULL COMMENT '分公司编码 B001',
    name VARCHAR(50) NOT NULL COMMENT '分公司名称',
    type VARCHAR(20) NOT NULL DEFAULT 'independent' COMMENT 'independent/affiliated',
    province VARCHAR(20) COMMENT '省',
    city VARCHAR(20) COMMENT '市',
    district VARCHAR(20) COMMENT '区',
    address VARCHAR(200) COMMENT '详细地址',
    leader_id BIGINT COMMENT '负责人用户ID',
    phone VARCHAR(20) COMMENT '联系电话',
    max_users INT COMMENT '最大人员配额',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分公司表';

-- =============================================
-- 分公司-部门关联表
-- =============================================
CREATE TABLE branch_dept (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    dept_id BIGINT NOT NULL,
    UNIQUE KEY uk_branch_dept (branch_id, dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分公司-部门关联';

-- =============================================
-- 用户表
-- =============================================
CREATE TABLE user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '0=平台超级管理员',
    user_no VARCHAR(20) NOT NULL COMMENT '工号 E202606001',
    username VARCHAR(50) NOT NULL COMMENT '登录账号',
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt加密',
    real_name VARCHAR(20) NOT NULL COMMENT '真实姓名',
    phone VARCHAR(20) NOT NULL COMMENT '手机号',
    email VARCHAR(100) COMMENT '邮箱',
    avatar_url VARCHAR(255) COMMENT '头像地址',
    dept_id BIGINT COMMENT '所属部门ID',
    branch_id BIGINT COMMENT '所属分公司ID',
    leader_id BIGINT COMMENT '直属上级ID',
    entry_date DATE COMMENT '入职日期',
    is_super_admin TINYINT NOT NULL DEFAULT 0 COMMENT '是否平台超级管理员',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
    pwd_reset_required TINYINT NOT NULL DEFAULT 1 COMMENT '是否需重置密码',
    login_fail_count INT NOT NULL DEFAULT 0,
    locked_until DATETIME COMMENT '账号锁定截止时间',
    last_login_at DATETIME COMMENT '最后登录时间',
    last_login_ip VARCHAR(50) COMMENT '最后登录IP',
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_username (username),
    INDEX idx_tenant (tenant_id),
    INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- =============================================
-- 角色表
-- =============================================
CREATE TABLE role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL COMMENT '角色名称',
    code VARCHAR(50) NOT NULL COMMENT '角色编码',
    description VARCHAR(200) COMMENT '角色描述',
    data_scope VARCHAR(20) NOT NULL DEFAULT 'all' COMMENT 'all/branch/dept/self',
    is_preset TINYINT NOT NULL DEFAULT 0 COMMENT '1=预置不可删除',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id),
    UNIQUE KEY uk_tenant_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- =============================================
-- 权限点表（平台级，无tenant_id）
-- =============================================
CREATE TABLE permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module VARCHAR(50) NOT NULL COMMENT '模块名称',
    code VARCHAR(100) NOT NULL UNIQUE COMMENT '权限编码 如 user:create',
    name VARCHAR(100) NOT NULL COMMENT '权限名称',
    description VARCHAR(200) COMMENT '权限说明',
    sort_order INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限点表';

-- =============================================
-- 角色-权限关联
-- =============================================
CREATE TABLE role_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    UNIQUE KEY uk_role_perm (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限关联';

-- =============================================
-- 用户-角色关联
-- =============================================
CREATE TABLE user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联';

-- =============================================
-- 操作日志表
-- =============================================
CREATE TABLE audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(50) NOT NULL COMMENT '操作人姓名（冗余）',
    module VARCHAR(50) NOT NULL COMMENT '操作模块',
    action_type VARCHAR(30) NOT NULL COMMENT '操作类型',
    description VARCHAR(500) NOT NULL COMMENT '操作描述',
    detail JSON COMMENT '变更前后数据快照',
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    result TINYINT NOT NULL COMMENT '0失败 1成功',
    error_msg VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';

-- =============================================
-- 登录日志表
-- =============================================
CREATE TABLE login_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    username VARCHAR(50) NOT NULL,
    login_type VARCHAR(20) NOT NULL COMMENT 'login/logout',
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    result TINYINT NOT NULL COMMENT '0失败 1成功',
    fail_reason VARCHAR(200),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志';

-- =============================================
-- 系统配置表
-- =============================================
CREATE TABLE system_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '0=平台级配置',
    config_key VARCHAR(100) NOT NULL COMMENT '配置键',
    config_value TEXT COMMENT '配置值',
    description VARCHAR(200),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_key (tenant_id, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置';

-- =============================================
-- 模块注册表（预留）
-- =============================================
CREATE TABLE module_registry (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_code VARCHAR(50) NOT NULL UNIQUE,
    module_name VARCHAR(100) NOT NULL,
    version VARCHAR(20) NOT NULL,
    entry_url VARCHAR(255) NOT NULL,
    menu_config JSON NOT NULL,
    permissions JSON NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='扩展模块注册表';
```

- [ ] **Step 2: Commit**

```bash
git add tengyei-backend/app/src/main/resources/db/migration/V1__create_tables.sql
git commit -m "feat: add Flyway V1 database schema migration"
```

---

## Task 4: 初始化数据（Flyway V2）

**Files:**
- Create: `tengyei-backend/app/src/main/resources/db/migration/V2__init_data.sql`

- [ ] **Step 1: 写初始化数据 SQL**

`db/migration/V2__init_data.sql`:

```sql
-- =============================================
-- 初始化权限点（V1.0 核心权限）
-- =============================================
INSERT INTO permission (module, code, name, sort_order) VALUES
('company',  'company:view',    '查看公司信息',   10),
('company',  'company:edit',    '编辑公司信息',   11),
('dept',     'dept:view',       '查看部门',       20),
('dept',     'dept:create',     '新增部门',       21),
('dept',     'dept:edit',       '编辑部门',       22),
('dept',     'dept:delete',     '删除部门',       23),
('branch',   'branch:view',     '查看分公司',     30),
('branch',   'branch:create',   '新增分公司',     31),
('branch',   'branch:edit',     '编辑分公司',     32),
('branch',   'branch:delete',   '删除分公司',     33),
('user',     'user:view',       '查看人员',       40),
('user',     'user:create',     '新增人员',       41),
('user',     'user:edit',       '编辑人员',       42),
('user',     'user:delete',     '删除人员',       43),
('user',     'user:reset_pwd',  '重置密码',       44),
('role',     'role:view',       '查看角色',       50),
('role',     'role:create',     '创建角色',       51),
('role',     'role:edit',       '编辑角色',       52),
('role',     'role:delete',     '删除角色',       53),
('log',      'log:view',        '查看日志',       60),
('log',      'log:export',      '导出日志',       61),
('setting',  'setting:view',    '查看设置',       70),
('setting',  'setting:edit',    '修改设置',       71);

-- =============================================
-- 初始化平台超级管理员账号
-- 密码: Admin@2026（BCrypt加密值，首次登录必须修改）
-- =============================================
INSERT INTO user (
    tenant_id, user_no, username, password, real_name, phone,
    is_super_admin, status, pwd_reset_required
) VALUES (
    0,
    'SUPER-ADMIN-001',
    'superadmin',
    '$2a$12$Xy5k2yJLJZ8Z4Q7Q1a2r8OTlWcTm1VcT.UJPnvH6XhI9D3GdEJzrS',
    '超级管理员',
    '13800000000',
    1, 1, 0
);
-- 注意：以上BCrypt hash对应明文 Admin@2026
-- 生产环境首次登录后必须修改密码

-- =============================================
-- 平台级系统配置默认值
-- =============================================
INSERT INTO system_config (tenant_id, config_key, config_value, description) VALUES
(0, 'login.max_fail_count',    '5',    '最大登录失败次数'),
(0, 'login.lock_minutes',      '15',   '账号锁定时长（分钟）'),
(0, 'login.single_device',     'false','是否单端登录'),
(0, 'data.log_retention_days', '730',  '日志保留天数（平台级）');
```

- [ ] **Step 2: 验证 Flyway 迁移（先启动主应用一次）**

```powershell
cd "E:\OneDrive\manager-software\tengyei-backend"
mvn -pl app spring-boot:run -Dspring-boot.run.profiles=dev
```

期望输出：`Successfully applied 2 migrations` 以及 `Started TengyeiApplication`  
如报错先检查 MySQL 连接配置。

- [ ] **Step 3: Commit**

```bash
git add tengyei-backend/app/src/main/resources/db/migration/V2__init_data.sql
git commit -m "feat: add initial permissions, super admin and system config data"
```

---

## Task 5: Common 模块 — 基础类

**Files:**
- Create: `common/src/main/java/com/tengyei/common/entity/BaseEntity.java`
- Create: `common/src/main/java/com/tengyei/common/context/TenantContext.java`
- Create: `common/src/main/java/com/tengyei/common/response/Result.java`
- Create: `common/src/main/java/com/tengyei/common/exception/BusinessException.java`
- Create: `common/src/main/java/com/tengyei/common/exception/GlobalExceptionHandler.java`
- Create: `common/src/main/java/com/tengyei/common/config/MybatisPlusConfig.java`
- Create: `common/src/main/java/com/tengyei/common/config/RedisConfig.java`

- [ ] **Step 1: 写测试（先验证 Result 构建）**

`common/src/test/java/com/tengyei/common/ResultTest.java`:
```java
package com.tengyei.common;

import com.tengyei.common.response.Result;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ResultTest {

    @Test
    void success_returns_code_zero() {
        Result<String> result = Result.ok("hello");
        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isEqualTo("hello");
        assertThat(result.getMsg()).isEqualTo("success");
    }

    @Test
    void fail_returns_error_code_and_message() {
        Result<Void> result = Result.fail(403, "无权限");
        assertThat(result.getCode()).isEqualTo(403);
        assertThat(result.getMsg()).isEqualTo("无权限");
        assertThat(result.getData()).isNull();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
cd "E:\OneDrive\manager-software\tengyei-backend"
mvn -pl common test
```

期望：FAIL，`Result` 类未找到

- [ ] **Step 3: 写 BaseEntity**

`common/src/main/java/com/tengyei/common/entity/BaseEntity.java`:
```java
package com.tengyei.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public abstract class BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer isDeleted;
}
```

- [ ] **Step 4: 写 TenantContext**

`common/src/main/java/com/tengyei/common/context/TenantContext.java`:
```java
package com.tengyei.common.context;

public class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> BRANCH_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> DATA_SCOPE = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) { TENANT_ID.set(tenantId); }
    public static Long getTenantId() { return TENANT_ID.get(); }

    public static void setUserId(Long userId) { USER_ID.set(userId); }
    public static Long getUserId() { return USER_ID.get(); }

    public static void setBranchId(Long branchId) { BRANCH_ID.set(branchId); }
    public static Long getBranchId() { return BRANCH_ID.get(); }

    public static void setDataScope(String scope) { DATA_SCOPE.set(scope); }
    public static String getDataScope() { return DATA_SCOPE.get(); }

    public static boolean isSuperAdmin() { return Long.valueOf(0L).equals(TENANT_ID.get()); }

    public static void clear() {
        TENANT_ID.remove();
        USER_ID.remove();
        BRANCH_ID.remove();
        DATA_SCOPE.remove();
    }
}
```

- [ ] **Step 5: 写 Result**

`common/src/main/java/com/tengyei/common/response/Result.java`:
```java
package com.tengyei.common.response;

import lombok.Data;

@Data
public class Result<T> {

    private int code;
    private String msg;
    private T data;

    private Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "success", data);
    }

    public static <T> Result<T> ok() {
        return new Result<>(0, "success", null);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public static <T> Result<T> fail(String msg) {
        return new Result<>(500, msg, null);
    }
}
```

- [ ] **Step 6: 写 BusinessException**

`common/src/main/java/com/tengyei/common/exception/BusinessException.java`:
```java
package com.tengyei.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(500, message);
    }
}
```

- [ ] **Step 7: 写全局异常处理器**

`common/src/main/java/com/tengyei/common/exception/GlobalExceptionHandler.java`:
```java
package com.tengyei.common.exception;

import com.tengyei.common.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("Business exception: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Result<Void> handleValidation(Exception e) {
        String msg = e instanceof MethodArgumentNotValidException ex
                ? ex.getBindingResult().getFieldErrors().stream()
                    .map(f -> f.getField() + ": " + f.getDefaultMessage())
                    .findFirst().orElse("参数校验失败")
                : "参数校验失败";
        return Result.fail(422, msg);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleGeneral(Exception e) {
        log.error("Unexpected error", e);
        return Result.fail(500, "服务器内部错误");
    }
}
```

- [ ] **Step 8: 写 MyBatisPlus 配置（多租户拦截器）**

`common/src/main/java/com/tengyei/common/config/MybatisPlusConfig.java`:
```java
package com.tengyei.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
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

    // 不需要租户过滤的表（平台级表 或 关联表）
    private static final Set<String> IGNORE_TABLES = new HashSet<>(Arrays.asList(
        "company", "permission", "role_permission", "user_role",
        "module_registry", "login_log"
    ));

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor() {
            @Override
            public Expression getTenantId() {
                Long tenantId = TenantContext.getTenantId();
                return new LongValue(tenantId != null ? tenantId : 0L);
            }

            @Override
            public boolean ignoreTable(String tableName) {
                // 超级管理员或表在忽略列表中，跳过租户过滤
                return TenantContext.isSuperAdmin() || IGNORE_TABLES.contains(tableName);
            }
        });
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

- [ ] **Step 9: 写 Redis 配置**

`common/src/main/java/com/tengyei/common/config/RedisConfig.java`:
```java
package com.tengyei.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}
```

- [ ] **Step 10: 运行测试确认通过**

```powershell
cd "E:\OneDrive\manager-software\tengyei-backend"
mvn -pl common test
```

期望：`Tests run: 2, Failures: 0`

- [ ] **Step 11: Commit**

```bash
git add tengyei-backend/common/
git commit -m "feat: add common module with tenant context, result, exception handler, MyBatisPlus config"
```

---

## Task 6: core-auth — JWT 工具类

**Files:**
- Create: `core-auth/src/main/java/com/tengyei/auth/dto/LoginRequest.java`
- Create: `core-auth/src/main/java/com/tengyei/auth/dto/LoginResponse.java`
- Create: `core-auth/src/main/java/com/tengyei/auth/dto/UserInfoVO.java`
- Create: `core-auth/src/main/java/com/tengyei/auth/service/JwtService.java`
- Test: `core-auth/src/test/java/com/tengyei/auth/JwtServiceTest.java`

- [ ] **Step 1: 写 JWT 测试**

`core-auth/src/test/java/com/tengyei/auth/JwtServiceTest.java`:
```java
package com.tengyei.auth;

import com.tengyei.auth.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
            "TestSecretKey@2026#MustBe32CharsLongXX",
            2,    // expireHours
            30    // refreshBeforeMinutes
        );
    }

    @Test
    void generate_and_parse_token() {
        String token = jwtService.generate(10001L, 5001L, 201L,
                List.of("branch_admin"), List.of("user:view"), "branch");

        assertThat(jwtService.isValid(token)).isTrue();
        assertThat(jwtService.getTenantId(token)).isEqualTo(10001L);
        assertThat(jwtService.getUserId(token)).isEqualTo(5001L);
        assertThat(jwtService.getBranchId(token)).isEqualTo(201L);
        assertThat(jwtService.getDataScope(token)).isEqualTo("branch");
    }

    @Test
    void invalid_token_returns_false() {
        assertThat(jwtService.isValid("invalid.token.here")).isFalse();
        assertThat(jwtService.isValid(null)).isFalse();
        assertThat(jwtService.isValid("")).isFalse();
    }

    @Test
    void super_admin_token_has_tenant_zero() {
        String token = jwtService.generate(0L, 1L, null,
                List.of("super_admin"), List.of(), "all");
        assertThat(jwtService.getTenantId(token)).isEqualTo(0L);
        assertThat(jwtService.getBranchId(token)).isNull();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
mvn -pl core-auth test -Dtest=JwtServiceTest
```

期望：FAIL，`JwtService` 未找到

- [ ] **Step 3: 写 DTO 类**

`core-auth/src/main/java/com/tengyei/auth/dto/LoginRequest.java`:
```java
package com.tengyei.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    private String password;
}
```

`core-auth/src/main/java/com/tengyei/auth/dto/LoginResponse.java`:
```java
package com.tengyei.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String accessToken;
    private Long expiresIn;        // 秒
    private Boolean pwdResetRequired;
    private String realName;
    private Long tenantId;
}
```

`core-auth/src/main/java/com/tengyei/auth/dto/UserInfoVO.java`:
```java
package com.tengyei.auth.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class UserInfoVO {
    private Long userId;
    private Long tenantId;
    private Long branchId;
    private String username;
    private String realName;
    private String avatarUrl;
    private Boolean isSuperAdmin;
    private String dataScope;
    private List<String> roleCodes;
    private List<String> permissions;
    private List<RouteVO> routes;  // 动态路由

    @Data
    @Builder
    public static class RouteVO {
        private String path;
        private String name;
        private List<RouteVO> children;
    }
}
```

- [ ] **Step 4: 写 JwtService**

`core-auth/src/main/java/com/tengyei/auth/service/JwtService.java`:
```java
package com.tengyei.auth.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expireHours;
    private final long refreshBeforeMinutes;

    public JwtService(
            @Value("${tengyei.jwt.secret}") String secret,
            @Value("${tengyei.jwt.expire-hours}") long expireHours,
            @Value("${tengyei.jwt.refresh-before-minutes}") long refreshBeforeMinutes) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireHours = expireHours;
        this.refreshBeforeMinutes = refreshBeforeMinutes;
    }

    public String generate(Long tenantId, Long userId, Long branchId,
                           List<String> roleCodes, List<String> permissions, String dataScope) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claim("tenant_id", tenantId)
                .claim("user_id", userId)
                .claim("branch_id", branchId)
                .claim("role_codes", roleCodes)
                .claim("permissions", permissions)
                .claim("data_scope", dataScope)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireHours * 3600)))
                .signWith(secretKey)
                .compact();
    }

    public boolean isValid(String token) {
        try {
            if (token == null || token.isBlank()) return false;
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean shouldRefresh(String token) {
        try {
            Date expiration = getClaims(token).getExpiration();
            return expiration.toInstant().isBefore(
                    Instant.now().plusSeconds(refreshBeforeMinutes * 60));
        } catch (Exception e) {
            return false;
        }
    }

    public Long getTenantId(String token) {
        return ((Number) getClaims(token).get("tenant_id")).longValue();
    }

    public Long getUserId(String token) {
        return ((Number) getClaims(token).get("user_id")).longValue();
    }

    public Long getBranchId(String token) {
        Object val = getClaims(token).get("branch_id");
        return val == null ? null : ((Number) val).longValue();
    }

    public String getDataScope(String token) {
        return (String) getClaims(token).get("data_scope");
    }

    @SuppressWarnings("unchecked")
    public List<String> getPermissions(String token) {
        return (List<String>) getClaims(token).get("permissions");
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

```powershell
mvn -pl core-auth test -Dtest=JwtServiceTest
```

期望：`Tests run: 3, Failures: 0`

- [ ] **Step 6: Commit**

```bash
git add tengyei-backend/core-auth/
git commit -m "feat: add JWT service with tenant claims and validation"
```

---

## Task 7: core-auth — 登录/登出/刷新接口

**Files:**
- Create: `core-auth/src/main/java/com/tengyei/auth/service/TokenBlacklistService.java`
- Create: `core-auth/src/main/java/com/tengyei/auth/service/AuthService.java`
- Create: `core-auth/src/main/java/com/tengyei/auth/filter/JwtAuthFilter.java`
- Create: `core-auth/src/main/java/com/tengyei/auth/config/SecurityConfig.java`
- Create: `core-auth/src/main/java/com/tengyei/auth/controller/AuthController.java`
- Test: `core-auth/src/test/java/com/tengyei/auth/AuthControllerTest.java`

- [ ] **Step 1: 写 AuthController 集成测试**

`core-auth/src/test/java/com/tengyei/auth/AuthControllerTest.java`:
```java
package com.tengyei.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tengyei.auth.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void login_with_valid_credentials_returns_token() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("superadmin");
        req.setPassword("Admin@2026");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void login_with_wrong_password_returns_error() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("superadmin");
        req.setPassword("wrongpassword");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void login_with_empty_username_returns_422() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("");
        req.setPassword("Admin@2026");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }
}
```

- [ ] **Step 2: 写 TokenBlacklistService**

`core-auth/src/main/java/com/tengyei/auth/service/TokenBlacklistService.java`:
```java
package com.tengyei.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String PREFIX = "token:blacklist:";

    private final RedisTemplate<String, Object> redisTemplate;

    public void blacklist(String token, long ttlSeconds) {
        if (ttlSeconds > 0) {
            redisTemplate.opsForValue().set(PREFIX + token, "1", Duration.ofSeconds(ttlSeconds));
        }
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + token));
    }
}
```

- [ ] **Step 3: 写 AuthService**

`core-auth/src/main/java/com/tengyei/auth/service/AuthService.java`:
```java
package com.tengyei.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tengyei.auth.dto.LoginRequest;
import com.tengyei.auth.dto.LoginResponse;
import com.tengyei.auth.dto.UserInfoVO;
import com.tengyei.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtService jwtService;
    private final TokenBlacklistService blacklistService;
    private final PasswordEncoder passwordEncoder;
    // UserMapper 和 UserRoleMapper 将在 core-auth 的测试配置中 mock，
    // 在 app 模块运行时通过 @MapperScan 注入
    // 为保持模块独立，这里通过接口注入（Plan 4 RBAC 完成后完整实现）
    // Task 7 先实现超级管理员登录逻辑（硬编码查询超级管理员）

    // 注意：此处直接使用 JDBC 模板查询以避免循环依赖，
    // Plan 4 完成后替换为 UserService
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private static final int MAX_FAIL_COUNT = 5;
    private static final int LOCK_MINUTES = 15;

    public LoginResponse login(LoginRequest req, String clientIp) {
        // 1. 查询用户（跳过租户过滤，username 全局唯一）
        var rows = jdbcTemplate.queryForList(
            "SELECT id, tenant_id, username, password, real_name, phone, " +
            "is_super_admin, status, pwd_reset_required, " +
            "login_fail_count, locked_until, branch_id " +
            "FROM user WHERE username = ? AND is_deleted = 0",
            req.getUsername()
        );

        if (rows.isEmpty()) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        var row = rows.get(0);
        Long userId = ((Number) row.get("id")).longValue();
        Long tenantId = ((Number) row.get("tenant_id")).longValue();
        String encodedPwd = (String) row.get("password");
        int status = ((Number) row.get("status")).intValue();
        int failCount = ((Number) row.get("login_fail_count")).intValue();
        Object lockedUntilObj = row.get("locked_until");
        boolean pwdResetRequired = ((Number) row.get("pwd_reset_required")).intValue() == 1;

        // 2. 检查锁定状态
        if (lockedUntilObj != null) {
            LocalDateTime lockedUntil = lockedUntilObj instanceof LocalDateTime ldt ? ldt
                    : LocalDateTime.parse(lockedUntilObj.toString().replace(" ", "T"));
            if (LocalDateTime.now().isBefore(lockedUntil)) {
                throw new BusinessException(423, "账号已锁定，请 " + LOCK_MINUTES + " 分钟后重试");
            }
        }

        // 3. 检查用户状态
        if (status == 0) {
            throw new BusinessException(423, "账号已停用，请联系管理员");
        }

        // 4. 验证密码
        if (!passwordEncoder.matches(req.getPassword(), encodedPwd)) {
            int newFailCount = failCount + 1;
            if (newFailCount >= MAX_FAIL_COUNT) {
                jdbcTemplate.update(
                    "UPDATE user SET login_fail_count = ?, locked_until = ? WHERE id = ?",
                    newFailCount,
                    LocalDateTime.now().plusMinutes(LOCK_MINUTES),
                    userId
                );
                throw new BusinessException(423, "密码错误次数过多，账号已锁定 " + LOCK_MINUTES + " 分钟");
            }
            jdbcTemplate.update("UPDATE user SET login_fail_count = ? WHERE id = ?",
                    newFailCount, userId);
            throw new BusinessException(401, "用户名或密码错误，还可尝试 " + (MAX_FAIL_COUNT - newFailCount) + " 次");
        }

        // 5. 检查公司状态（非超级管理员）
        boolean isSuperAdmin = ((Number) row.get("is_super_admin")).intValue() == 1;
        if (!isSuperAdmin) {
            var companyRows = jdbcTemplate.queryForList(
                "SELECT status, expire_date FROM company WHERE id = ? AND is_deleted = 0",
                tenantId
            );
            if (companyRows.isEmpty() || ((Number) companyRows.get(0).get("status")).intValue() != 1) {
                throw new BusinessException(423, "所属企业已停用，请联系平台管理员");
            }
        }

        // 6. 登录成功，重置失败次数，更新最后登录时间
        jdbcTemplate.update(
            "UPDATE user SET login_fail_count = 0, locked_until = NULL, " +
            "last_login_at = NOW(), last_login_ip = ? WHERE id = ?",
            clientIp, userId
        );

        // 7. 查询用户角色和权限（超级管理员拥有全部权限）
        List<String> roleCodes;
        List<String> permissions;
        String dataScope;
        Long branchId = row.get("branch_id") != null
                ? ((Number) row.get("branch_id")).longValue() : null;

        if (isSuperAdmin) {
            roleCodes = List.of("super_admin");
            permissions = List.of("*");
            dataScope = "all";
        } else {
            roleCodes = jdbcTemplate.queryForList(
                "SELECT r.code FROM role r " +
                "JOIN user_role ur ON ur.role_id = r.id " +
                "WHERE ur.user_id = ? AND r.status = 1 AND r.is_deleted = 0",
                String.class, userId
            );
            permissions = jdbcTemplate.queryForList(
                "SELECT DISTINCT p.code FROM permission p " +
                "JOIN role_permission rp ON rp.permission_id = p.id " +
                "JOIN user_role ur ON ur.role_id = rp.role_id " +
                "WHERE ur.user_id = ? AND p.status = 1",
                String.class, userId
            );
            dataScope = jdbcTemplate.queryForObject(
                "SELECT MIN(r.data_scope) FROM role r " +
                "JOIN user_role ur ON ur.role_id = r.id " +
                "WHERE ur.user_id = ? AND r.status = 1",
                String.class, userId
            );
            if (dataScope == null) dataScope = "self";
        }

        // 8. 生成 JWT
        String token = jwtService.generate(tenantId, userId, branchId,
                roleCodes, permissions, dataScope);

        return LoginResponse.builder()
                .accessToken(token)
                .expiresIn(7200L)
                .pwdResetRequired(pwdResetRequired)
                .realName((String) row.get("real_name"))
                .tenantId(tenantId)
                .build();
    }

    public void logout(String token) {
        if (jwtService.isValid(token)) {
            // 将 token 加入黑名单，TTL 设为 2 小时（token 最大有效期）
            blacklistService.blacklist(token, 7200L);
        }
    }

    public String refresh(String token) {
        if (!jwtService.isValid(token) || blacklistService.isBlacklisted(token)) {
            throw new BusinessException(401, "Token 无效或已过期");
        }
        if (!jwtService.shouldRefresh(token)) {
            return token; // 还没到刷新窗口
        }
        // 旧 token 加入黑名单，生成新 token
        blacklistService.blacklist(token, 7200L);
        Long tenantId = jwtService.getTenantId(token);
        Long userId = jwtService.getUserId(token);
        Long branchId = jwtService.getBranchId(token);
        List<String> permissions = jwtService.getPermissions(token);
        // 重新查询角色（防止权限变更）
        List<String> roleCodes = jdbcTemplate.queryForList(
            "SELECT r.code FROM role r JOIN user_role ur ON ur.role_id = r.id " +
            "WHERE ur.user_id = ? AND r.status = 1",
            String.class, userId
        );
        String dataScope = jwtService.getDataScope(token);
        return jwtService.generate(tenantId, userId, branchId, roleCodes, permissions, dataScope);
    }
}
```

- [ ] **Step 4: 写 JwtAuthFilter**

`core-auth/src/main/java/com/tengyei/auth/filter/JwtAuthFilter.java`:
```java
package com.tengyei.auth.filter;

import com.tengyei.auth.service.JwtService;
import com.tengyei.auth.service.TokenBlacklistService;
import com.tengyei.common.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenBlacklistService blacklistService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (token != null && jwtService.isValid(token) && !blacklistService.isBlacklisted(token)) {
                Long tenantId = jwtService.getTenantId(token);
                Long userId = jwtService.getUserId(token);
                Long branchId = jwtService.getBranchId(token);
                String dataScope = jwtService.getDataScope(token);
                List<String> permissions = jwtService.getPermissions(token);

                TenantContext.setTenantId(tenantId);
                TenantContext.setUserId(userId);
                TenantContext.setBranchId(branchId);
                TenantContext.setDataScope(dataScope);

                List<SimpleGrantedAuthority> authorities = permissions.stream()
                        .map(p -> new SimpleGrantedAuthority("PERM_" + p))
                        .collect(Collectors.toList());

                var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (Exception ignored) {
            // 非法 token 直接忽略，后续接口自行返回 401
        }
        chain.doFilter(request, response);
        TenantContext.clear(); // 请求结束清理 ThreadLocal
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
```

- [ ] **Step 5: 写 SecurityConfig**

`core-auth/src/main/java/com/tengyei/auth/config/SecurityConfig.java`:
```java
package com.tengyei.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tengyei.auth.filter.JwtAuthFilter;
import com.tengyei.common.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setContentType("application/json;charset=UTF-8");
                    res.setStatus(200);
                    res.getWriter().write(objectMapper.writeValueAsString(
                            Result.fail(401, "未登录或Token已过期")));
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setContentType("application/json;charset=UTF-8");
                    res.setStatus(200);
                    res.getWriter().write(objectMapper.writeValueAsString(
                            Result.fail(403, "无权限访问")));
                })
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
```

- [ ] **Step 6: 写 AuthController**

`core-auth/src/main/java/com/tengyei/auth/controller/AuthController.java`:
```java
package com.tengyei.auth.controller;

import com.tengyei.auth.dto.LoginRequest;
import com.tengyei.auth.dto.LoginResponse;
import com.tengyei.auth.service.AuthService;
import com.tengyei.common.response.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                       HttpServletRequest request) {
        String ip = getClientIp(request);
        return Result.ok(authService.login(req, ip));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        authService.logout(token);
        return Result.ok();
    }

    @PostMapping("/refresh")
    public Result<String> refresh(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        return Result.ok(authService.refresh(token));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return ip.split(",")[0].trim();
    }
}
```

- [ ] **Step 7: 创建 test profile 配置（使用 H2 内存数据库）**

`app/src/test/resources/application-test.yml`:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  flyway:
    enabled: true
    locations: classpath:db/migration
  data:
    redis:
      host: localhost
      port: 6379

tengyei:
  jwt:
    secret: "TestSecretKey@2026#MustBe32CharsLongXX"
    expire-hours: 2
    refresh-before-minutes: 30
```

在 `app/pom.xml` 中添加 H2 测试依赖：
```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 8: 运行测试**

```powershell
mvn -pl app test -Dtest=AuthControllerTest
```

期望：`Tests run: 3, Failures: 0`

- [ ] **Step 9: 完整启动验证**

```powershell
mvn -pl app spring-boot:run -Dspring-boot.run.profiles=dev
```

用 curl 或 Postman 测试：
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" `
  -Method POST `
  -ContentType "application/json" `
  -Body '{"username":"superadmin","password":"Admin@2026"}'
```

期望响应：`{"code":0,"msg":"success","data":{"accessToken":"eyJ...","expiresIn":7200,...}}`

- [ ] **Step 10: Commit**

```bash
git add tengyei-backend/core-auth/ tengyei-backend/app/
git commit -m "feat: implement JWT auth with login, logout, refresh and tenant context injection"
```

---

## Task 8: Vue 3 前端项目初始化

**Files:**
- Create: `tengyei-frontend/` (整个前端项目)

- [ ] **Step 1: 用 Vite 脚手架创建项目**

```powershell
cd "E:\OneDrive\manager-software"
npm create vite@latest tengyei-frontend -- --template vue-ts
cd tengyei-frontend
npm install
npm install element-plus @element-plus/icons-vue
npm install pinia vue-router axios
npm install -D @types/node unplugin-auto-import unplugin-vue-components
```

- [ ] **Step 2: 配置 vite.config.ts**

`tengyei-frontend/vite.config.ts`:
```typescript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { resolve } from 'path'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
      imports: ['vue', 'vue-router', 'pinia'],
      dts: 'src/auto-imports.d.ts',
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts',
    }),
  ],
  resolve: {
    alias: { '@': resolve(__dirname, 'src') }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      }
    }
  }
})
```

- [ ] **Step 3: 写全局 CSS 变量（颜色系统）**

`tengyei-frontend/src/styles/variables.css`:
```css
:root {
  /* 主色系 */
  --color-primary: #1d4ed8;
  --color-primary-light: #3b82f6;
  --color-primary-dark: #1e40af;

  /* 语义色 */
  --color-success: #10b981;
  --color-warning: #f59e0b;
  --color-danger: #ef4444;
  --color-info: #64748b;

  /* 中性色 */
  --color-bg-page: #f8fafc;
  --color-bg-card: #ffffff;
  --color-border: #e2e8f0;
  --color-border-light: #f1f5f9;

  /* 文字 */
  --color-text-primary: #0f172a;
  --color-text-secondary: #64748b;
  --color-text-placeholder: #94a3b8;
  --color-text-disabled: #cbd5e1;

  /* 侧边栏 */
  --sidebar-bg: #0f172a;
  --sidebar-active-bg: #1d4ed8;
  --sidebar-text: #94a3b8;
  --sidebar-active-text: #ffffff;
  --sidebar-width: 220px;
  --sidebar-collapsed-width: 64px;

  /* 尺寸规范 */
  --radius-sm: 4px;
  --radius-md: 8px;
  --radius-lg: 12px;
  --header-height: 56px;
}
```

`tengyei-frontend/src/styles/element-override.css`:
```css
/* Element Plus 主题覆盖 */
:root {
  --el-color-primary: #1d4ed8;
  --el-color-primary-light-3: #3b82f6;
  --el-color-primary-light-5: #60a5fa;
  --el-color-primary-light-7: #93c5fd;
  --el-color-primary-light-8: #bfdbfe;
  --el-color-primary-light-9: #dbeafe;
  --el-color-primary-dark-2: #1e40af;
  --el-border-radius-base: 6px;
  --el-border-radius-small: 4px;
}

/* 表格 */
.el-table th.el-table__cell {
  background-color: var(--color-border-light) !important;
  color: var(--color-text-secondary);
  font-weight: 600;
  font-size: 13px;
}

/* 卡片 */
.el-card {
  border-color: var(--color-border);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06) !important;
}

/* 按钮主色 */
.el-button--primary {
  background-color: var(--color-primary);
  border-color: var(--color-primary);
}
```

- [ ] **Step 4: 写 main.ts**

`tengyei-frontend/src/main.ts`:
```typescript
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import './styles/variables.css'
import './styles/element-override.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)

// 注册所有 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.mount('#app')
```

- [ ] **Step 5: Commit**

```bash
cd "E:/OneDrive/manager-software"
git add tengyei-frontend/
git commit -m "feat: scaffold Vue 3 frontend with Element Plus, Pinia, routing and design token CSS"
```

---

## Task 9: 前端 — Axios 封装 + Auth Store

**Files:**
- Create: `tengyei-frontend/src/utils/request.ts`
- Create: `tengyei-frontend/src/api/auth.ts`
- Create: `tengyei-frontend/src/stores/auth.ts`

- [ ] **Step 1: 写 Axios 封装**

`tengyei-frontend/src/utils/request.ts`:
```typescript
import axios, { AxiosInstance, AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

const request: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
})

// 请求拦截：自动注入 Token
request.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('access_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：统一处理错误码
request.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data.code === 0) return data
    if (data.code === 401) {
      localStorage.removeItem('access_token')
      window.location.href = '/login'
      return Promise.reject(new Error(data.msg))
    }
    ElMessage.error(data.msg || '请求失败')
    return Promise.reject(new Error(data.msg))
  },
  (error) => {
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default request
```

- [ ] **Step 2: 写 Auth API**

`tengyei-frontend/src/api/auth.ts`:
```typescript
import request from '@/utils/request'

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  expiresIn: number
  pwdResetRequired: boolean
  realName: string
  tenantId: number
}

export interface RouteVO {
  path: string
  name: string
  children?: RouteVO[]
}

export interface UserInfoVO {
  userId: number
  tenantId: number
  branchId?: number
  username: string
  realName: string
  avatarUrl?: string
  isSuperAdmin: boolean
  dataScope: string
  roleCodes: string[]
  permissions: string[]
  routes: RouteVO[]
}

export const authApi = {
  login: (data: LoginRequest) =>
    request.post<any, { data: LoginResponse }>('/auth/login', data),

  logout: () => request.post('/auth/logout'),

  refresh: () => request.post<any, { data: string }>('/auth/refresh'),

  getUserInfo: () =>
    request.get<any, { data: UserInfoVO }>('/auth/userinfo'),
}
```

- [ ] **Step 3: 写 Auth Store（Pinia）**

`tengyei-frontend/src/stores/auth.ts`:
```typescript
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, UserInfoVO } from '@/api/auth'
import router from '@/router'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('access_token') || '')
  const userInfo = ref<UserInfoVO | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const isSuperAdmin = computed(() => userInfo.value?.isSuperAdmin ?? false)
  const permissions = computed(() => userInfo.value?.permissions ?? [])

  function hasPermission(perm: string): boolean {
    if (isSuperAdmin.value) return true
    return permissions.value.includes(perm) || permissions.value.includes('*')
  }

  async function login(username: string, password: string) {
    const res = await authApi.login({ username, password })
    token.value = res.data.accessToken
    localStorage.setItem('access_token', res.data.accessToken)
    return res.data
  }

  async function fetchUserInfo() {
    const res = await authApi.getUserInfo()
    userInfo.value = res.data
    return res.data
  }

  async function logout() {
    try { await authApi.logout() } catch {}
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('access_token')
    router.push('/login')
  }

  return { token, userInfo, isLoggedIn, isSuperAdmin, permissions, hasPermission, login, fetchUserInfo, logout }
})
```

- [ ] **Step 4: Commit**

```bash
git add tengyei-frontend/src/utils/ tengyei-frontend/src/api/ tengyei-frontend/src/stores/
git commit -m "feat: add axios request interceptor, auth API and Pinia auth store"
```

---

## Task 10: 前端 — 路由配置 + 登录页

**Files:**
- Create: `tengyei-frontend/src/router/index.ts`
- Create: `tengyei-frontend/src/views/login/LoginView.vue`
- Create: `tengyei-frontend/src/views/error/403View.vue`
- Create: `tengyei-frontend/src/App.vue`

- [ ] **Step 1: 写路由配置（含导航守卫）**

`tengyei-frontend/src/router/index.ts`:
```typescript
import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

// 静态路由（不需要权限）
const staticRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/403',
    name: '403',
    component: () => import('@/views/error/403View.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    redirect: '/login'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes: staticRoutes,
})

// 路由守卫
router.beforeEach(async (to) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth === false) return true

  if (!authStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 已登录但未加载用户信息，先加载
  if (!authStore.userInfo) {
    try {
      await authStore.fetchUserInfo()
    } catch {
      return { path: '/login' }
    }
  }

  return true
})

export default router
```

- [ ] **Step 2: 写登录页**

`tengyei-frontend/src/views/login/LoginView.vue`:
```vue
<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <div class="logo">腾</div>
        <h1 class="title">腾飞企业管理系统</h1>
        <p class="subtitle">Multi-Tenant Enterprise Management</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            size="large"
            :prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-btn"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, FormInstance, FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await authStore.login(form.username, form.password)
    if (res.pwdResetRequired) {
      ElMessage.warning('首次登录，请修改密码')
      // TODO Plan 4: 跳转修改密码页
      return
    }
    // 加载用户信息和路由
    await authStore.fetchUserInfo()
    const redirect = (route.query.redirect as string) || getDefaultRoute()
    router.push(redirect)
  } catch (e) {
    // 错误由 request.ts 拦截器统一处理
  } finally {
    loading.value = false
  }
}

function getDefaultRoute(): string {
  if (authStore.isSuperAdmin) return '/admin/dashboard'
  return '/company/dashboard'
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #0f172a 100%);
}

.login-card {
  width: 420px;
  background: #ffffff;
  border-radius: 16px;
  padding: 48px 40px;
  box-shadow: 0 25px 60px rgba(0, 0, 0, 0.4);
}

.login-header {
  text-align: center;
  margin-bottom: 36px;
}

.logo {
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #1d4ed8, #3b82f6);
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 700;
  color: #ffffff;
  margin: 0 auto 16px;
}

.title {
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 6px;
}

.subtitle {
  font-size: 13px;
  color: #64748b;
  margin: 0;
  letter-spacing: 0.5px;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  letter-spacing: 2px;
  background: linear-gradient(135deg, #1d4ed8, #3b82f6);
  border: none;
}

.login-btn:hover {
  background: linear-gradient(135deg, #1e40af, #2563eb);
}
</style>
```

- [ ] **Step 3: 写 403 页面**

`tengyei-frontend/src/views/error/403View.vue`:
```vue
<template>
  <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:100vh;gap:16px;">
    <div style="font-size:80px;color:#e2e8f0;">🚫</div>
    <h2 style="color:#0f172a;margin:0;">无权限访问</h2>
    <p style="color:#64748b;margin:0;">您没有访问此页面的权限，请联系管理员</p>
    <el-button type="primary" @click="router.go(-1)">返回上一页</el-button>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()
</script>
```

- [ ] **Step 4: 写 App.vue**

`tengyei-frontend/src/App.vue`:
```vue
<template>
  <router-view />
</template>

<script setup lang="ts">
</script>

<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body {
  font-family: 'Inter', 'PingFang SC', -apple-system, BlinkMacSystemFont, sans-serif;
  background: var(--color-bg-page);
  color: var(--color-text-primary);
  -webkit-font-smoothing: antialiased;
}
</style>
```

- [ ] **Step 5: 本地联调验证**

确保后端在 8080 端口运行后：

```powershell
cd "E:\OneDrive\manager-software\tengyei-frontend"
npm run dev
```

浏览器打开 `http://localhost:5173`，应自动跳转到 `/login`，输入 `superadmin` / `Admin@2026` 登录成功并收到 Token。

- [ ] **Step 6: Commit**

```bash
cd "E:/OneDrive/manager-software"
git add tengyei-frontend/src/router/ tengyei-frontend/src/views/ tengyei-frontend/src/App.vue
git commit -m "feat: add login page with dark theme, routing guard and 403 error page"
```

---

## Task 11: UserInfo 接口（后端）

**Files:**
- Create: `core-auth/src/main/java/com/tengyei/auth/controller/UserInfoController.java`

- [ ] **Step 1: 写测试**

在 `AuthControllerTest.java` 中追加：

```java
@Test
void userinfo_without_token_returns_401() throws Exception {
    mockMvc.perform(get("/api/v1/auth/userinfo"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(401));
}

@Test
void userinfo_with_valid_token_returns_user_data() throws Exception {
    // 先登录取 token
    LoginRequest req = new LoginRequest();
    req.setUsername("superadmin");
    req.setPassword("Admin@2026");
    String resp = mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
            .andReturn().getResponse().getContentAsString();

    String token = objectMapper.readTree(resp).path("data").path("accessToken").asText();

    mockMvc.perform(get("/api/v1/auth/userinfo")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.username").value("superadmin"))
            .andExpect(jsonPath("$.data.isSuperAdmin").value(true));
}
```

- [ ] **Step 2: 实现 UserInfoController**

`core-auth/src/main/java/com/tengyei/auth/controller/UserInfoController.java`:
```java
package com.tengyei.auth.controller;

import com.tengyei.auth.dto.UserInfoVO;
import com.tengyei.auth.service.JwtService;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.exception.BusinessException;
import com.tengyei.common.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserInfoController {

    private final JdbcTemplate jdbcTemplate;
    private final JwtService jwtService;

    @GetMapping("/userinfo")
    public Result<UserInfoVO> userinfo(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        Long userId = TenantContext.getUserId();
        Long tenantId = TenantContext.getTenantId();
        Long branchId = TenantContext.getBranchId();
        String dataScope = TenantContext.getDataScope();

        var rows = jdbcTemplate.queryForList(
            "SELECT username, real_name, avatar_url, is_super_admin FROM user WHERE id = ?", userId
        );
        if (rows.isEmpty()) throw new BusinessException(401, "用户不存在");
        var row = rows.get(0);

        boolean isSuperAdmin = ((Number) row.get("is_super_admin")).intValue() == 1;
        List<String> permissions = jwtService.getPermissions(token);

        List<String> roleCodes = jdbcTemplate.queryForList(
            "SELECT r.code FROM role r JOIN user_role ur ON ur.role_id = r.id WHERE ur.user_id = ?",
            String.class, userId
        );

        // 超级管理员路由
        List<UserInfoVO.RouteVO> routes = isSuperAdmin
            ? buildSuperAdminRoutes()
            : buildCompanyRoutes(permissions);

        return Result.ok(UserInfoVO.builder()
                .userId(userId)
                .tenantId(tenantId)
                .branchId(branchId)
                .username((String) row.get("username"))
                .realName((String) row.get("real_name"))
                .avatarUrl((String) row.get("avatar_url"))
                .isSuperAdmin(isSuperAdmin)
                .dataScope(dataScope != null ? dataScope : "all")
                .roleCodes(roleCodes)
                .permissions(permissions)
                .routes(routes)
                .build());
    }

    private List<UserInfoVO.RouteVO> buildSuperAdminRoutes() {
        return List.of(
            route("/admin/dashboard", "系统概览"),
            route("/admin/companies", "企业管理"),
            route("/admin/audit-log", "审计日志"),
            route("/admin/settings", "系统设置")
        );
    }

    private List<UserInfoVO.RouteVO> buildCompanyRoutes(List<String> permissions) {
        List<UserInfoVO.RouteVO> routes = new java.util.ArrayList<>();
        routes.add(route("/company/dashboard", "工作台"));
        if (permissions.contains("dept:view") || permissions.contains("branch:view")) {
            routes.add(route("/company/org", "组织管理"));
        }
        if (permissions.contains("user:view")) {
            routes.add(route("/company/users", "人员管理"));
        }
        if (permissions.contains("role:view")) {
            routes.add(route("/company/roles", "角色与权限"));
        }
        if (permissions.contains("log:view")) {
            routes.add(route("/company/audit-log", "审计日志"));
        }
        if (permissions.contains("setting:view")) {
            routes.add(route("/company/settings", "公司设置"));
        }
        return routes;
    }

    private UserInfoVO.RouteVO route(String path, String name) {
        return UserInfoVO.RouteVO.builder().path(path).name(name).build();
    }
}
```

- [ ] **Step 3: 运行完整测试**

```powershell
cd "E:\OneDrive\manager-software\tengyei-backend"
mvn test
```

期望：所有测试通过

- [ ] **Step 4: 最终 Commit**

```bash
git add tengyei-backend/core-auth/
git commit -m "feat: add userinfo endpoint returning routes and permissions by role"
```

---

## 自检清单

- [x] Task 1: Maven 多模块结构 ✅
- [x] Task 2: Spring Boot 主应用 + 配置 ✅
- [x] Task 3: Flyway V1 建表（13 张表）✅
- [x] Task 4: Flyway V2 初始数据（权限点 + 超级管理员）✅
- [x] Task 5: Common 模块（TenantContext、Result、异常处理、MyBatisPlus 多租户、Redis）✅
- [x] Task 6: JWT 生成/验证/刷新 ✅
- [x] Task 7: 登录/登出/刷新接口 + Spring Security ✅
- [x] Task 8: Vue 3 + Element Plus 前端初始化 ✅
- [x] Task 9: Axios 封装 + Auth Store ✅
- [x] Task 10: 登录页 + 路由守卫 ✅
- [x] Task 11: UserInfo 接口 ✅

**Phase 1 完成后可以：**
- ✅ 超级管理员登录，获取 JWT
- ✅ 调用 `/api/v1/auth/userinfo` 获取路由和权限列表
- ✅ 多租户数据隔离（MyBatisPlus 拦截器）已就位
- ✅ Token 黑名单登出

**下一步（Plan 2）：超级管理员公司管理模块**
