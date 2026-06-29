package com.tengyei.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.exception.BusinessException;
import com.tengyei.common.response.PageResult;
import com.tengyei.org.dto.BranchSaveDTO;
import com.tengyei.org.dto.BranchVO;
import com.tengyei.org.entity.Branch;
import com.tengyei.org.mapper.BranchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchMapper branchMapper;

    public PageResult<BranchVO> page(long page, long size) {
        Page<Branch> result = branchMapper.selectPage(new Page<>(page, size),
            new LambdaQueryWrapper<Branch>().orderByDesc(Branch::getId));
        return PageResult.from(result, BranchVO::from);
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
