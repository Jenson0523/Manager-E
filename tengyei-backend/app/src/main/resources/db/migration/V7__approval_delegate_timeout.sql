-- 审批代理规则(一人一条) + 节点超时小时数
CREATE TABLE wf_delegate (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL COMMENT '委托人',
    owner_name VARCHAR(64) NOT NULL,
    delegate_id BIGINT NOT NULL COMMENT '代理人',
    delegate_name VARCHAR(64) NOT NULL,
    start_at DATETIME NOT NULL,
    end_at DATETIME NOT NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_owner (tenant_id, owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批代理规则';

ALTER TABLE wf_node ADD COLUMN timeout_hours INT COMMENT '超时小时数,激活时换算 due_at';
