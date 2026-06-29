package com.tengyei.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.exception.BusinessException;
import com.tengyei.common.response.PageResult;
import com.tengyei.org.dto.BranchSaveDTO;
import com.tengyei.org.dto.BranchVO;
import com.tengyei.org.entity.Branch;
import com.tengyei.org.entity.BranchDept;
import com.tengyei.org.entity.Dept;
import com.tengyei.org.mapper.BranchDeptMapper;
import com.tengyei.org.mapper.BranchMapper;
import com.tengyei.org.mapper.DeptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchMapper branchMapper;
    private final BranchDeptMapper branchDeptMapper;
    private final DeptMapper deptMapper;

    public PageResult<BranchVO> page(long page, long size) {
        LambdaQueryWrapper<Branch> qw = new LambdaQueryWrapper<>();

        // 租户隔离：非超管只能看本租户
        if (!TenantContext.isSuperAdmin()) {
            qw.eq(Branch::getTenantId, TenantContext.getTenantId());
        }

        // 数据级权限过滤
        String scope = TenantContext.getDataScope();
        if ("branch".equals(scope)) {
            Long branchId = TenantContext.getBranchId();
            if (branchId != null) {
                qw.eq(Branch::getId, branchId);
            } else {
                return PageResult.of(Collections.emptyList(), 0L, page, size);
            }
        } else if ("dept".equals(scope) || "self".equals(scope)) {
            return PageResult.of(Collections.emptyList(), 0L, page, size);
        }

        qw.orderByDesc(Branch::getId);
        Page<Branch> result = branchMapper.selectPage(new Page<>(page, size), qw);

        // 填充关联部门信息
        List<BranchVO> vos = result.getRecords().stream()
                .map(this::enrich)
                .collect(Collectors.toList());

        return PageResult.of(vos, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** 填充 BranchVO 的部门关联信息 */
    private BranchVO enrich(Branch b) {
        BranchVO vo = BranchVO.from(b);
        List<BranchDept> links = branchDeptMapper.selectList(
            new LambdaQueryWrapper<BranchDept>().eq(BranchDept::getBranchId, b.getId()));
        if (links.isEmpty()) {
            return BranchVO.builder()
                    .id(b.getId()).branchNo(b.getBranchNo()).name(b.getName())
                    .type(b.getType()).leaderId(b.getLeaderId()).phone(b.getPhone())
                    .city(b.getCity()).status(b.getStatus())
                    .deptIds(Collections.emptyList()).deptNames(Collections.emptyList())
                    .build();
        }
        List<Long> deptIds = links.stream().map(BranchDept::getDeptId).collect(Collectors.toList());
        List<Dept> depts = deptMapper.selectBatchIds(deptIds);
        List<String> deptNames = depts.stream().map(Dept::getName).collect(Collectors.toList());
        return BranchVO.builder()
                .id(b.getId()).branchNo(b.getBranchNo()).name(b.getName())
                .type(b.getType()).leaderId(b.getLeaderId()).phone(b.getPhone())
                .city(b.getCity()).status(b.getStatus())
                .deptIds(deptIds).deptNames(deptNames)
                .build();
    }

    /**
     * 获取分公司关联的部门ID列表
     */
    public List<Long> getDeptIds(Long branchId) {
        return branchDeptMapper.selectList(
            new LambdaQueryWrapper<BranchDept>().eq(BranchDept::getBranchId, branchId))
            .stream().map(BranchDept::getDeptId).collect(Collectors.toList());
    }

    /**
     * 批量关联部门到分公司
     */
    @Transactional
    public void linkDepts(Long branchId, List<Long> deptIds) {
        Branch branch = branchMapper.selectById(branchId);
        if (branch == null) throw new BusinessException(404, "分支机构不存在");
        Long tenantId = TenantContext.getTenantId();

        for (Long deptId : deptIds) {
            // 检查是否已关联
            Long count = branchDeptMapper.selectCount(
                new LambdaQueryWrapper<BranchDept>()
                    .eq(BranchDept::getBranchId, branchId)
                    .eq(BranchDept::getDeptId, deptId));
            if (count != null && count > 0) continue;

            BranchDept bd = new BranchDept();
            bd.setTenantId(tenantId);
            bd.setBranchId(branchId);
            bd.setDeptId(deptId);
            branchDeptMapper.insert(bd);
        }
    }

    /**
     * 解除分公司与部门的关联
     */
    public void unlinkDept(Long branchId, Long deptId) {
        branchDeptMapper.delete(
            new LambdaQueryWrapper<BranchDept>()
                .eq(BranchDept::getBranchId, branchId)
                .eq(BranchDept::getDeptId, deptId));
    }

    public Long create(BranchSaveDTO dto) {
        Branch b = new Branch();
        b.setTenantId(TenantContext.getTenantId());
        apply(b, dto);
        b.setStatus(1);
        branchMapper.insert(b);
        return b.getId();
    }

    public void update(Long id, BranchSaveDTO dto) {
        Branch b = branchMapper.selectById(id);
        if (b == null) throw new BusinessException(404, "分支机构不存在");
        apply(b, dto);
        branchMapper.updateById(b);
    }

    public void changeStatus(Long id, Integer status) {
        Branch b = branchMapper.selectById(id);
        if (b == null) throw new BusinessException(404, "分支机构不存在");
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(422, "状态值无效");
        }
        b.setStatus(status);
        branchMapper.updateById(b);
    }

    private void apply(Branch b, BranchSaveDTO dto) {
        b.setBranchNo(dto.getBranchNo());
        b.setName(dto.getName());
        b.setType(dto.getType() != null ? dto.getType() : "independent");
        b.setProvince(dto.getProvince());
        b.setCity(dto.getCity());
        b.setDistrict(dto.getDistrict());
        b.setAddress(dto.getAddress());
        b.setLeaderId(dto.getLeaderId());
        b.setPhone(dto.getPhone());
        b.setMaxUsers(dto.getMaxUsers());
    }
}
