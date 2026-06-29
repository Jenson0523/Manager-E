package com.tengyei.org.dto;

import com.tengyei.org.entity.Branch;
import lombok.Builder;
import lombok.Getter;

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
