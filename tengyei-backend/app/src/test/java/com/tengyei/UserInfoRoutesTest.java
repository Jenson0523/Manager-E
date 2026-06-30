package com.tengyei;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression: a company admin with company-tier view permissions must get the
 * org/users/roles menu items from /userinfo. Bug was buildCompanyRoutes checking
 * "PERM_xxx" prefixed codes against the raw permission claim ("xxx").
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserInfoRoutesTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;

    @Test
    void companyAdmin_userinfo_includesOrgUsersRolesMenus() throws Exception {
        OrgTestSupport.Seeded seeded = OrgTestSupport.seedCompanyAdmin(jdbc);
        String token = OrgTestSupport.login(mockMvc, om, seeded.username());

        mockMvc.perform(get("/api/v1/auth/userinfo").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routes[*].path", hasItem("/company/users")))
                .andExpect(jsonPath("$.data.routes[*].path", hasItem("/company/org")))
                .andExpect(jsonPath("$.data.routes[*].path", hasItem("/company/roles")));
    }
}
