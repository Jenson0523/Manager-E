package com.tengyei.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.exception.BusinessException;
import com.tengyei.org.dto.DeptSaveDTO;
import com.tengyei.org.dto.DeptTreeVO;
import com.tengyei.org.entity.Dept;
import com.tengyei.org.mapper.DeptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeptService {

    private final DeptMapper deptMapper;
    private final JdbcTemplate jdbcTemplate;

    public List<DeptTreeVO> tree() {
        LambdaQueryWrapper<Dept> qw = new LambdaQueryWrapper<>();

        // 租户隔离：非超管只能看本租户
        if (!TenantContext.isSuperAdmin()) {
            qw.eq(Dept::getTenantId, TenantContext.getTenantId());
        }

        // 数据级权限过滤
        String scope = TenantContext.getDataScope();
        final Set<Long> allowedDeptIds;
        if ("branch".equals(scope)) {
            Long branchId = TenantContext.getBranchId();
            if (branchId != null) {
                allowedDeptIds = getBranchDeptIds(branchId);
                if (allowedDeptIds.isEmpty()) return Collections.emptyList();
            } else {
                return Collections.emptyList();
            }
        } else if ("dept".equals(scope)) {
            Set<Long> userDeptIds = getUserDeptIds();
            if (!userDeptIds.isEmpty()) {
                Set<Long> ids = new LinkedHashSet<>();
                for (Long did : userDeptIds) ids.addAll(collectSubDeptIds(did));
                allowedDeptIds = ids;
            } else {
                return Collections.emptyList();
            }
        } else if ("self".equals(scope)) {
            Set<Long> userDeptIds = getUserDeptIds();
            if (!userDeptIds.isEmpty()) {
                allowedDeptIds = userDeptIds;
            } else {
                return Collections.emptyList();
            }
        } else {
            allowedDeptIds = null;
        }

        List<Dept> all = deptMapper.selectList(
            qw.orderByAsc(Dept::getSortOrder).orderByAsc(Dept::getId));

        if (allowedDeptIds != null) {
            all = all.stream().filter(d -> allowedDeptIds.contains(d.getId())).collect(Collectors.toList());
        }

        Map<Long, DeptTreeVO> map = new LinkedHashMap<>();
        for (Dept d : all) map.put(d.getId(), DeptTreeVO.from(d));

        // 批量查询负责人名称
        Set<Long> leaderIds = all.stream().map(Dept::getLeaderId).filter(id -> id != null && id > 0).collect(Collectors.toSet());
        if (!leaderIds.isEmpty()) {
            String inClause = leaderIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, real_name as realName FROM `user` WHERE id IN (" + inClause + ")");
            Map<Long, String> leaderMap = rows.stream().collect(
                Collectors.toMap(
                    r -> ((Number) r.get("id")).longValue(),
                    r -> (String) r.get("realName"),
                    (a, b) -> a
                ));
            for (DeptTreeVO vo : map.values()) {
                if (vo.getLeaderId() != null) {
                    vo.setLeaderName(leaderMap.get(vo.getLeaderId()));
                }
            }
        }

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

    /**
     * 获取当前用户关联的全部部门ID（与人员数据范围口径保持一致）
     */
    private Set<Long> getUserDeptIds() {
        Long userId = TenantContext.getUserId();
        if (userId == null) return Collections.emptySet();
        try {
            List<Long> ids = jdbcTemplate.queryForList(
                "SELECT dept_id FROM user_dept WHERE user_id = ?", Long.class, userId);
            return new LinkedHashSet<>(ids);
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    /**
     * 获取指定分公司下关联的全部部门ID
     */
    private Set<Long> getBranchDeptIds(Long branchId) {
        try {
            List<Long> ids = jdbcTemplate.queryForList(
                "SELECT dept_id FROM branch_dept WHERE branch_id = ?", Long.class, branchId);
            return new LinkedHashSet<>(ids);
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    /**
     * 递归收集部门及其所有子部门ID
     */
    private Set<Long> collectSubDeptIds(Long parentId) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(parentId);
        List<Dept> children = deptMapper.selectList(
            new LambdaQueryWrapper<Dept>().eq(Dept::getParentId, parentId));
        for (Dept child : children) {
            ids.addAll(collectSubDeptIds(child.getId()));
        }
        return ids;
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
