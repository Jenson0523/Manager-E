package com.tengyei.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.exception.BusinessException;
import com.tengyei.common.response.PageResult;
import com.tengyei.org.dto.UserCreateDTO;
import com.tengyei.org.dto.UserExportVO;
import com.tengyei.org.dto.UserUpdateDTO;
import com.tengyei.org.dto.UserVO;
import com.tengyei.org.entity.Dept;
import com.tengyei.org.entity.User;
import com.tengyei.org.mapper.DeptMapper;
import com.tengyei.org.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final DeptMapper deptMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public PageResult<UserVO> page(long page, long size, String keyword, Long deptId, Long roleId) {
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        qw.eq(User::getIsSuperAdmin, 0);

        // 租户隔离：非超管只能看本租户
        if (!TenantContext.isSuperAdmin()) {
            qw.eq(User::getTenantId, TenantContext.getTenantId());
        }

        // 数据级权限过滤
        String scope = TenantContext.getDataScope();
        if ("branch".equals(scope)) {
            Long branchId = TenantContext.getBranchId();
            if (branchId != null) {
                qw.eq(User::getBranchId, branchId);
            }
        } else if ("dept".equals(scope)) {
            Long userDeptId = getUserDeptId();
            if (userDeptId != null) {
                Set<Long> deptIds = collectSubDeptIds(userDeptId);
                qw.in(User::getDeptId, deptIds);
            }
        } else if ("self".equals(scope)) {
            qw.eq(User::getId, TenantContext.getUserId());
        }

        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getRealName, keyword)
                    .or().like(User::getPhone, keyword));
        }
        if (deptId != null) qw.eq(User::getDeptId, deptId);
        qw.orderByDesc(User::getId);
        Page<User> result = userMapper.selectPage(new Page<>(page, size), qw);

        List<UserVO> vos = new ArrayList<>();
        for (User u : result.getRecords()) {
            List<Long> roleIds = jdbcTemplate.queryForList(
                "SELECT role_id FROM user_role WHERE user_id = ?", Long.class, u.getId());
            List<String> roleNames = roleIds.isEmpty() ? List.of() :
                jdbcTemplate.queryForList(
                    "SELECT name FROM role WHERE id IN (" +
                    String.join(",", roleIds.stream().map(String::valueOf).toList()) + ")",
                    String.class);
            if (roleId != null && !roleIds.contains(roleId)) continue;
            vos.add(UserVO.builder()
                    .id(u.getId()).username(u.getUsername()).realName(u.getRealName())
                    .phone(u.getPhone()).email(u.getEmail()).deptId(u.getDeptId())
                    .branchId(u.getBranchId()).status(u.getStatus())
                    .roleIds(roleIds).roleNames(roleNames).build());
        }
        return PageResult.of(vos, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Transactional
    public Long create(UserCreateDTO dto) {
        Long global = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM `user` WHERE username = ?", Long.class, dto.getUsername());
        if (global != null && global > 0) {
            throw new BusinessException(409, "用户名已存在");
        }
        Long tenantId = TenantContext.getTenantId();
        User u = new User();
        u.setTenantId(tenantId);
        u.setUsername(dto.getUsername());
        u.setPassword(passwordEncoder.encode(dto.getPassword()));
        u.setRealName(dto.getRealName());
        u.setPhone(dto.getPhone());
        u.setEmail(dto.getEmail());
        u.setDeptId(dto.getDeptId());
        u.setBranchId(dto.getBranchId());
        u.setIsSuperAdmin(0);
        u.setStatus(1);
        u.setPwdResetRequired(1);
        u.setLoginFailCount(0);
        u.setUserNo("PENDING"); // temporary placeholder; NOT NULL constraint
        userMapper.insert(u);
        u.setUserNo("U" + tenantId + "-" + String.format("%04d", u.getId()));
        userMapper.updateById(u);
        assignRoles(u.getId(), dto.getRoleIds());
        return u.getId();
    }

    public void update(Long id, UserUpdateDTO dto) {
        User u = requireUser(id);
        u.setRealName(dto.getRealName());
        u.setPhone(dto.getPhone());
        u.setEmail(dto.getEmail());
        u.setDeptId(dto.getDeptId());
        u.setBranchId(dto.getBranchId());
        userMapper.updateById(u);
    }

    public List<UserExportVO> export(String keyword, Long deptId) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT u.id, u.username, u.real_name, u.phone, u.email, " +
            "u.status, u.created_at, d.name AS dept_name " +
            "FROM `user` u LEFT JOIN dept d ON d.id = u.dept_id " +
            "WHERE u.is_super_admin = 0 AND u.is_deleted = 0");

        if (!TenantContext.isSuperAdmin()) {
            sql.append(" AND u.tenant_id = ?");
            params.add(TenantContext.getTenantId());
        }

        // 数据级权限过滤
        String scope = TenantContext.getDataScope();
        if ("branch".equals(scope)) {
            Long branchId = TenantContext.getBranchId();
            if (branchId != null) {
                sql.append(" AND u.branch_id = ?");
                params.add(branchId);
            }
        } else if ("dept".equals(scope)) {
            Long userDeptId = getUserDeptId();
            if (userDeptId != null) {
                Set<Long> deptIds = collectSubDeptIds(userDeptId);
                String inClause = String.join(",", deptIds.stream().map(String::valueOf).toList());
                sql.append(" AND u.dept_id IN (").append(inClause).append(")");
            }
        } else if ("self".equals(scope)) {
            sql.append(" AND u.id = ?");
            params.add(TenantContext.getUserId());
        }

        if (StringUtils.hasText(keyword)) {
            sql.append(" AND (u.real_name LIKE ? OR u.username LIKE ? OR u.phone LIKE ?)");
            String kw = "%" + keyword + "%";
            params.add(kw); params.add(kw); params.add(kw);
        }
        if (deptId != null) {
            sql.append(" AND u.dept_id = ?");
            params.add(deptId);
        }
        sql.append(" ORDER BY u.id LIMIT 5000");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        return rows.stream().map(row -> {
            Long userId = ((Number) row.get("id")).longValue();
            List<String> roleNames = jdbcTemplate.queryForList(
                "SELECT r.name FROM role r JOIN user_role ur ON ur.role_id = r.id WHERE ur.user_id = ?",
                String.class, userId);

            UserExportVO vo = new UserExportVO();
            vo.setRealName((String) row.get("real_name"));
            vo.setUsername((String) row.get("username"));
            vo.setPhone((String) row.get("phone"));
            vo.setEmail((String) row.get("email"));
            vo.setDeptName((String) row.get("dept_name"));
            vo.setRoles(String.join(", ", roleNames));
            vo.setStatus(Integer.valueOf(1).equals(row.get("status")) ? "启用" : "停用");
            Object ca = row.get("created_at");
            vo.setCreatedAt(ca != null ? ca.toString() : "");
            return vo;
        }).toList();
    }

    public void changeStatus(Long id, Integer status) {
        User u = requireUser(id);
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(422, "状态值无效");
        }
        u.setStatus(status);
        userMapper.updateById(u);
    }

    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        requireUser(userId);
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id = ?", userId);
        if (roleIds != null) {
            for (Long rid : roleIds) {
                jdbcTemplate.update(
                    "INSERT INTO user_role (user_id, role_id, created_at) VALUES (?,?,NOW())",
                    userId, rid);
            }
        }
    }

    public void resetPassword(Long id, String newPassword) {
        User u = requireUser(id);
        if (!StringUtils.hasText(newPassword)) throw new BusinessException(422, "新密码不能为空");
        u.setPassword(passwordEncoder.encode(newPassword));
        u.setPwdResetRequired(1);
        u.setLoginFailCount(0);
        userMapper.updateById(u);
        // updateById ignores null fields, so clear locked_until explicitly
        jdbcTemplate.update("UPDATE `user` SET locked_until = NULL WHERE id = ?", id);
    }

    private User requireUser(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw new BusinessException(404, "用户不存在");
        return u;
    }

    /**
     * 获取当前用户的部门ID
     */
    private Long getUserDeptId() {
        Long userId = TenantContext.getUserId();
        if (userId == null) return null;
        User u = userMapper.selectById(userId);
        return u != null ? u.getDeptId() : null;
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
}
