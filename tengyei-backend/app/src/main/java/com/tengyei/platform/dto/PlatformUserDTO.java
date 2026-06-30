package com.tengyei.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class PlatformUserDTO {
    @NotBlank(message = "账号不能为空")
    private String username;
    @NotBlank(message = "姓名不能为空")
    private String realName;
    private String phone;
    private String email;
    private String password;
    private List<Long> roleIds;
}
