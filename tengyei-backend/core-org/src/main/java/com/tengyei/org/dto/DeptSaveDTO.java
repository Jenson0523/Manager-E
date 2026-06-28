package com.tengyei.org.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeptSaveDTO {
    @NotBlank(message = "部门名称不能为空")
    private String name;
    private String code;
    private Long parentId;
    private Long leaderId;
    private Integer sortOrder;
}
