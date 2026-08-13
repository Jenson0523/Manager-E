-- H2 镜像:V20 的建表未创建 idx_tenant_no,故只加唯一约束
ALTER TABLE biz_contract
    ADD CONSTRAINT uk_tenant_contract_no UNIQUE (tenant_id, contract_no);
