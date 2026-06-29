package com.tengyei.company.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tengyei.common.exception.BusinessException;
import com.tengyei.common.response.PageResult;
import com.tengyei.company.dto.CompanyCreateDTO;
import com.tengyei.company.dto.CompanyUpdateDTO;
import com.tengyei.company.dto.CompanyVO;
import com.tengyei.company.entity.Company;
import com.tengyei.company.mapper.CompanyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyMapper companyMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public PageResult<CompanyVO> page(long page, long size, String keyword) {
        LambdaQueryWrapper<Company> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.like(Company::getFullName, keyword).or().like(Company::getShortName, keyword);
        }
        qw.orderByDesc(Company::getId);
        Page<Company> result = companyMapper.selectPage(new Page<>(page, size), qw);
        return PageResult.from(result, CompanyVO::from);
    }

    public CompanyVO detail(Long id) {
        Company c = companyMapper.selectById(id);
        if (c == null) throw new BusinessException(404, "企业不存在");
        return CompanyVO.from(c);
    }

    @Transactional
    public Long create(CompanyCreateDTO dto) {
        Long existing = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM `user` WHERE username = ?", Long.class, dto.getAdminUsername());
        if (existing != null && existing > 0) {
            throw new BusinessException(409, "管理员账号已存在");
        }

        Company c = new Company();
        c.setFullName(dto.getFullName());
        c.setShortName(dto.getShortName());
        c.setCreditCode(dto.getCreditCode());
        c.setAdminName(dto.getAdminName());
        c.setAdminPhone(dto.getAdminPhone());
        c.setAdminEmail(dto.getAdminEmail());
        c.setStatus(1); // 创建即启用
        c.setCompanyNo("PENDING");
        companyMapper.insert(c);

        Long companyId = c.getId();
        c.setCompanyNo("E" + String.format("%06d", companyId));
        companyMapper.updateById(c);

        String encoded = passwordEncoder.encode(dto.getAdminPassword());
        jdbcTemplate.update(
            "INSERT INTO `user` (tenant_id, user_no, username, password, real_name, phone, email, " +
            "is_super_admin, status, pwd_reset_required, is_deleted, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, 0, 1, 1, 0, NOW(), NOW())",
            companyId, "U" + companyId + "-0001", dto.getAdminUsername(), encoded,
            dto.getAdminName(), dto.getAdminPhone(), dto.getAdminEmail());

        return companyId;
    }

    public void update(Long id, CompanyUpdateDTO dto) {
        Company c = companyMapper.selectById(id);
        if (c == null) throw new BusinessException(404, "企业不存在");
        c.setFullName(dto.getFullName());
        c.setShortName(dto.getShortName());
        c.setCreditCode(dto.getCreditCode());
        c.setAdminName(dto.getAdminName());
        c.setAdminPhone(dto.getAdminPhone());
        c.setAdminEmail(dto.getAdminEmail());
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
    }
}
