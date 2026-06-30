package com.tengyei.platform.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlatformRoleVO {
    private Long id;
    private String name;
    private String code;
    private String description;
    private Integer isPreset;
    private Integer status;
}
