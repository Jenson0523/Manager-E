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
    /** CC节点专用:抄送人ID列表(approverType=CC时使用) */
    private List<Long> ccUserIds;

    @Data
    public static class Wrapper {
        private List<ApprovalNodeConfig> nodes;
    }
}
