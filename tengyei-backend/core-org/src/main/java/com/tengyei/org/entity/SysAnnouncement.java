package com.tengyei.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tengyei.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("sys_announcement")
public class SysAnnouncement extends BaseEntity {
    private Long tenantId;
    private String title;
    private String content;
    private String level;
    private String linkUrl;
    private String targetScope;
    private String targetIds;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer status;
    private String createdBy;
}
