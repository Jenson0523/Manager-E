package com.tengyei.org.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BatchRolesDTO {
    @NotEmpty(message = "ids 不能为空")
    private List<Long> ids;

    @NotNull(message = "roleIds 不能为空")
    private List<Long> roleIds;
}
