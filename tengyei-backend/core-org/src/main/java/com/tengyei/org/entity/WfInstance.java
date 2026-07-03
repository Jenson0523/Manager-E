package com.tengyei.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tengyei.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("wf_instance")
public class WfInstance extends BaseEntity {
    private Long tenantId;
    private String instanceNo;
    private Long definitionId;
    private String formType;
    private String formData;
    private String bizType;
    private Long bizId;
    private String bizTable;
    private Long applicantId;
    private String applicantName;
    private String status;
    private String currentNode;
    private Integer priority;
}
