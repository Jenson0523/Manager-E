package com.tengyei.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tengyei.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("user")
public class User extends BaseEntity {
    private Long tenantId;
    private String userNo;
    private String username;
    private String password;
    private String realName;
    private String phone;
    private String email;
    private String avatarUrl;
    private Long deptId;
    private Long branchId;
    private Long leaderId;
    private LocalDate entryDate;
    private Integer isSuperAdmin;
    private Integer status;
    private Integer pwdResetRequired;
    private Integer loginFailCount;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
}
