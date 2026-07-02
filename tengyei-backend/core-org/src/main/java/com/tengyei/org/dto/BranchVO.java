package com.tengyei.org.dto;

import com.tengyei.org.entity.Branch;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BranchVO {
    private Long id;
    private String branchNo;
    private String name;
    private String type;
    private Long leaderId;
    private String phone;
    private String city;
    private Integer status;
    /** 关联的部门ID */
    private Long deptId;
    /** 关联的部门名称 */
    private String deptName;
    /** 关联的部门ID列表（兼容旧字段） */
    private List<Long> deptIds;
    /** 关联的部门名称列表（兼容旧字段） */
    private List<String> deptNames;

    public static BranchVO from(Branch b) {
        return BranchVO.builder()
                .id(b.getId())
                .branchNo(b.getBranchNo())
                .name(b.getName())
                .type(b.getType())
                .leaderId(b.getLeaderId())
                .phone(b.getPhone())
                .city(b.getCity())
                .status(b.getStatus())
                .build();
    }
}
