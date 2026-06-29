package com.tengyei.org.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class UserVO {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private Long deptId;
    private Long branchId;
    private Integer status;
    private List<Long> roleIds;
    private List<String> roleNames;
}
