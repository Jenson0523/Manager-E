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
