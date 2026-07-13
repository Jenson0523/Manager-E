package com.tengyei.org.dto;

import lombok.Data;

import java.util.List;

@Data
public class ApprovalNodeConfig {
    private String key;
    private String name;
    private String approverType;
    private String resolveMode;
    private Integer orderBy;
    private String condition;
    private Long targetUserId;
    private Long targetRoleId;
    private Integer timeoutHours;
    /** 驳回策略:TERMINATE(默认)/TO_INITIATOR/TO_PREV */
    private String rejectPolicy;

    @Data
    public static class Wrapper {
        private List<ApprovalNodeConfig> nodes;
        /** 默认抄送人ID列表(管理员在流程中配置,发起人提交时预填) */
        private List<Long> ccUserIds;
    }
}
