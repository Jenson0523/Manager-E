package com.tengyei.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("permission")
public class Permission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String module;
    private String code;
    private String name;
    private String description;
    private Integer sortOrder;
    private Integer status;
}
