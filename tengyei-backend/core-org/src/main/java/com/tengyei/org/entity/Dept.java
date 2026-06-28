package com.tengyei.org.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tengyei.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("dept")
public class Dept extends BaseEntity {
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private Long tenantId;
    private String name;
    private String code;
    private Long parentId;
    private Long leaderId;
    private Integer sortOrder;
    private Integer status;
}
