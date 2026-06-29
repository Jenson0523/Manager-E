package com.tengyei.org.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BranchSaveDTO {
    @NotBlank(message = "机构编号不能为空")
    private String branchNo;
    @NotBlank(message = "机构名称不能为空")
    private String name;
    private String type;
    private String province;
    private String city;
    private String district;
    private String address;
    private Long leaderId;
    private String phone;
    private Integer maxUsers;
}
