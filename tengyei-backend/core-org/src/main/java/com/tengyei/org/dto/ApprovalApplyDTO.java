package com.tengyei.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class ApprovalApplyDTO {
    @NotBlank(message = "表单类型不能为空")
    private String formType;
    @NotNull(message = "表单数据不能为空")
    private Map<String, Object> formData;
}
