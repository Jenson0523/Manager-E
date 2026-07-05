package com.tengyei.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tengyei.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("wf_definition")
public class WfDefinition extends BaseEntity {
    private Long tenantId;
    private String formType;
    private String formName;
    private String processKey;
    private String configJson;
    private String fieldsJson;
    private Integer version;
    private Integer status;
    private Integer isDefault;
}
