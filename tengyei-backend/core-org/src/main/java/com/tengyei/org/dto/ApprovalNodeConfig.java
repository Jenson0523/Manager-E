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

    @Data
    public static class Wrapper {
        private List<ApprovalNodeConfig> nodes;
    }
}
