package com.tengyei.org.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApprovalFlowVO {
    private Long id;
    private String formType;
    private String formName;
    private String processKey;
    private String configJson;
    private String fieldsJson;
    private Integer version;
    private Integer status;
}
