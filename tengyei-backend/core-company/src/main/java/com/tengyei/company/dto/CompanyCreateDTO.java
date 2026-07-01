package com.tengyei.company.dto;

import com.tengyei.common.validation.CreditCode;
import com.tengyei.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CompanyCreateDTO {
    @NotBlank(message = "企业全称不能为空")
    private String fullName;
    @NotBlank(message = "企业简称不能为空")
    private String shortName;
    @CreditCode
    private String creditCode;
    private LocalDate expireDate;
    @NotBlank(message = "管理员姓名不能为空")
    private String adminName;
    @NotBlank(message = "管理员电话不能为空")
    private String adminPhone;
    private String adminEmail;
    @NotBlank(message = "初始管理员账号不能为空")
    private String adminUsername;
    @NotBlank(message = "初始管理员密码不能为空")
    @StrongPassword
    private String adminPassword;
}
