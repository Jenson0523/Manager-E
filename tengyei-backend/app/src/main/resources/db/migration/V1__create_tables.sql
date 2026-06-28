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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公司/租户表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表';

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
    UNIQUE KEY uk_tenant_branch_no (tenant_id, branch_no),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分公司表';

-- =============================================
-- 分公司-部门关联表
-- =============================================
CREATE TABLE branch_dept (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    dept_id BIGINT NOT NULL,
    UNIQUE KEY uk_branch_dept (branch_id, dept_id),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分公司-部门关联';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限点表';

-- =============================================
-- 角色-权限关联
-- =============================================
CREATE TABLE role_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_perm (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-权限关联';

-- =============================================
-- 用户-角色关联
-- =============================================
CREATE TABLE user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关联';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='扩展模块注册表';
