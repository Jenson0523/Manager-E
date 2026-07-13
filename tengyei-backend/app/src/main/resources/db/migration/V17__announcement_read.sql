-- 通知已读记录:打开详情即记一次(幂等)
CREATE TABLE sys_announcement_read (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    announcement_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(64),
    read_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ann_user (announcement_id, user_id),
    INDEX idx_ann (announcement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知已读记录';
