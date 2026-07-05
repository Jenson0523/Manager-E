package com.tengyei.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tengyei.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_notice")
public class SysNotice extends BaseEntity {
    private Long tenantId;
    private Long userId;
    private String type;
    private String title;
    private String content;
    private String bizType;
    private Long bizId;
    private Integer isRead;
}
