CREATE TABLE sys_announcement_read (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    announcement_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(64),
    read_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (announcement_id, user_id)
);
