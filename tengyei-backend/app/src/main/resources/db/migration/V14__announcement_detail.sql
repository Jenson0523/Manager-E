-- 通知详情:记录发布人ID(老数据为空,详情回退显示 created_by 姓名)
ALTER TABLE sys_announcement ADD COLUMN created_by_id BIGINT COMMENT '发布人用户ID';

-- 权限名与菜单名对齐:公告管理 -> 通知管理
UPDATE permission SET name = '通知管理' WHERE code IN ('announcement:manage', 'platform:announcement:manage');
