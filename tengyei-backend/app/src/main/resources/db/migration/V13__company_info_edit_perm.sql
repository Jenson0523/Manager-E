-- 企业信息维护权限(logo 等企业自有信息),回填存量企业管理员
INSERT INTO permission (module, code, name, tier, sort_order) VALUES
('company', 'company:info:edit', '企业信息维护', 'company', 240);

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, NOW()
FROM role r
JOIN permission p ON p.code = 'company:info:edit'
WHERE r.code = 'company_admin'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
