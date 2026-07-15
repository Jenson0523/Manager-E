-- 改密踢会话:记录最后一次改密时间,早于该时间签发的 token 一律拒绝
ALTER TABLE `user` ADD COLUMN pwd_changed_at DATETIME NULL COMMENT '最后改密时间,早于此时间签发的token失效';
