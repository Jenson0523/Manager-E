package com.tengyei;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression: a @PreAuthorize denial must return a graceful HTTP 200 + code 403
 * ("无权限访问"), NOT an HTTP 500 ("网络错误"). Bug was GlobalExceptionHandler.handleGeneral
 * swallowing Spring Security's AccessDeniedException as a 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessDeniedTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;

    @Test
    void companyAdmin_callingPlatformEndpoint_isDeniedGracefully() throws Exception {
        // company admin has company-tier perms only, NOT platform:user:view
        OrgTestSupport.Seeded seeded = OrgTestSupport.seedCompanyAdmin(jdbc);
        String token = OrgTestSupport.login(mockMvc, om, seeded.username());

        mockMvc.perform(get("/api/v1/platform/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())          // HTTP 200 (graceful envelope), not 500
                .andExpect(jsonPath("$.code").value(403));
    }
}
