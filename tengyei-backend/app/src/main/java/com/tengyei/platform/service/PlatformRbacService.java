package com.tengyei.platform.service;

import com.tengyei.common.exception.BusinessException;
import com.tengyei.platform.dto.PlatformRoleDTO;
import com.tengyei.platform.dto.PlatformRoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
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
}
