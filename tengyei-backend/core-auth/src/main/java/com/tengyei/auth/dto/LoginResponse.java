package com.tengyei.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String accessToken;
    private Long expiresIn;
    private Boolean pwdResetRequired;
    private String realName;
    private Long tenantId;
}
