package com.tengyei.org.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ContractVO {
    private Long id;
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
    private String ownerName;
    private Long ownerDeptId;
    private String ownerDeptName;
    private String status;
    private List<ContractSaveDTO.Attachment> attachments;
    private String remark;
    private String createdBy;
    private LocalDateTime createdAt;
    /** 距到期天数:负数=已过期,null=无固定期限或已完成/终止 */
    private Long daysToExpire;
}
