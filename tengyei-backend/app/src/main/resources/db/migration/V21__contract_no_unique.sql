-- 合同编号改为「永久唯一」:编号一旦使用就不再复用(含已删除的合同),符合台账审计习惯;
-- 同时用数据库约束根治 assertNoNotTaken「先查后插」在并发下可插入重号的问题。
-- 唯一键覆盖软删记录,故 ContractService 的查重与自动编号一并改为不排除 is_deleted。

-- 原普通索引与新唯一键前缀相同,保留会成为冗余索引
ALTER TABLE biz_contract DROP INDEX idx_tenant_no;

ALTER TABLE biz_contract
    ADD CONSTRAINT uk_tenant_contract_no UNIQUE (tenant_id, contract_no);
