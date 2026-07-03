package com.tengyei.org.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ApprovalApproveDTO {
    @Pattern(regexp = "APPROVE|REJECT", message = "action 只能是 APPROVE 或 REJECT")
    private String action;
    private String comment;
}
