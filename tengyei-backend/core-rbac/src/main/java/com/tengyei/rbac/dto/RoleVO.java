package com.tengyei.rbac.dto;

import com.tengyei.rbac.entity.Role;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RoleVO {
    private Long id;
    private String name;
    private String code;
    private String description;
    private String dataScope;
    private Integer isPreset;
    private Integer status;

    public static RoleVO from(Role r) {
        return RoleVO.builder()
                .id(r.getId()).name(r.getName()).code(r.getCode())
                .description(r.getDescription()).dataScope(r.getDataScope())
                .isPreset(r.getIsPreset()).status(r.getStatus()).build();
    }
}
