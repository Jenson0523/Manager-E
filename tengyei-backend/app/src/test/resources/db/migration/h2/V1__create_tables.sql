-- H2-compatible version of V1__create_tables.sql
-- Differences from MySQL: unique index names, no ON UPDATE CURRENT_TIMESTAMP

CREATE TABLE company (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_no VARCHAR(20) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    short_name VARCHAR(50) NOT NULL,
    credit_code VARCHAR(18),
    logo_url VARCHAR(255),
    admin_name VARCHAR(20) NOT NULL,
    admin_phone VARCHAR(20) NOT NULL,
    admin_email VARCHAR(100),
    status TINYINT NOT NULL DEFAULT 0,
    expire_date DATE,
    max_users INT,
    max_branches INT,
    remark VARCHAR(200),
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE dept (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    code VARCHAR(30),
    parent_id BIGINT NOT NULL DEFAULT 0,
    leader_id BIGINT,
    sort_order INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX dept_idx_tenant (tenant_id)
);

CREATE TABLE branch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    branch_no VARCHAR(20) NOT NULL,
    name VARCHAR(50) NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'independent',
    province VARCHAR(20),
    city VARCHAR(20),
    district VARCHAR(20),
    address VARCHAR(200),
    leader_id BIGINT,
    phone VARCHAR(20),
    max_users INT,
    status TINYINT NOT NULL DEFAULT 1,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_branch_no (tenant_id, branch_no),
    INDEX branch_idx_tenant (tenant_id)
);

CREATE TABLE branch_dept (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    dept_id BIGINT NOT NULL,
    UNIQUE KEY uk_branch_dept (branch_id, dept_id),
    INDEX branch_dept_idx_tenant (tenant_id)
);

CREATE TABLE user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    user_no VARCHAR(20) NOT NULL,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(20) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(100),
    avatar_url VARCHAR(255),
    dept_id BIGINT,
    branch_id BIGINT,
    leader_id BIGINT,
    entry_date DATE,
    is_super_admin TINYINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    pwd_reset_required TINYINT NOT NULL DEFAULT 1,
    login_fail_count INT NOT NULL DEFAULT 0,
    locked_until DATETIME,
    last_login_at DATETIME,
    last_login_ip VARCHAR(50),
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_username (username),
    INDEX user_idx_tenant (tenant_id),
    INDEX user_idx_phone (phone)
);

CREATE TABLE role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    code VARCHAR(50) NOT NULL,
    description VARCHAR(200),
    data_scope VARCHAR(20) NOT NULL DEFAULT 'all',
    is_preset TINYINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX role_idx_tenant (tenant_id),
    UNIQUE KEY uk_tenant_code (tenant_id, code)
);

CREATE TABLE permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module VARCHAR(50) NOT NULL,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(200),
    sort_order INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1
);

CREATE TABLE role_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_perm (role_id, permission_id)
);

CREATE TABLE user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role (user_id, role_id)
);

CREATE TABLE audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(50) NOT NULL,
    module VARCHAR(50) NOT NULL,
    action_type VARCHAR(30) NOT NULL,
    description VARCHAR(500) NOT NULL,
    detail CLOB,
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    result TINYINT NOT NULL,
    error_msg VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX audit_log_idx_tenant (tenant_id),
    INDEX audit_log_idx_created (created_at)
);

CREATE TABLE login_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    username VARCHAR(50) NOT NULL,
    login_type VARCHAR(20) NOT NULL,
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    result TINYINT NOT NULL,
    fail_reason VARCHAR(200),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX login_log_idx_tenant (tenant_id),
    INDEX login_log_idx_user (user_id)
);

CREATE TABLE system_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    config_key VARCHAR(100) NOT NULL,
    config_value CLOB,
    description VARCHAR(200),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_key (tenant_id, config_key)
);

CREATE TABLE module_registry (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module_code VARCHAR(50) NOT NULL UNIQUE,
    module_name VARCHAR(100) NOT NULL,
    version VARCHAR(20) NOT NULL,
    entry_url VARCHAR(255) NOT NULL,
    menu_config CLOB NOT NULL,
    permissions CLOB NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
