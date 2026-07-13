package com.tengyei.org.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class ApprovalExportVO {
    @ExcelProperty("单号")
    private String instanceNo;
    @ExcelProperty("表单类型")
    private String formName;
    @ExcelProperty("申请人")
    private String applicantName;
    @ExcelProperty("状态")
    private String status;
    @ExcelProperty("当前节点")
    private String currentNode;
    @ExcelProperty("申请时间")
    private String createdAt;
    @ExcelProperty("完结时间")
    private String finishedAt;
    @ExcelProperty("表单数据")
    private String formData;
}
