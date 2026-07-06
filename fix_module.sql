CREATE TABLE IF NOT EXISTS module_registry (
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

INSERT IGNORE INTO permission (module, code, name, tier, sort_order) VALUES
('platform', 'platform:module:view',   '查看模块管理', 'platform', 160),
('platform', 'platform:module:create', '注册新模块',   'platform', 161),
('platform', 'platform:module:edit',   '编辑模块',     'platform', 162),
('platform', 'platform:module:disable','启用/停用模块','platform', 163);

INSERT IGNORE INTO module_registry (module_code, module_name, version, entry_url, menu_config, permissions, status) VALUES
('approval', 'OA审批中心', '1.0.0', '/company/approval',
 '["审批中心"]',
 '["approval:view","approval:apply","approval:approve","approval:manage"]',
 1);
