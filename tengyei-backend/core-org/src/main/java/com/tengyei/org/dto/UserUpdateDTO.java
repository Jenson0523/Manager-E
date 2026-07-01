package com.tengyei.org.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class UserUpdateDTO {
    @NotBlank(message = "真实姓名不能为空")
    private String realName;
    @NotBlank(message = "手机号不能为空")
    private String phone;
    private String email;
    private Long deptId;
    private List<Long> deptIds;
    private Long branchId;
}
