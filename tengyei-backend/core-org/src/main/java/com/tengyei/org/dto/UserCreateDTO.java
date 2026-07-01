package com.tengyei.org.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class UserCreateDTO {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "真实姓名不能为空")
    private String realName;
    @NotBlank(message = "手机号不能为空")
    private String phone;
    private String email;
    @NotBlank(message = "初始密码不能为空")
    private String password;
    private Long deptId;
    private List<Long> deptIds;
    private Long branchId;
    private List<Long> roleIds;
}
