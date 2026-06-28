package com.tengyei.company.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tengyei.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@TableName("company")
public class Company extends BaseEntity {
    private String companyNo;
    private String fullName;
    private String shortName;
    private String creditCode;
    private String logoUrl;
    private String adminName;
    private String adminPhone;
    private String adminEmail;
    /** 0=待激活 1=启用 2=停用 */
    private Integer status;
    private LocalDate expireDate;
    private Integer maxUsers;
    private Integer maxBranches;
    private String remark;
}
