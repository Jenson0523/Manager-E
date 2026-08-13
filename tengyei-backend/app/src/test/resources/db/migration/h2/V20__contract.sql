CREATE TABLE biz_contract (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    contract_no VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(16) NOT NULL DEFAULT 'SALE',
    party_b VARCHAR(128) NOT NULL,
    party_b_contact VARCHAR(64),
    party_b_phone VARCHAR(32),
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    sign_date DATE,
    start_date DATE,
    end_date DATE,
    owner_id BIGINT,
    owner_dept_id BIGINT,
    owner_branch_id BIGINT,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    attachments VARCHAR(2048),
    remark VARCHAR(512),
    expire_notified_on DATE,
    created_by VARCHAR(64),
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO permission (module, code, name, tier, sort_order) VALUES
('contract', 'contract:view',    '查看合同',      'company', 240),
('contract', 'contract:manage',  '新增/编辑合同', 'company', 241),
('contract', 'contract:disable', '作废/删除合同', 'company', 242);

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, NOW()
FROM role r
JOIN permission p ON p.code IN ('contract:view', 'contract:manage', 'contract:disable')
WHERE r.code = 'company_admin'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

INSERT INTO module_registry (module_code, module_name, version, entry_url, menu_config, permissions, status) VALUES
('contract', '合同管理', '1.0.0', '/company/contract',
 '["合同管理"]',
 '["contract:view","contract:manage","contract:disable"]',
 1);
