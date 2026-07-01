-- 用户多部门关联表
CREATE TABLE IF NOT EXISTS user_dept (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    dept_id BIGINT NOT NULL COMMENT '部门ID',
    is_primary TINYINT NOT NULL DEFAULT 0 COMMENT '是否主部门：1是 0否',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_dept (user_id, dept_id),
    INDEX idx_user_id (user_id),
    INDEX idx_dept_id (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-部门关联表';

-- 迁移旧数据：将 user.dept_id 作为用户主部门关联
INSERT IGNORE INTO user_dept (user_id, dept_id, is_primary, created_at)
SELECT id, dept_id, 1, NOW() FROM `user` WHERE dept_id IS NOT NULL AND dept_id > 0 AND is_deleted = 0;
