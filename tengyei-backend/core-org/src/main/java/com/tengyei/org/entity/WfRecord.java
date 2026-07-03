package com.tengyei.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tengyei.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("wf_record")
public class WfRecord extends BaseEntity {
    private Long tenantId;
    private Long instanceId;
    private Long nodeId;
    private Long operatorId;
    private String operatorName;
    private String action;
    private String comment;
    private String beforeStatus;
    private String afterStatus;
    private Long targetUserId;
}
