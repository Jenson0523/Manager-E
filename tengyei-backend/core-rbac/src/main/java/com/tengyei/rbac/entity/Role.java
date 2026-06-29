package com.tengyei.rbac.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tengyei.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("role")
public class Role extends BaseEntity {
    private Long tenantId;
    private String name;
    private String code;
    private String description;
    /** all | branch | dept | self */
    private String dataScope;
    private Integer isPreset;
    private Integer status;
}
