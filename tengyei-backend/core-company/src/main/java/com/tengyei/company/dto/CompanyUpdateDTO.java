package com.tengyei.company.dto;

import com.tengyei.common.validation.CreditCode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CompanyUpdateDTO {
    @NotBlank(message = "企业全称不能为空")
    private String fullName;
    @NotBlank(message = "企业简称不能为空")
    private String shortName;
    @CreditCode
    private String creditCode;
    private String adminName;
    private String adminPhone;
    private String adminEmail;
    private String logoUrl;
    private LocalDate expireDate;
    private String remark;
}
