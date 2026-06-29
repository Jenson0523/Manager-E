package com.tengyei.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.tengyei.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("branch")
public class Branch extends BaseEntity {
    private Long tenantId;
    private String branchNo;
    private String name;
    /** independent | affiliated */
    private String type;
    private String province;
    private String city;
    private String district;
    private String address;
    private Long leaderId;
    private String phone;
    private Integer maxUsers;
    private Integer status;
}
