package com.tengyei.org.dto;

import com.tengyei.org.entity.Dept;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class DeptTreeVO {
    private Long id;
    private String name;
    private String code;
    private Long parentId;
    private Long leaderId;
    private Integer sortOrder;
    private Integer status;
    private List<DeptTreeVO> children = new ArrayList<>();

    public static DeptTreeVO from(Dept d) {
        DeptTreeVO vo = new DeptTreeVO();
        vo.setId(d.getId());
        vo.setName(d.getName());
        vo.setCode(d.getCode());
        vo.setParentId(d.getParentId());
        vo.setLeaderId(d.getLeaderId());
        vo.setSortOrder(d.getSortOrder());
        vo.setStatus(d.getStatus());
        return vo;
    }
}
