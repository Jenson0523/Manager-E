package com.tengyei.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tengyei.common.context.TenantContext;
import com.tengyei.common.exception.BusinessException;
import com.tengyei.common.response.PageResult;
import com.tengyei.org.dto.UserCreateDTO;
import com.tengyei.org.dto.UserUpdateDTO;
import com.tengyei.org.dto.UserVO;
import com.tengyei.org.entity.User;
import com.tengyei.org.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public PageResult<UserVO> page(long page, long size, String keyword, Long deptId, Long roleId) {
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        qw.eq(User::getIsSuperAdmin, 0);
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
            "SELECT COUNT(*) FROM user WHERE username = ?", Long.class, dto.getUsername());
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
        jdbcTemplate.update("UPDATE user SET locked_until = NULL WHERE id = ?", id);
    }

    private User requireUser(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw new BusinessException(404, "用户不存在");
        return u;
    }
}
