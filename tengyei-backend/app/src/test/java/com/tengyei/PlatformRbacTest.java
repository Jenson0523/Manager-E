package com.tengyei;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformRbacTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;

    private String ownerToken() throws Exception {
        return OrgTestSupport.login(mockMvc, om, "superadmin");
    }

    @Test
    void platformUserList_returnsOnlyTenantZero() throws Exception {
        OrgTestSupport.seedCompanyAdmin(jdbc); // creates a company admin at tenant != 0
        String token = ownerToken();
        mockMvc.perform(get("/api/v1/platform/users").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.realName=='企业管理员')]").isEmpty());
    }

    @Test
    void cannotDeleteOwnerAccount() throws Exception {
        String token = ownerToken();
        Long ownerId = jdbc.queryForObject(
            "SELECT id FROM user WHERE username = 'superadmin'", Long.class);
        // BusinessException is returned as HTTP 200 with non-zero body code (see GlobalExceptionHandler)
        mockMvc.perform(delete("/api/v1/platform/users/" + ownerId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(409));
    }
}
