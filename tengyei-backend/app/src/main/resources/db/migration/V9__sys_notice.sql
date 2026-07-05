-- 站内消息中心:审批待办/结果/超时等通知的统一落点
CREATE TABLE sys_notice (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL COMMENT '接收人',
    type VARCHAR(32) NOT NULL COMMENT 'APPROVAL_TODO/APPROVAL_RESULT/APPROVAL_TIMEOUT',
    title VARCHAR(128) NOT NULL,
    content VARCHAR(512),
    biz_type VARCHAR(32) COMMENT '关联业务类型,如 approval',
    biz_id BIGINT COMMENT '关联业务ID,如审批实例ID',
    is_read TINYINT NOT NULL DEFAULT 0,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_read (tenant_id, user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息';
