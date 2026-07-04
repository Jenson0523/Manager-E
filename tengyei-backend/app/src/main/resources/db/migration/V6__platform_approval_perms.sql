-- 平台层审批权限：平台角色可被授予审批能力（配置流程/发起/审批），与公司层 approval:* 平行
INSERT INTO permission (module, code, name, tier, sort_order) VALUES
('approval', 'platform:approval:view',     '查看审批',     'platform', 210),
('approval', 'platform:approval:apply',    '发起审批',     'platform', 211),
('approval', 'platform:approval:approve',  '审批通过',     'platform', 212),
('approval', 'platform:approval:reject',   '审批驳回',     'platform', 213),
('approval', 'platform:approval:transfer', '审批转交',     'platform', 214),
('approval', 'platform:approval:cancel',   '审批撤回',     'platform', 215),
('approval', 'platform:approval:delegate', '审批代理',     'platform', 216),
('approval', 'platform:approval:manage',   '流程管理',     'platform', 217);
