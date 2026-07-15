package com.tengyei.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.exception.BusinessException;
import com.tengyei.common.response.PageResult;
import com.tengyei.common.validation.PasswordRule;
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
import java.util.stream.Collectors;

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

        // Tenant isolation
        if (!TenantContext.isSuperAdmin()) {
            qw.eq(User::getTenantId, TenantContext.getTenantId());
        }

        // Data scope filter
        String scope = TenantContext.getDataScope();
        if ("branch".equals(scope)) {
            Long branchId = TenantContext.getBranchId();
            if (branchId != null) {
                qw.eq(User::getBranchId, branchId);
            }
        } else if ("dept".equals(scope)) {
            Set<Long> userDeptIds = getCurrentUserDeptIds();
            if (!userDeptIds.isEmpty()) {
                Set<Long> allDeptIds = new LinkedHashSet<>();
                for (Long did : userDeptIds) {
                    allDeptIds.addAll(collectSubDeptIds(did));
                }
                if (deptId != null) {
                    // Filter by selected dept, but only within allowed scope
                    if (allDeptIds.contains(deptId)) {
                        qw.inSql(User::getId,
                            "SELECT user_id FROM user_dept WHERE dept_id = " + deptId);
                    } else {
                        return PageResult.of(List.of(), 0, page, size);
                    }
                } else {
                    String inClause = allDeptIds.stream().map(String::valueOf).collect(Collectors.joining(","));
                    qw.inSql(User::getId,
                        "SELECT user_id FROM user_dept WHERE dept_id IN (" + inClause + ")");
                }
            }
        } else if ("self".equals(scope)) {
            qw.eq(User::getId, TenantContext.getUserId());
        }

        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getRealName, keyword)
                    .or().like(User::getPhone, keyword));
        }

        // deptId filter for non-dept scope
        if (deptId != null && !"dept".equals(scope)) {
            qw.inSql(User::getId,
                "SELECT user_id FROM user_dept WHERE dept_id = " + deptId);
        }

        qw.orderByDesc(User::getId);
        Page<User> result = userMapper.selectPage(new Page<>(page, size), qw);

        List<UserVO> vos = new ArrayList<>();
        for (User u : result.getRecords()) {
            List<Long> roleIds = jdbcTemplate.queryForList(
                "SELECT role_id FROM user_role WHERE user_id = ?", Long.class, u.getId());
            if (roleId != null && !roleIds.contains(roleId)) continue;

            List<String> roleNames = roleIds.isEmpty() ? List.of() :
                jdbcTemplate.queryForList(
                    "SELECT name FROM role WHERE id IN (" +
                    String.join(",", roleIds.stream().map(String::valueOf).toList()) + ")",
                    String.class);

            List<Long> deptIds = jdbcTemplate.queryForList(
                "SELECT dept_id FROM user_dept WHERE user_id = ? ORDER BY is_primary DESC", Long.class, u.getId());
            List<String> deptNames = deptIds.isEmpty() ? List.of() :
                jdbcTemplate.queryForList(
                    "SELECT name FROM dept WHERE id IN (" +
                    String.join(",", deptIds.stream().map(String::valueOf).toList()) + ") AND is_deleted = 0",
                    String.class);

            vos.add(UserVO.builder()
                    .id(u.getId()).username(u.getUsername()).realName(u.getRealName())
                    .phone(maskPhone(u.getPhone())).email(u.getEmail()).deptId(u.getDeptId())
                    .deptIds(deptIds).deptNames(deptNames)
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
        checkQuota(tenantId, dto.getBranchId());
        User u = new User();
        u.setTenantId(tenantId);
        u.setUsername(dto.getUsername());
        u.setPassword(passwordEncoder.encode(dto.getPassword()));
        u.setRealName(dto.getRealName());
        u.setPhone(dto.getPhone());
        u.setEmail(dto.getEmail());

        Long primaryDeptId = resolvePrimaryDeptId(dto.getDeptId(), dto.getDeptIds());
        u.setDeptId(primaryDeptId);
        u.setBranchId(dto.getBranchId());
        u.setIsSuperAdmin(0);
        u.setStatus(1);
        u.setPwdResetRequired(1);
        u.setLoginFailCount(0);
        u.setUserNo("PENDING");
        userMapper.insert(u);
        u.setUserNo("U" + tenantId + "-" + String.format("%04d", u.getId()));
        userMapper.updateById(u);

        saveUserDepts(u.getId(), dto.getDeptIds(), dto.getDeptId());
        assignRoles(u.getId(), dto.getRoleIds());
        return u.getId();
    }

    public void update(Long id, UserUpdateDTO dto) {
        User u = requireUser(id);
        u.setRealName(dto.getRealName());
        u.setPhone(dto.getPhone());
        u.setEmail(dto.getEmail());
        Long primaryDeptId = resolvePrimaryDeptId(dto.getDeptId(), dto.getDeptIds());
        u.setDeptId(primaryDeptId);
        u.setBranchId(dto.getBranchId());
        userMapper.updateById(u);
        saveUserDepts(id, dto.getDeptIds(), dto.getDeptId());
    }

    /** §9.2 人员/分公司配额校验：max_users 为 NULL 表示不限 */
    private void checkQuota(Long tenantId, Long branchId) {
        Integer companyMax = jdbcTemplate.queryForObject(
            "SELECT max_users FROM company WHERE id = ?", Integer.class, tenantId);
        if (companyMax != null) {
            Long used = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `user` WHERE tenant_id = ? AND is_deleted = 0 AND is_super_admin = 0",
                Long.class, tenantId);
            if (used != null && used >= companyMax) {
                throw new BusinessException(409, "公司人员数已达上限（" + companyMax + "）");
            }
        }
        if (branchId != null) {
            Integer branchMax = jdbcTemplate.queryForObject(
                "SELECT max_users FROM branch WHERE id = ?", Integer.class, branchId);
            if (branchMax != null) {
                Long used = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `user` WHERE branch_id = ? AND is_deleted = 0 AND is_super_admin = 0",
                    Long.class, branchId);
                if (used != null && used >= branchMax) {
                    throw new BusinessException(409, "分公司人员数已达上限（" + branchMax + "）");
                }
            }
        }
    }

    /** 当前租户人员用量，前端用量条用 */
    public Map<String, Integer> quota() {
        Long tenantId = TenantContext.getTenantId();
        Integer max = jdbcTemplate.queryForObject(
            "SELECT max_users FROM company WHERE id = ?", Integer.class, tenantId);
        Long used = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM `user` WHERE tenant_id = ? AND is_deleted = 0 AND is_super_admin = 0",
            Long.class, tenantId);
        Map<String, Integer> result = new java.util.LinkedHashMap<>();
        result.put("used", used == null ? 0 : used.intValue());
        result.put("max", max);
        return result;
    }

    private Long resolvePrimaryDeptId(Long explicitDeptId, List<Long> deptIds) {
        if (explicitDeptId != null) return explicitDeptId;
        if (deptIds != null && !deptIds.isEmpty()) return deptIds.get(0);
        return null;
    }

    private void saveUserDepts(Long userId, List<Long> deptIds, Long primaryDeptId) {
        jdbcTemplate.update("DELETE FROM user_dept WHERE user_id = ?", userId);
        if (deptIds == null || deptIds.isEmpty()) {
            if (primaryDeptId != null) {
                jdbcTemplate.update(
                    "INSERT INTO user_dept (user_id, dept_id, is_primary, created_at) VALUES (?, ?, 1, NOW())",
                    userId, primaryDeptId);
            }
            return;
        }
        List<Long> distinct = new ArrayList<>(new LinkedHashSet<>(deptIds));
        Long primary = primaryDeptId != null && distinct.contains(primaryDeptId)
                ? primaryDeptId : distinct.get(0);
        for (Long did : distinct) {
            int isPrimary = did.equals(primary) ? 1 : 0;
            jdbcTemplate.update(
                "INSERT INTO user_dept (user_id, dept_id, is_primary, created_at) VALUES (?, ?, ?, NOW())",
                userId, did, isPrimary);
        }
    }

    public List<UserExportVO> export(String keyword, Long deptId) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT u.id, u.username, u.real_name, u.phone, u.email, " +
            "u.status, u.created_at, d.name AS dept_name " +
            "FROM `user` u LEFT JOIN dept d ON d.id = u.dept_id AND d.is_deleted = 0 " +
            "WHERE u.is_super_admin = 0 AND u.is_deleted = 0");

        if (!TenantContext.isSuperAdmin()) {
            sql.append(" AND u.tenant_id = ?");
            params.add(TenantContext.getTenantId());
        }

        String scope = TenantContext.getDataScope();
        if ("branch".equals(scope)) {
            Long branchId = TenantContext.getBranchId();
            if (branchId != null) {
                sql.append(" AND u.branch_id = ?");
                params.add(branchId);
            }
        } else if ("dept".equals(scope)) {
            Set<Long> userDeptIds = getCurrentUserDeptIds();
            if (!userDeptIds.isEmpty()) {
                Set<Long> allDeptIds = new LinkedHashSet<>();
                for (Long did : userDeptIds) {
                    allDeptIds.addAll(collectSubDeptIds(did));
                }
                String inClause = allDeptIds.stream().map(String::valueOf).collect(Collectors.joining(","));
                sql.append(" AND u.id IN (SELECT user_id FROM user_dept WHERE dept_id IN (")
                   .append(inClause).append("))");
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
            sql.append(" AND u.id IN (SELECT user_id FROM user_dept WHERE dept_id = ?)");
            params.add(deptId);
        }
        sql.append(" ORDER BY u.id LIMIT 5000");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        return rows.stream().map(row -> {
            Long userId = ((Number) row.get("id")).longValue();
            List<String> roleNames = jdbcTemplate.queryForList(
                "SELECT r.name FROM role r JOIN user_role ur ON ur.role_id = r.id WHERE ur.user_id = ?",
                String.class, userId);
            List<String> deptNames = jdbcTemplate.queryForList(
                "SELECT d.name FROM dept d JOIN user_dept ud ON ud.dept_id = d.id WHERE ud.user_id = ? AND d.is_deleted = 0",
                String.class, userId);

            UserExportVO vo = new UserExportVO();
            vo.setRealName((String) row.get("real_name"));
            vo.setUsername((String) row.get("username"));
            vo.setPhone(maskPhone((String) row.get("phone")));
            vo.setEmail((String) row.get("email"));
            vo.setDeptName((String) row.get("dept_name"));
            vo.setDeptNames(String.join(", ", deptNames));
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
    public void batchChangeStatus(List<Long> ids, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(422, "状态值无效");
        }
        List<Long> allowedIds = filterIdsInScope(ids);
        if (allowedIds.isEmpty()) return;
        String inClause = String.join(",", allowedIds.stream().map(String::valueOf).toList());
        if (!TenantContext.isSuperAdmin()) {
            Long tenantId = TenantContext.getTenantId();
            jdbcTemplate.update(
                "UPDATE `user` SET status = ? WHERE id IN (" + inClause + ") AND tenant_id = ? AND is_deleted = 0",
                status, tenantId);
        } else {
            jdbcTemplate.update(
                "UPDATE `user` SET status = ? WHERE id IN (" + inClause + ") AND is_deleted = 0",
                status);
        }
    }

    @Transactional
    public void batchAssignRoles(List<Long> ids, List<Long> roleIds) {
        // 复用单个分配逻辑,统一走"角色必须属于目标用户公司"的越权防护
        for (Long userId : filterIdsInScope(ids)) {
            assignRoles(userId, roleIds);
        }
    }

    /**
     * 过滤出当前操作者数据范围内、且属于本租户的用户ID，用于批量写操作的越权防护。
     */
    private List<Long> filterIdsInScope(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Long> result = new ArrayList<>();
        for (Long id : ids) {
            User u = userMapper.selectById(id);
            if (u == null) continue;
            try {
                assertUserInScope(u);
                result.add(id);
            } catch (BusinessException ignored) {
                // 越权目标静默跳过，不阻塞合法项
            }
        }
        return result;
    }

    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        User u = requireUser(userId);
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id = ?", userId);
        if (roleIds != null) {
            for (Long rid : roleIds) {
                // 只允许分配目标用户所属公司的角色。否则可绕过界面直接调接口
                // 把平台角色(tenant 0)或其他公司的角色挂给本公司用户,越权提权
                Long ok = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM role WHERE id = ? AND tenant_id = ? AND is_deleted = 0",
                    Long.class, rid, u.getTenantId());
                if (ok != null && ok > 0) {
                    jdbcTemplate.update(
                        "INSERT INTO user_role (user_id, role_id, created_at) VALUES (?,?,NOW())",
                        userId, rid);
                }
            }
        }
    }

    public void resetPassword(Long id, String newPassword) {
        User u = requireUser(id);
        if (!StringUtils.hasText(newPassword)) throw new BusinessException(422, "新密码不能为空");
        if (!PasswordRule.isValid(newPassword)) throw new BusinessException(422, PasswordRule.MESSAGE);
        u.setPassword(passwordEncoder.encode(newPassword));
        u.setPwdResetRequired(1);
        u.setLoginFailCount(0);
        userMapper.updateById(u);
        // 重置密码同时作废该用户所有旧 token(处置密码泄露时立即踢下线)
        jdbcTemplate.update("UPDATE `user` SET locked_until = NULL, pwd_changed_at = NOW() WHERE id = ?", id);
    }

    private User requireUser(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw new BusinessException(404, "用户不存在");
        assertUserInScope(u);
        return u;
    }

    /**
     * 数据范围写操作校验：确保目标用户落在当前操作者的可及范围内，防止越权写。
     * 与列表读取口径保持一致：all 放行；branch 限本分公司；dept 限本部门及子部门；self 限本人。
     */
    private void assertUserInScope(User target) {
        if (TenantContext.isSuperAdmin()) return;
        String scope = TenantContext.getDataScope();
        if (scope == null || "all".equals(scope)) return;

        Long currentUserId = TenantContext.getUserId();
        if ("self".equals(scope)) {
            if (!target.getId().equals(currentUserId)) {
                throw new BusinessException(403, "无权操作该范围外的用户");
            }
            return;
        }
        if ("branch".equals(scope)) {
            Long branchId = TenantContext.getBranchId();
            if (branchId == null || !branchId.equals(target.getBranchId())) {
                throw new BusinessException(403, "无权操作该范围外的用户");
            }
            return;
        }
        if ("dept".equals(scope)) {
            Set<Long> allowed = new LinkedHashSet<>();
            for (Long deptId : getCurrentUserDeptIds()) {
                allowed.addAll(collectSubDeptIds(deptId));
            }
            List<Long> targetDeptIds = jdbcTemplate.queryForList(
                "SELECT dept_id FROM user_dept WHERE user_id = ?", Long.class, target.getId());
            boolean hit = targetDeptIds.stream().anyMatch(allowed::contains);
            if (!hit && target.getId().equals(currentUserId)) hit = true;
            if (!hit) {
                throw new BusinessException(403, "无权操作该范围外的用户");
            }
        }
    }

    private Set<Long> getCurrentUserDeptIds() {
        Long userId = TenantContext.getUserId();
        if (userId == null) return Set.of();
        List<Long> ids = jdbcTemplate.queryForList(
            "SELECT dept_id FROM user_dept WHERE user_id = ?", Long.class, userId);
        return new LinkedHashSet<>(ids);
    }

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

    private String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
