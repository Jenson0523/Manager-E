-- 公告租户内定向:ALL=全员 / DEPT=指定部门 / ROLE=指定角色
ALTER TABLE sys_announcement
    ADD COLUMN audience_type VARCHAR(16) NOT NULL DEFAULT 'ALL' COMMENT '租户内接收范围 ALL/DEPT/ROLE',
    ADD COLUMN audience_ids VARCHAR(512) COMMENT '部门或角色ID,逗号分隔';
