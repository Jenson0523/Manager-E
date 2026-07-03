package com.tengyei;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class CompanyAdminSeedTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired com.tengyei.company.service.CompanyService companyService;

    @Test
    void createCompany_seedsCompanyAdminRoleWithAllCompanyPermissions() {
        com.tengyei.company.dto.CompanyCreateDTO dto = new com.tengyei.company.dto.CompanyCreateDTO();
        dto.setFullName("种子测试企业");
        dto.setShortName("种子");
        dto.setAdminName("管理员");
        dto.setAdminPhone("13800000001");
        dto.setAdminUsername("seed_admin_" + System.nanoTime());
        dto.setAdminPassword("Admin@2026");
        Long companyId = companyService.create(dto);

        Long roleCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM role WHERE tenant_id = ? AND code = 'company_admin'",
            Long.class, companyId);
        assertEquals(1L, roleCount, "应自动建 company_admin 角色");

        Long permCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM role_permission rp JOIN role r ON r.id = rp.role_id " +
            "WHERE r.tenant_id = ? AND r.code = 'company_admin'", Long.class, companyId);
        Long companyPerms = jdbc.queryForObject(
            "SELECT COUNT(*) FROM permission WHERE tier = 'company'", Long.class);
        assertEquals(companyPerms, permCount, "company_admin 应拥有全部公司权限");

        Long urCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_role ur JOIN `user` u ON u.id = ur.user_id " +
            "WHERE u.tenant_id = ? AND u.is_super_admin = 0", Long.class, companyId);
        assertEquals(1L, urCount, "管理员应挂到 company_admin 角色");
    }
}
