package com.tengyei.company.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tengyei.common.exception.BusinessException;
import com.tengyei.common.response.PageResult;
import com.tengyei.common.service.CompanyBlockService;
import com.tengyei.company.dto.CompanyCreateDTO;
import com.tengyei.company.dto.CompanyUpdateDTO;
import com.tengyei.company.dto.CompanyVO;
import com.tengyei.company.entity.Company;
import java.util.stream.Collectors;
import com.tengyei.company.mapper.CompanyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyMapper companyMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final java.util.Optional<CompanyBlockService> companyBlockService;

    public PageResult<CompanyVO> page(long page, long size, String keyword) {
        LambdaQueryWrapper<Company> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.like(Company::getFullName, keyword).or().like(Company::getShortName, keyword);
        }
        qw.orderByDesc(Company::getId);
        Page<Company> result = companyMapper.selectPage(new Page<>(page, size), qw);
        // Populate admin username
        var vos = result.getRecords().stream().map(c -> {
            CompanyVO vo = CompanyVO.from(c);
            try {
                String sql = "SELECT username FROM `user` WHERE tenant_id = ? AND is_super_admin = 0 AND is_deleted = 0 LIMIT 1";
                String username = jdbcTemplate.queryForObject(sql, String.class, c.getId());
                vo.setAdminUsername(username);
            } catch (Exception e) {
                log.debug("No admin user found for company {}", c.getId());
            }
            return vo;
        }).collect(Collectors.toList());
        return PageResult.of(vos, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public CompanyVO detail(Long id) {
        Company c = companyMapper.selectById(id);
        if (c == null) throw new BusinessException(404, "企业不存在");
        return CompanyVO.from(c);
    }

    @Transactional
    public Long create(CompanyCreateDTO dto) {
        // 1. Check username uniqueness (via raw JDBC to bypass MyBatis tenant filter)
        Long existing = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM `user` WHERE username = ? AND is_deleted = 0",
            Long.class, dto.getAdminUsername());
        if (existing != null && existing > 0) {
            throw new BusinessException(409, "管理员账号已存在");
        }

        // 2. Insert company — use UUID as temp company_no to avoid UNIQUE conflicts
        Company c = new Company();
        c.setFullName(dto.getFullName());
        c.setShortName(dto.getShortName());
        c.setCreditCode(dto.getCreditCode());
        c.setExpireDate(dto.getExpireDate());
        c.setAdminName(dto.getAdminName());
        c.setAdminPhone(dto.getAdminPhone());
        c.setAdminEmail(dto.getAdminEmail());
        c.setStatus(1);
        c.setCompanyNo("T-" + UUID.randomUUID().toString().substring(0, 8));
        log.info("Creating company: fullName={}", dto.getFullName());
        companyMapper.insert(c);

        Long companyId = c.getId();
        log.info("Company inserted with id={}, updating company_no", companyId);
        c.setCompanyNo("E" + String.format("%06d", companyId));
        companyMapper.updateById(c);

        // 3. Insert admin user via JdbcTemplate (bypasses MyBatis tenant interceptor)
        String encoded = passwordEncoder.encode(dto.getAdminPassword());
        log.info("Inserting admin user for company id={}", companyId);
        jdbcTemplate.update(
            "INSERT INTO `user` (tenant_id, user_no, username, password, real_name, phone, email, " +
            "is_super_admin, status, pwd_reset_required, login_fail_count, is_deleted, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, 0, 1, 1, 0, 0, NOW(), NOW())",
            companyId,
            "U" + companyId + "-0001",
            dto.getAdminUsername(),
            encoded,
            dto.getAdminName(),
            dto.getAdminPhone(),
            dto.getAdminEmail());

        // 4. 取刚插入的管理员 userId
        Long adminUserId = jdbcTemplate.queryForObject(
            "SELECT id FROM `user` WHERE username = ? AND is_deleted = 0",
            Long.class, dto.getAdminUsername());

        // 5. 建预设 company_admin 角色（query-back 取 id，兼容 H2/MySQL）
        jdbcTemplate.update(
            "INSERT INTO role (tenant_id, name, code, data_scope, is_preset, status, " +
            "is_deleted, created_at, updated_at) VALUES (?, '企业管理员', 'company_admin', 'all', 1, 1, 0, NOW(), NOW())",
            companyId);
        Long roleId = jdbcTemplate.queryForObject(
            "SELECT id FROM role WHERE tenant_id = ? AND code = 'company_admin'",
            Long.class, companyId);

        // 6. 挂接管理员到 company_admin 角色
        jdbcTemplate.update(
            "INSERT INTO user_role (user_id, role_id, created_at) VALUES (?, ?, NOW())",
            adminUserId, roleId);

        // 7. 授予全部 company 层权限
        jdbcTemplate.update(
            "INSERT INTO role_permission (role_id, permission_id, created_at) " +
            "SELECT ?, id, NOW() FROM permission WHERE tier = 'company'", roleId);

        log.info("Company creation completed: id={}, no=E{}", companyId, String.format("%06d", companyId));
        return companyId;
    }

    public void update(Long id, CompanyUpdateDTO dto) {
        Company c = companyMapper.selectById(id);
        if (c == null) throw new BusinessException(404, "企业不存在");
        c.setFullName(dto.getFullName());
        c.setShortName(dto.getShortName());
        c.setCreditCode(dto.getCreditCode());
        c.setExpireDate(dto.getExpireDate());
        c.setAdminName(dto.getAdminName());
        c.setAdminPhone(dto.getAdminPhone());
        c.setAdminEmail(dto.getAdminEmail());
        c.setLogoUrl(dto.getLogoUrl());
        c.setExpireDate(dto.getExpireDate());
        c.setRemark(dto.getRemark());
        companyMapper.updateById(c);
    }

    public void changeStatus(Long id, Integer status) {
        Company c = companyMapper.selectById(id);
        if (c == null) throw new BusinessException(404, "企业不存在");
        if (status == null || (status != 1 && status != 2)) {
            throw new BusinessException(422, "状态值无效");
        }
        c.setStatus(status);
        companyMapper.updateById(c);
        if (status == 2) {
            companyBlockService.ifPresent(svc -> svc.block(id));
        } else {
            companyBlockService.ifPresent(svc -> svc.unblock(id));
        }
    }

    public void delete(Long id) {
        Company c = companyMapper.selectById(id);
        if (c == null) throw new BusinessException(404, "企业不存在");
        if (c.getStatus() == 1) throw new BusinessException(422, "请先停用企业再删除");
        // 逻辑删除
        companyMapper.deleteById(id);
    }

    /** 企业自改本公司 logo,只能改自己所在租户 */
    public void updateMyLogo(String logoUrl) {
        Long tenantId = com.tengyei.common.context.TenantContext.getTenantId();
        if (tenantId == null || tenantId == 0L) throw new BusinessException(422, "仅企业用户可操作");
        if (!StringUtils.hasText(logoUrl)) throw new BusinessException(422, "logo地址不能为空");
        jdbcTemplate.update("UPDATE company SET logo_url = ?, updated_at = NOW() WHERE id = ?", logoUrl, tenantId);
    }

    public void resetAdminPassword(Long companyId, String newPassword) {
        Company c = companyMapper.selectById(companyId);
        if (c == null) throw new BusinessException(404, "企业不存在");
        if (!StringUtils.hasText(newPassword)) throw new BusinessException(422, "新密码不能为空");
        if (newPassword.length() < 8 || newPassword.length() > 20)
            throw new BusinessException(422, "密码须为 8-20 位");
        // Find the admin user by tenant_id = companyId (first non-super-admin, non-deleted user)
        Long adminUserId = null;
        try {
            adminUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM `user` WHERE tenant_id = ? AND is_super_admin = 0 AND is_deleted = 0 LIMIT 1",
                Long.class, companyId);
        } catch (Exception e) {
            // fall through
        }
        if (adminUserId == null) throw new BusinessException(404, "未找到该企业的管理员账号");
        String encoded = passwordEncoder.encode(newPassword);
        jdbcTemplate.update(
            "UPDATE `user` SET password = ?, pwd_reset_required = 1, login_fail_count = 0, locked_until = NULL, pwd_changed_at = NOW() " +
            "WHERE id = ?",
            encoded, adminUserId);
        log.info("Admin password reset for company {} user {}", companyId, adminUserId);
    }
}


