package com.tengyei.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tengyei.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("wf_node")
public class WfNode extends BaseEntity {
    private Long tenantId;
    private Long instanceId;
    private String nodeKey;
    private String nodeName;
    private String approverType;
    private Long targetUserId;
    private Long targetRoleId;
    private Long approverId;
    private String approverName;
    private String resolveMode;
    private Integer nodeOrder;
    private String status;
    private String result;
    private String comment;
    private Long actionBy;
    private LocalDateTime actionAt;
    private LocalDateTime dueAt;
    private Integer timeoutHours;
    // ponytail: 并发审批的乐观锁保护暂缓，version 列保留待需要时再接 @Version
    private Integer version;
}
