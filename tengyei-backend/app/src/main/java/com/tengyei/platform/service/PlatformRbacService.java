package com.tengyei.platform.service;

import com.tengyei.common.exception.BusinessException;
import com.tengyei.common.validation.PasswordRule;
import com.tengyei.platform.dto.PlatformRoleDTO;
import com.tengyei.platform.dto.PlatformRoleVO;
import com.tengyei.platform.dto.PlatformUserDTO;
import com.tengyei.platform.dto.PlatformUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlatformRbacService {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    /* ===================== 平台角色 ===================== */

    public List<PlatformRoleVO> listRoles() {
        return jdbc.query(
            "SELECT id, name, code, description, is_preset, status FROM role " +
            "WHERE tenant_id = 0 AND is_deleted = 0 ORDER BY id",
            (rs, i) -> PlatformRoleVO.builder()
                .id(rs.getLong("id")).name(rs.getString("name")).code(rs.getString("code"))
                .description(rs.getString("description")).isPreset(rs.getInt("is_preset"))
                .status(rs.getInt("status")).build());
    }

    public Long createRole(PlatformRoleDTO dto) {
        Long dup = jdbc.queryForObject(
            "SELECT COUNT(*) FROM role WHERE tenant_id = 0 AND code = ?", Long.class, dto.getCode());
        if (dup != null && dup > 0) throw new BusinessException(409, "角色编码已存在");
        jdbc.update(
            "INSERT INTO role (tenant_id, name, code, description, data_scope, is_preset, status, " +
            "is_deleted, created_at, updated_at) VALUES (0, ?, ?, ?, 'all', 0, 1, 0, NOW(), NOW())",
            dto.getName(), dto.getCode(), dto.getDescription());
        return jdbc.queryForObject(
            "SELECT id FROM role WHERE tenant_id = 0 AND code = ?", Long.class, dto.getCode());
    }

    public void updateRole(Long id, PlatformRoleDTO dto) {
        requirePlatformRole(id);
        jdbc.update("UPDATE role SET name = ?, description = ? WHERE id = ? AND tenant_id = 0",
            dto.getName(), dto.getDescription(), id);
    }

    public void deleteRole(Long id) {
        String code = requirePlatformRole(id);
        if ("super_admin".equals(code)) throw new BusinessException(409, "内置角色不可删除");
        Long inUse = jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_role WHERE role_id = ?", Long.class, id);
        if (inUse != null && inUse > 0) throw new BusinessException(409, "角色已分配账号，无法删除");
        jdbc.update("UPDATE role SET is_deleted = 1 WHERE id = ? AND tenant_id = 0", id);
    }

    public List<Long> rolePermissionIds(Long roleId) {
        requirePlatformRole(roleId);
        return jdbc.queryForList(
            "SELECT permission_id FROM role_permission WHERE role_id = ?", Long.class, roleId);
    }

    @Transactional
    public void assignRolePermissions(Long roleId, List<Long> permissionIds) {
        String code = requirePlatformRole(roleId);
        if ("super_admin".equals(code)) throw new BusinessException(409, "内置角色权限不可修改");
        jdbc.update("DELETE FROM role_permission WHERE role_id = ?", roleId);
        if (permissionIds != null) {
            for (Long pid : permissionIds) {
                Long ok = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM permission WHERE id = ? AND tier = 'platform'", Long.class, pid);
                if (ok != null && ok > 0) {
                    jdbc.update("INSERT INTO role_permission (role_id, permission_id, created_at) VALUES (?, ?, NOW())",
                        roleId, pid);
                }
            }
        }
    }

    /** 校验角色存在且属平台层，返回其 code */
    private String requirePlatformRole(Long id) {
        List<String> codes = jdbc.queryForList(
            "SELECT code FROM role WHERE id = ? AND tenant_id = 0 AND is_deleted = 0", String.class, id);
        if (codes.isEmpty()) throw new BusinessException(404, "平台角色不存在");
        return codes.get(0);
    }

    /* ===================== 平台账号 ===================== */

    /** 服务端分页 + 角色整页批量查询(替代原全量返回+每行2次查询) */
    public com.tengyei.common.response.PageResult<PlatformUserVO> listUsers(String keyword, long page, long size) {
        StringBuilder where = new StringBuilder("WHERE tenant_id = 0 AND is_deleted = 0");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (username LIKE ? OR real_name LIKE ? OR phone LIKE ?)");
            String like = "%" + keyword + "%";
            params.add(like); params.add(like); params.add(like);
        }
        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM `user` " + where, Long.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((page - 1) * size);
        List<PlatformUserVO> records = jdbc.query(
            "SELECT id, username, real_name, phone, email, status, is_super_admin FROM `user` " +
            where + " ORDER BY id LIMIT ? OFFSET ?",
            pageParams.toArray(), (rs, i) -> PlatformUserVO.builder()
                .id(rs.getLong("id")).username(rs.getString("username")).realName(rs.getString("real_name"))
                .phone(rs.getString("phone")).email(rs.getString("email")).status(rs.getInt("status"))
                .isSuperAdmin(rs.getInt("is_super_admin"))
                .roleIds(new ArrayList<>()).roleNames(new ArrayList<>()).build());

        if (!records.isEmpty()) {
            java.util.Map<Long, PlatformUserVO> byId = new java.util.HashMap<>();
            records.forEach(v -> byId.put(v.getId(), v));
            String uin = String.join(",", records.stream().map(v -> String.valueOf(v.getId())).toList());
            jdbc.query(
                "SELECT ur.user_id, r.id, r.name FROM role r JOIN user_role ur ON ur.role_id = r.id " +
                "WHERE ur.user_id IN (" + uin + ")",
                rs -> {
                    PlatformUserVO v = byId.get(rs.getLong(1));
                    if (v != null) {
                        v.getRoleIds().add(rs.getLong(2));
                        v.getRoleNames().add(rs.getString(3));
                    }
                });
        }
        return com.tengyei.common.response.PageResult.of(records, total != null ? total : 0, page, size);
    }

    @Transactional
    public Long createUser(PlatformUserDTO dto) {
        if (dto.getPassword() == null || dto.getPassword().isBlank())
            throw new BusinessException(422, "初始密码不能为空");
        Long dup = jdbc.queryForObject(
            "SELECT COUNT(*) FROM `user` WHERE username = ? AND is_deleted = 0", Long.class, dto.getUsername());
        if (dup != null && dup > 0) throw new BusinessException(409, "账号已存在");
        jdbc.update(
            "INSERT INTO `user` (tenant_id, user_no, username, password, real_name, phone, email, " +
            "is_super_admin, status, pwd_reset_required, login_fail_count, is_deleted, created_at, updated_at) " +
            "VALUES (0, ?, ?, ?, ?, ?, ?, 0, 1, 1, 0, 0, NOW(), NOW())",
            "P-" + System.nanoTime(), dto.getUsername(), passwordEncoder.encode(dto.getPassword()),
            dto.getRealName(), dto.getPhone(), dto.getEmail());
        Long userId = jdbc.queryForObject(
            "SELECT id FROM `user` WHERE username = ? AND is_deleted = 0", Long.class, dto.getUsername());
        replaceUserRoles(userId, dto.getRoleIds());
        return userId;
    }

    @Transactional
    public void updateUser(Long id, PlatformUserDTO dto) {
        requirePlatformUser(id);
        jdbc.update("UPDATE `user` SET real_name = ?, phone = ?, email = ? WHERE id = ? AND tenant_id = 0",
            dto.getRealName(), dto.getPhone(), dto.getEmail(), id);
        if (dto.getRoleIds() != null) replaceUserRoles(id, dto.getRoleIds());
    }

    public void deleteUser(Long id) {
        requireNonOwner(id, "内置账号不可删除");
        jdbc.update("UPDATE `user` SET is_deleted = 1 WHERE id = ? AND tenant_id = 0", id);
    }

    public void changeUserStatus(Long id, Integer status) {
        requireNonOwner(id, "内置账号不可停用");
        if (status == null || (status != 0 && status != 1)) throw new BusinessException(422, "状态值无效");
        jdbc.update("UPDATE `user` SET status = ? WHERE id = ? AND tenant_id = 0", status, id);
    }

    public void resetUserPassword(Long id, String password) {
        requirePlatformUser(id);
        if (!PasswordRule.isValid(password)) throw new BusinessException(422, PasswordRule.MESSAGE);
        jdbc.update("UPDATE `user` SET password = ?, pwd_changed_at = NOW() WHERE id = ? AND tenant_id = 0",
            passwordEncoder.encode(password), id);
    }

    @Transactional
    public void assignUserRoles(Long id, List<Long> roleIds) {
        requireNonOwner(id, "内置账号角色不可修改");
        replaceUserRoles(id, roleIds);
    }

    private void replaceUserRoles(Long userId, List<Long> roleIds) {
        jdbc.update("DELETE FROM user_role WHERE user_id = ?", userId);
        if (roleIds != null) {
            for (Long rid : roleIds) {
                Long ok = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM role WHERE id = ? AND tenant_id = 0 AND is_deleted = 0",
                    Long.class, rid);
                if (ok != null && ok > 0) {
                    jdbc.update("INSERT INTO user_role (user_id, role_id, created_at) VALUES (?, ?, NOW())",
                        userId, rid);
                }
            }
        }
    }

    private void requirePlatformUser(Long id) {
        Long c = jdbc.queryForObject(
            "SELECT COUNT(*) FROM `user` WHERE id = ? AND tenant_id = 0 AND is_deleted = 0", Long.class, id);
        if (c == null || c == 0) throw new BusinessException(404, "平台账号不存在");
    }

    private void requireNonOwner(Long id, String msg) {
        requirePlatformUser(id);
        Integer owner = jdbc.queryForObject(
            "SELECT is_super_admin FROM `user` WHERE id = ?", Integer.class, id);
        if (owner != null && owner == 1) throw new BusinessException(409, msg);
    }
}
