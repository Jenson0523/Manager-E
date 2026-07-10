-- 审批深化:节点级驳回策略(TERMINATE=终结/TO_INITIATOR=退回发起人/TO_PREV=退回上一节点),空=TERMINATE
ALTER TABLE wf_node ADD COLUMN reject_policy VARCHAR(16) COMMENT '驳回策略,空=TERMINATE';
