CREATE TABLE wf_delegate (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    owner_name VARCHAR(64) NOT NULL,
    delegate_id BIGINT NOT NULL,
    delegate_name VARCHAR(64) NOT NULL,
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, owner_id)
);

ALTER TABLE wf_node ADD COLUMN timeout_hours INT;
