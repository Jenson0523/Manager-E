package com.tengyei.org.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApprovalDelegateDTO {
    @NotNull(message = "代理人不能为空")
    private Long delegateId;
    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startAt;
    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endAt;
    private Integer status;
}
