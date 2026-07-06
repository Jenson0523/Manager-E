-- 横幅公告:平台/公司独立发布,平台可定向发给指定企业;分紧急程度,可带跳转链接
CREATE TABLE sys_announcement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL COMMENT '发布方租户(0=平台)',
    title VARCHAR(128) NOT NULL,
    content VARCHAR(512),
    level VARCHAR(16) NOT NULL DEFAULT 'INFO' COMMENT 'INFO/WARN/URGENT',
    link_url VARCHAR(255) COMMENT '点击跳转的站内路径,可空',
    target_scope VARCHAR(16) NOT NULL DEFAULT 'SELF' COMMENT 'SELF=本租户 ALL_COMPANIES=全部企业 COMPANIES=指定企业(仅平台)',
    target_ids VARCHAR(512) COMMENT 'scope=COMPANIES 时的企业ID,逗号分隔',
    start_at DATETIME COMMENT '展示开始,空=立即',
    end_at DATETIME COMMENT '展示结束,空=长期',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    created_by VARCHAR(64) COMMENT '发布人姓名',
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='横幅公告';

INSERT INTO permission (module, code, name, tier, sort_order) VALUES
('announcement', 'announcement:manage',          '公告管理', 'company',  220),
('announcement', 'platform:announcement:manage', '公告管理', 'platform', 230);

-- 存量 company_admin 自动获得公告管理权(与 V5 审批权限回填同模式)
INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, NOW()
FROM role r
JOIN permission p ON p.code = 'announcement:manage'
WHERE r.code = 'company_admin'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
