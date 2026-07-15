package com.tengyei.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.exception.BusinessException;
import com.tengyei.rbac.dto.RoleSaveDTO;
import com.tengyei.rbac.dto.RoleVO;
import com.tengyei.rbac.entity.Role;
import com.tengyei.rbac.mapper.RoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleMapper roleMapper;
    private final JdbcTemplate jdbcTemplate;

    public List<RoleVO> list() {
        return roleMapper.selectList(
                new LambdaQueryWrapper<Role>().orderByAsc(Role::getId))
            .stream().map(RoleVO::from).toList();
    }

    public Long create(RoleSaveDTO dto) {
        Long dup = roleMapper.selectCount(
            new LambdaQueryWrapper<Role>().eq(Role::getCode, dto.getCode()));
        if (dup != null && dup > 0) throw new BusinessException(409, "角色编码已存在");
        Role r = new Role();
        r.setTenantId(TenantContext.getTenantId());
        r.setName(dto.getName());
        r.setCode(dto.getCode());
        r.setDescription(dto.getDescription());
        r.setDataScope(dto.getDataScope() != null ? dto.getDataScope() : "self");
        r.setIsPreset(0);
        r.setStatus(1);
        roleMapper.insert(r);
        return r.getId();
    }

    /** 复制角色:名称加"副本"、编码加时间戳后缀,权限一并复制。新建相似角色时免去重配权限 */
    @Transactional
    public Long copy(Long id) {
        Role src = requireRole(id);
        Role r = new Role();
        r.setTenantId(src.getTenantId());
        r.setName(src.getName() + "副本");
        r.setCode(src.getCode() + "_copy_" + (System.currentTimeMillis() % 100000));
        r.setDescription(src.getDescription());
        r.setDataScope(src.getDataScope());
        r.setIsPreset(0);
        r.setStatus(1);
        roleMapper.insert(r);
        jdbcTemplate.update(
            "INSERT INTO role_permission (role_id, permission_id, created_at) " +
            "SELECT ?, permission_id, NOW() FROM role_permission WHERE role_id = ?",
            r.getId(), id);
        return r.getId();
    }

    public void update(Long id, RoleSaveDTO dto) {
        Role r = requireRole(id);
        r.setName(dto.getName());
        r.setDescription(dto.getDescription());
        r.setDataScope(dto.getDataScope());
        roleMapper.updateById(r);
    }

    public void delete(Long id) {
        Role r = requireRole(id);
        if (r.getIsPreset() != null && r.getIsPreset() == 1) {
            throw new BusinessException(409, "预设角色不可删除");
        }
        Long inUse = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_role WHERE role_id = ?", Long.class, id);
        if (inUse != null && inUse > 0) throw new BusinessException(409, "角色已分配用户，无法删除");
        roleMapper.deleteById(id);
    }

    public List<Long> permissionIds(Long roleId) {
        requireRole(roleId);
        return jdbcTemplate.queryForList(
            "SELECT permission_id FROM role_permission WHERE role_id = ?", Long.class, roleId);
    }

    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        Role r = requireRole(roleId);
        jdbcTemplate.update("DELETE FROM role_permission WHERE role_id = ?", roleId);
        if (permissionIds != null) {
            // 只允许挂本层级的权限:公司角色只能配 company 层权限。
            // 否则公司管理员可绕过界面直接调接口把平台权限ID塞进自己角色,垂直提权到平台侧
            String tier = (r.getTenantId() != null && r.getTenantId() == 0L) ? "platform" : "company";
            for (Long pid : permissionIds) {
                Long ok = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM permission WHERE id = ? AND tier = ? AND status = 1",
                    Long.class, pid, tier);
                if (ok != null && ok > 0) {
                    jdbcTemplate.update(
                        "INSERT INTO role_permission (role_id, permission_id, created_at) VALUES (?,?,NOW())",
                        roleId, pid);
                }
            }
        }
    }

    private Role requireRole(Long id) {
        Role r = roleMapper.selectById(id);
        if (r == null) throw new BusinessException(404, "角色不存在");
        return r;
    }
}
