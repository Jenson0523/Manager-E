-- 多部门员工发起审批时可选提交部门,部门负责人(DEPT_LEADER)按此部门解析;为空则回退主部门
ALTER TABLE wf_instance ADD COLUMN submit_dept_id BIGINT NULL COMMENT '发起时所选部门(多部门员工),部门负责人按此解析';
