-- V10 H2: 模块注册管理权限点 + 预置模块数据 (H2 test version)

INSERT INTO permission (module, code, name, tier, sort_order) VALUES
('platform', 'platform:module:view',   '查看模块管理', 'platform', 160),
('platform', 'platform:module:create', '注册新模块',   'platform', 161),
('platform', 'platform:module:edit',   '编辑模块',     'platform', 162),
('platform', 'platform:module:disable','启用/停用模块','platform', 163);

INSERT INTO module_registry (module_code, module_name, version, entry_url, menu_config, permissions, status) VALUES
('approval', 'OA审批中心', '1.0.0', '/company/approval',
 '["审批中心"]',
 '["approval:view","approval:apply","approval:approve","approval:manage"]',
 1);
