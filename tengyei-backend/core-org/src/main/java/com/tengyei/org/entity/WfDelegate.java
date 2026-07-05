package com.tengyei.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tengyei.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("wf_delegate")
public class WfDelegate extends BaseEntity {
    private Long tenantId;
    private Long ownerId;
    private String ownerName;
    private Long delegateId;
    private String delegateName;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer status;
}
