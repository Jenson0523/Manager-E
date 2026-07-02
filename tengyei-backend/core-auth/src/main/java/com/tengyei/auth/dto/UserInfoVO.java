package com.tengyei.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserInfoVO {
    private Long userId;
    private Long tenantId;
    private Long branchId;
    private String username;
    private String realName;
    private String avatarUrl;
    private String companyName;
    private String companyLogo;
    private Boolean isSuperAdmin;
    private String dataScope;
    private List<String> roleCodes;
    private List<String> permissions;
    private List<RouteVO> routes;
    private Boolean pwdResetRequired;

    @Data
    @Builder
    public static class RouteVO {
        private String path;
        private String name;
        private List<RouteVO> children;
    }
}
