package com.tengyei.org.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApprovalFlowSaveDTO {
    @NotBlank(message = "表单类型不能为空")
    private String formType;
    @NotBlank(message = "表单名称不能为空")
    private String formName;
    @NotBlank(message = "流程标识不能为空")
    private String processKey;
    @NotBlank(message = "审批节点配置不能为空")
    private String configJson;
}
