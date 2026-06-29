package com.tengyei.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class AuditLogExportVO {
    @ExcelProperty("操作人")
    private String userName;

    @ExcelProperty("模块")
    private String module;

    @ExcelProperty("操作类型")
    private String actionType;

    @ExcelProperty("操作描述")
    private String description;

    @ExcelProperty("IP地址")
    private String ipAddress;

    @ExcelProperty("结果")
    private String result;

    @ExcelProperty("错误信息")
    private String errorMsg;

    @ExcelProperty("时间")
    private String createdAt;
}
