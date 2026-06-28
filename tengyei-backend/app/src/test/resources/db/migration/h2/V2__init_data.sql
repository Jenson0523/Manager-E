-- H2-compatible version of V2__init_data.sql
-- Identical content to the main V2 migration

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

-- Password: Admin@2026 (BCrypt strength 12)
INSERT INTO user (
    tenant_id, user_no, username, password, real_name, phone,
    is_super_admin, status, pwd_reset_required
) VALUES (
    0,
    'SUPER-ADMIN-001',
    'superadmin',
    '$2a$12$ufBfS4xdJAYPymNVNHkCbepoD4vzNhw.A2pR942oNeIfzpZFNO.R2',
    '超级管理员',
    '13800000000',
    1, 1, 0
);

INSERT INTO system_config (tenant_id, config_key, config_value, description) VALUES
(0, 'login.max_fail_count',    '5',    '最大登录失败次数'),
(0, 'login.lock_minutes',      '15',   '账号锁定时长（分钟）'),
(0, 'login.single_device',     'false','是否单端登录'),
(0, 'data.log_retention_days', '730',  '日志保留天数（平台级）');
