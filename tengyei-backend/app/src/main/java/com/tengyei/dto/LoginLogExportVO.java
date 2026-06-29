package com.tengyei.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class LoginLogExportVO {
    @ExcelProperty("账号")
    private String username;

    @ExcelProperty("登录方式")
    private String loginType;

    @ExcelProperty("IP地址")
    private String ipAddress;

    @ExcelProperty("结果")
    private String result;

    @ExcelProperty("失败原因")
    private String failReason;

    @ExcelProperty("时间")
    private String createdAt;
}
