package com.tengyei.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.exception.BusinessException;
import com.tengyei.org.dto.DeptSaveDTO;
import com.tengyei.org.dto.DeptTreeVO;
import com.tengyei.org.entity.Dept;
import com.tengyei.org.mapper.DeptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeptService {

    private final DeptMapper deptMapper;

    public List<DeptTreeVO> tree() {
        List<Dept> all = deptMapper.selectList(
            new LambdaQueryWrapper<Dept>().orderByAsc(Dept::getSortOrder).orderByAsc(Dept::getId));
        Map<Long, DeptTreeVO> map = new LinkedHashMap<>();
        for (Dept d : all) map.put(d.getId(), DeptTreeVO.from(d));
        List<DeptTreeVO> roots = new ArrayList<>();
        for (Dept d : all) {
            DeptTreeVO node = map.get(d.getId());
            Long pid = d.getParentId();
            if (pid == null || pid == 0L || !map.containsKey(pid)) {
                roots.add(node);
            } else {
                map.get(pid).getChildren().add(node);
            }
        }
        return roots;
    }

    public Long create(DeptSaveDTO dto) {
        Dept d = new Dept();
        d.setTenantId(TenantContext.getTenantId());
        d.setName(dto.getName());
        d.setCode(dto.getCode());
        d.setParentId(dto.getParentId() != null ? dto.getParentId() : 0L);
        d.setLeaderId(dto.getLeaderId());
        d.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        d.setStatus(1);
        deptMapper.insert(d);
        return d.getId();
    }

    public void update(Long id, DeptSaveDTO dto) {
        Dept d = deptMapper.selectById(id);
        if (d == null) throw new BusinessException(404, "部门不存在");
        d.setName(dto.getName());
        d.setCode(dto.getCode());
        d.setLeaderId(dto.getLeaderId());
        if (dto.getSortOrder() != null) d.setSortOrder(dto.getSortOrder());
        deptMapper.updateById(d);
    }

    public void delete(Long id) {
        Long childCount = deptMapper.selectCount(
            new LambdaQueryWrapper<Dept>().eq(Dept::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException(409, "存在子部门，无法删除");
        }
        deptMapper.deleteById(id);
    }
}
