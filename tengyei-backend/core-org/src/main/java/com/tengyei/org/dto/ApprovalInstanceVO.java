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
    /** 流程定义的表单字段配置(JSON)，前端按此结构化展示表单数据 */
    private String fieldsJson;
    private Long applicantId;
    private String applicantName;
    private String status;
    private String currentNode;
    private Integer priority;
    private LocalDateTime createdAt;
    /** 待办列表用:我的待办节点截止时间(超时提醒) */
    private LocalDateTime myDueAt;
    /** 警告提示(如节点无可用审批人等) */
    private String warning;
    private List<NodeVO> nodes;

    @Data
    @Builder
    public static class NodeVO {
        private Long id;
        private String nodeKey;
        private String nodeName;
        private Long approverId;
        private String approverName;
        private String status;
        private String result;
        private String comment;
        private LocalDateTime actionAt;
        private LocalDateTime dueAt;
        private String rejectPolicy;
    }
}
