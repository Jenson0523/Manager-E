package com.tengyei.company.dto;

import com.tengyei.company.entity.Company;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
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
    private LocalDate expireDate;
    private String logoUrl;
    @Setter
    private String adminUsername;
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
                .expireDate(c.getExpireDate())
                .logoUrl(c.getLogoUrl())
                .createdAt(c.getCreatedAt())
                .build();
    }
}

