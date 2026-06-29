package com.tengyei.org.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class UserExportVO {
    @ExcelProperty("姓名")
    private String realName;

    @ExcelProperty("账号")
    private String username;

    @ExcelProperty("手机号")
    private String phone;

    @ExcelProperty("邮箱")
    private String email;

    @ExcelProperty("部门")
    private String deptName;

    @ExcelProperty("角色")
    private String roles;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("创建时间")
    private String createdAt;
}
