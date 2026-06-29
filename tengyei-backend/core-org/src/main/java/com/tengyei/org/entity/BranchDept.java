package com.tengyei.org.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("branch_dept")
public class BranchDept {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long branchId;
    private Long deptId;
}
