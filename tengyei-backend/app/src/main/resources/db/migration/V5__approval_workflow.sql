-- OA 审批流程：4 张核心表 + PERM_approval:* 权限点
-- 所有表均带 tenant_id 以复用 MyBatis-Plus 租户插件自动隔离（与 company/dept/user 等一致）

CREATE TABLE wf_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    form_type VARCHAR(64) NOT NULL COMMENT '表单类型：leave/expense/purchase',
    form_name VARCHAR(128) NOT NULL COMMENT '表单名称',
    process_key VARCHAR(128) NOT NULL COMMENT '流程标识',
    config_json TEXT NOT NULL COMMENT '审批节点 JSON 配置',
    version INT NOT NULL DEFAULT 1,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    is_default TINYINT NOT NULL DEFAULT 1,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_form (tenant_id, form_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程定义';

CREATE TABLE wf_instance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    instance_no VARCHAR(64) NOT NULL COMMENT '实例编号 WF+日期+序号',
    definition_id BIGINT NOT NULL,
    form_type VARCHAR(64) NOT NULL,
    form_data TEXT COMMENT '表单字段快照 JSON，未挂业务表时的数据来源',
    biz_type VARCHAR(64) COMMENT '业务模块自有表单时填写',
    biz_id BIGINT COMMENT '业务模块自有表单时填写',
    biz_table VARCHAR(128) COMMENT '业务模块自有表单时填写',
    applicant_id BIGINT NOT NULL,
    applicant_name VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/CANCELED',
    current_node VARCHAR(64) COMMENT '当前待办节点 key',
    priority TINYINT NOT NULL DEFAULT 0,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_instance_no (instance_no),
    INDEX idx_tenant_applicant (tenant_id, applicant_id),
    INDEX idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程实例';

CREATE TABLE wf_node (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    node_key VARCHAR(64) NOT NULL,
    node_name VARCHAR(128) NOT NULL,
    approver_type VARCHAR(32) NOT NULL COMMENT 'LEADER/DEPT_LEADER/SPECIFIC_USER/ROLE/SELF_APPROVE',
    target_user_id BIGINT COMMENT 'approverType=SPECIFIC_USER 时来自配置',
    target_role_id BIGINT COMMENT 'approverType=ROLE 时来自配置',
    approver_id BIGINT COMMENT '解析后的审批人ID，多人取首个/或签任一',
    approver_name VARCHAR(64) COMMENT '解析时点快照，防止人员变更后断链',
    resolve_mode VARCHAR(32) NOT NULL DEFAULT 'FIRST' COMMENT 'FIRST/ALL/ANYONE',
    node_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING/APPROVING/APPROVED/REJECTED/CANCELED',
    result VARCHAR(32),
    comment TEXT,
    action_by BIGINT,
    action_at DATETIME,
    due_at DATETIME,
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_instance (instance_id),
    INDEX idx_approver (tenant_id, approver_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批节点';

CREATE TABLE wf_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    node_id BIGINT,
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL COMMENT 'APPLY/APPROVE/REJECT/TRANSFER/AGENT/RETURN/CANCEL',
    comment TEXT,
    before_status VARCHAR(32),
    after_status VARCHAR(32),
    target_user_id BIGINT,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_instance (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批操作记录';

INSERT INTO permission (module, code, name, tier, sort_order) VALUES
('approval', 'approval:view',     '查看审批',     'company', 200),
('approval', 'approval:apply',    '发起审批',     'company', 201),
('approval', 'approval:approve',  '审批通过',     'company', 202),
('approval', 'approval:reject',   '审批驳回',     'company', 203),
('approval', 'approval:transfer', '审批转交',     'company', 204),
('approval', 'approval:cancel',   '审批撤回',     'company', 205),
('approval', 'approval:delegate', '审批代理',     'company', 206),
('approval', 'approval:manage',   '流程管理',     'company', 207);

-- 让存量 company_admin 角色自动拿到新权限（照 V3 回填逻辑）
INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, NOW()
FROM role r
JOIN permission p ON p.tier = 'company' AND p.module = 'approval'
WHERE r.code = 'company_admin'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
