package com.tengyei.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tengyei.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@TableName("biz_contract")
public class Contract extends BaseEntity {
    private Long tenantId;
    private String contractNo;
    private String name;
    private String type;
    private String partyB;
    private String partyBContact;
    private String partyBPhone;
    private BigDecimal amount;
    private LocalDate signDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long ownerId;
    private Long ownerDeptId;
    private Long ownerBranchId;
    private String status;
    private String attachments;
    private String remark;
    private LocalDate expireNotifiedOn;
    private String createdBy;
}
