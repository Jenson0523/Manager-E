package com.tengyei;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 企业自改 logo:企业管理员可改自己(company:info:edit 回填),平台用户 422 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class CompanyLogoTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void companyAdminUpdatesOwnLogo() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbcTemplate);
        String companyToken = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());
        String platformToken = OrgTestSupport.login(mockMvc, objectMapper, "superadmin");

        mockMvc.perform(put("/api/v1/companies/my/logo")
                .header("Authorization", "Bearer " + companyToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"logoUrl\":\"/uploads/logo/x.png\"}"))
                .andExpect(jsonPath("$.code").value(0));
        assertEquals("/uploads/logo/x.png", jdbcTemplate.queryForObject(
                "SELECT logo_url FROM company WHERE id = ?", String.class, seeded.tenantId()));

        // 平台用户不属于任何企业 -> 422
        mockMvc.perform(put("/api/v1/companies/my/logo")
                .header("Authorization", "Bearer " + platformToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"logoUrl\":\"/uploads/logo/y.png\"}"))
                .andExpect(jsonPath("$.code").value(422));
    }
}
