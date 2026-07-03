CREATE TABLE wf_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    form_type VARCHAR(64) NOT NULL,
    form_name VARCHAR(128) NOT NULL,
    process_key VARCHAR(128) NOT NULL,
    config_json TEXT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    status TINYINT NOT NULL DEFAULT 1,
    is_default TINYINT NOT NULL DEFAULT 1,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE wf_instance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    instance_no VARCHAR(64) NOT NULL,
    definition_id BIGINT NOT NULL,
    form_type VARCHAR(64) NOT NULL,
    form_data TEXT,
    biz_type VARCHAR(64),
    biz_id BIGINT,
    biz_table VARCHAR(128),
    applicant_id BIGINT NOT NULL,
    applicant_name VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    current_node VARCHAR(64),
    priority TINYINT NOT NULL DEFAULT 0,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (instance_no)
);

CREATE TABLE wf_node (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    node_key VARCHAR(64) NOT NULL,
    node_name VARCHAR(128) NOT NULL,
    approver_type VARCHAR(32) NOT NULL,
    target_user_id BIGINT,
    target_role_id BIGINT,
    approver_id BIGINT,
    approver_name VARCHAR(64),
    resolve_mode VARCHAR(32) NOT NULL DEFAULT 'FIRST',
    node_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'WAITING',
    result VARCHAR(32),
    comment TEXT,
    action_by BIGINT,
    action_at TIMESTAMP,
    due_at TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE wf_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    node_id BIGINT,
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    comment TEXT,
    before_status VARCHAR(32),
    after_status VARCHAR(32),
    target_user_id BIGINT,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO permission (module, code, name, tier, sort_order) VALUES
('approval', 'approval:view',     '查看审批',     'company', 200),
('approval', 'approval:apply',    '发起审批',     'company', 201),
('approval', 'approval:approve',  '审批通过',     'company', 202),
('approval', 'approval:reject',   '审批驳回',     'company', 203),
('approval', 'approval:transfer', '审批转交',     'company', 204),
('approval', 'approval:cancel',   '审批撤回',     'company', 205),
('approval', 'approval:delegate', '审批代理',     'company', 206),
('approval', 'approval:manage',   '流程管理',     'company', 207);

INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, NOW()
FROM role r
JOIN permission p ON p.tier = 'company' AND p.module = 'approval'
WHERE r.code = 'company_admin'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
