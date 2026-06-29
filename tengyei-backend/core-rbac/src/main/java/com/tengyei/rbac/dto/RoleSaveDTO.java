package com.tengyei.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RoleSaveDTO {
    @NotBlank(message = "角色名称不能为空")
    private String name;
    @NotBlank(message = "角色编码不能为空")
    private String code;
    private String description;
    /** all | branch | dept | self */
    private String dataScope;
}
