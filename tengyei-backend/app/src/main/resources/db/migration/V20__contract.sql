-- 合同台账:租户内合同全生命周期登记(草稿->生效->履约->完成/终止),按 data_scope 隔离,到期前站内提醒

CREATE TABLE biz_contract (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    contract_no VARCHAR(64) NOT NULL COMMENT '合同编号,租户内唯一(服务层校验,软删不占用)',
    name VARCHAR(128) NOT NULL COMMENT '合同名称',
    type VARCHAR(16) NOT NULL DEFAULT 'SALE' COMMENT 'SALE=销售 PURCHASE=采购 SERVICE=劳务 LEASE=租赁 OTHER=其他',
    party_b VARCHAR(128) NOT NULL COMMENT '对方单位',
    party_b_contact VARCHAR(64) COMMENT '对方联系人',
    party_b_phone VARCHAR(32) COMMENT '对方联系电话',
    amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '合同金额(元)',
    sign_date DATE COMMENT '签订日期',
    start_date DATE COMMENT '生效日期',
    end_date DATE COMMENT '到期日期,空=无固定期限',
    owner_id BIGINT COMMENT '负责人(经办人)用户ID',
    owner_dept_id BIGINT COMMENT '归属部门,写入时按负责人主部门带出,data_scope=dept 时据此过滤',
    owner_branch_id BIGINT COMMENT '归属分支机构,data_scope=branch 时据此过滤',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT=草稿 EFFECTIVE=已生效 PERFORMING=履约中 COMPLETED=已完成 TERMINATED=已终止',
    attachments VARCHAR(2048) COMMENT '附件JSON数组 [{"name":"","url":""}]',
    remark VARCHAR(512) COMMENT '备注',
    expire_notified_on DATE COMMENT '最近一次到期提醒日期,同一天不重复推送',
    created_by VARCHAR(64) COMMENT '创建人姓名',
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_status (tenant_id, status),
    INDEX idx_tenant_end (tenant_id, end_date),
    INDEX idx_tenant_owner (tenant_id, owner_id),
    INDEX idx_tenant_no (tenant_id, contract_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同台账';

-- 权限三档(与 V15 通知拆分同颗粒度):查看可单独授予业务员,新增编辑与作废删除分开
INSERT INTO permission (module, code, name, tier, sort_order) VALUES
('contract', 'contract:view',    '查看合同',      'company', 240),
('contract', 'contract:manage',  '新增/编辑合同', 'company', 241),
('contract', 'contract:disable', '作废/删除合同', 'company', 242);

-- 存量 company_admin 自动获得全部合同权限(与 V11/V15 回填同模式)
INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, NOW()
FROM role r
JOIN permission p ON p.code IN ('contract:view', 'contract:manage', 'contract:disable')
WHERE r.code = 'company_admin'
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 上架合同模块(菜单入口由 module_registry 驱动,详见 docs/模块注册配置教程.md)
INSERT INTO module_registry (module_code, module_name, version, entry_url, menu_config, permissions, status) VALUES
('contract', '合同管理', '1.0.0', '/company/contract',
 '["合同管理"]',
 '["contract:view","contract:manage","contract:disable"]',
 1);
