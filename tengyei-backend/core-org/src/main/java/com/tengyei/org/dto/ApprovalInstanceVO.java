package com.tengyei.org.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ApprovalInstanceVO {
    private Long id;
    private String instanceNo;
    private String formType;
    private String formName;
    private String formData;
    private Long applicantId;
    private String applicantName;
    private String status;
    private String currentNode;
    private Integer priority;
    private LocalDateTime createdAt;
    private List<NodeVO> nodes;

    @Data
    @Builder
    public static class NodeVO {
        private Long id;
        private String nodeKey;
        private String nodeName;
        private String approverName;
        private String status;
        private String result;
        private String comment;
        private LocalDateTime actionAt;
    }
}
