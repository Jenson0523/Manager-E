CREATE TABLE IF NOT EXISTS user_dept (
    ID BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    dept_id BIGINT NOT NULL,
    is_primary TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, dept_id)
);

INSERT IGNORE INTO user_dept (user_id, dept_id, is_primary, created_at)
SELECT id, dept_id, 1, NOW() FROM user WHERE dept_id IS NOT NULL AND dept_id > 0 AND is_deleted = 0;
