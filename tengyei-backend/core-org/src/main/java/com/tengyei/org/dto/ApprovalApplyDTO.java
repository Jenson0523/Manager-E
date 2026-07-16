package com.tengyei.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ApprovalApplyDTO {
    @NotBlank(message = "表单类型不能为空")
    private String formType;
    @NotNull(message = "表单数据不能为空")
    private Map<String, Object> formData;
    /** 抄送人(可空):知会即可,不占审批环节 */
    private List<Long> ccUserIds;
    /** 发起部门(可空):多部门员工选择以哪个部门身份提交,决定部门负责人是谁;单部门可不传 */
    private Long deptId;
}
