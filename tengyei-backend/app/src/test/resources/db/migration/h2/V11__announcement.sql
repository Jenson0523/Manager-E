CREATE TABLE sys_announcement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(512),
    level VARCHAR(16) NOT NULL DEFAULT 'INFO',
    link_url VARCHAR(255),
    target_scope VARCHAR(16) NOT NULL DEFAULT 'SELF',
    target_ids VARCHAR(512),
    start_at TIMESTAMP,
    end_at TIMESTAMP,
    status TINYINT NOT NULL DEFAULT 1,
    created_by VARCHAR(64),
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO permission (module, code, name, tier, sort_order) VALUES
('announcement', 'announcement:manage',          '公告管理', 'company',  220),
('announcement', 'platform:announcement:manage', '公告管理', 'platform', 230);

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, NOW()
FROM role r
JOIN permission p ON p.code = 'announcement:manage'
WHERE r.code = 'company_admin'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
