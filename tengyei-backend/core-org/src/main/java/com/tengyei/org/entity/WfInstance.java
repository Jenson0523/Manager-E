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
    /** 发起时所选部门(多部门员工):部门负责人(DEPT_LEADER)按此解析,为空回退主部门 */
    private Long submitDeptId;
}
