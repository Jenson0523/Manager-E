INSERT INTO permission (module, code, name, tier, sort_order) VALUES
('announcement', 'announcement:view',            '查看通知',      'company',  219),
('announcement', 'announcement:disable',         '停用/删除通知', 'company',  221),
('announcement', 'platform:announcement:view',   '查看通知',      'platform', 229),
('announcement', 'platform:announcement:disable','停用/删除通知', 'platform', 231);

UPDATE permission SET name = '发布/编辑通知' WHERE code IN ('announcement:manage', 'platform:announcement:manage');

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT rp.role_id, p.id, NOW()
FROM role_permission rp
JOIN permission mp ON mp.id = rp.permission_id AND mp.code = 'announcement:manage'
JOIN permission p ON p.code = 'announcement:view'
WHERE NOT EXISTS (SELECT 1 FROM role_permission x WHERE x.role_id = rp.role_id AND x.permission_id = p.id);

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT rp.role_id, p.id, NOW()
FROM role_permission rp
JOIN permission mp ON mp.id = rp.permission_id AND mp.code = 'announcement:manage'
JOIN permission p ON p.code = 'announcement:disable'
WHERE NOT EXISTS (SELECT 1 FROM role_permission x WHERE x.role_id = rp.role_id AND x.permission_id = p.id);

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT rp.role_id, p.id, NOW()
FROM role_permission rp
JOIN permission mp ON mp.id = rp.permission_id AND mp.code = 'platform:announcement:manage'
JOIN permission p ON p.code = 'platform:announcement:view'
WHERE NOT EXISTS (SELECT 1 FROM role_permission x WHERE x.role_id = rp.role_id AND x.permission_id = p.id);

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT rp.role_id, p.id, NOW()
FROM role_permission rp
JOIN permission mp ON mp.id = rp.permission_id AND mp.code = 'platform:announcement:manage'
JOIN permission p ON p.code = 'platform:announcement:disable'
WHERE NOT EXISTS (SELECT 1 FROM role_permission x WHERE x.role_id = rp.role_id AND x.permission_id = p.id);
