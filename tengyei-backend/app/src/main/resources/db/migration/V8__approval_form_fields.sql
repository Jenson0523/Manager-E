-- 审批表单字段定义: [{key,label,type(text|number|date|textarea|select),required,options}]
ALTER TABLE wf_definition ADD COLUMN fields_json TEXT COMMENT '表单字段定义 JSON';
