package com.tengyei.org.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/** 人员批量导入行(也用于生成导入模板) */
@Data
@ColumnWidth(20)
public class UserImportRowVO {

    @ExcelProperty("姓名(必填)")
    private String realName;

    @ExcelProperty("账号(必填)")
    private String username;

    @ExcelProperty("初始密码(必填)")
    private String password;

    @ExcelProperty("手机(必填)")
    private String phone;

    @ExcelProperty("邮箱")
    private String email;

    @ExcelProperty("部门(部门名,可留空)")
    private String deptName;

    @ExcelProperty("角色(角色名,多个用逗号分隔)")
    private String roleNames;
}
