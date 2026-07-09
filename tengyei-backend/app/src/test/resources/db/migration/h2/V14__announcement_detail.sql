ALTER TABLE sys_announcement ADD COLUMN created_by_id BIGINT;

UPDATE permission SET name = '通知管理' WHERE code IN ('announcement:manage', 'platform:announcement:manage');
