package com.tengyei.company.dto;

import com.tengyei.company.entity.Company;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CompanyVO {
    private Long id;
    private String companyNo;
    private String fullName;
    private String shortName;
    private String creditCode;
    private String adminName;
    private String adminPhone;
    private String adminEmail;
    private String remark;
    private Integer status;
    private LocalDateTime createdAt;

    public static CompanyVO from(Company c) {
        return CompanyVO.builder()
                .id(c.getId())
                .companyNo(c.getCompanyNo())
                .fullName(c.getFullName())
                .shortName(c.getShortName())
                .creditCode(c.getCreditCode())
                .adminName(c.getAdminName())
                .adminPhone(c.getAdminPhone())
                .adminEmail(c.getAdminEmail())
                .remark(c.getRemark())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
