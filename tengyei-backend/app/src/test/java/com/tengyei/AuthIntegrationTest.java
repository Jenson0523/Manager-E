package com.tengyei;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tengyei.auth.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class AuthIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void login_with_valid_credentials_returns_token() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("superadmin");
        req.setPassword("Admin@2026");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresIn").value(7200));
    }

    @Test
    void login_with_wrong_password_returns_401() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("superadmin");
        req.setPassword("wrongpassword");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void login_with_empty_username_returns_422() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("");
        req.setPassword("Admin@2026");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void userinfo_without_token_returns_401_in_body() throws Exception {
        mockMvc.perform(get("/api/v1/auth/userinfo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void account_lockout_after_5_failed_attempts() throws Exception {
        jdbcTemplate.update(
            "UPDATE user SET login_fail_count = 0, locked_until = NULL WHERE username = 'superadmin'");
        try {
            LoginRequest badReq = new LoginRequest();
            badReq.setUsername("superadmin");
            badReq.setPassword("wrongpassword");
            String badJson = objectMapper.writeValueAsString(badReq);

            for (int i = 1; i <= 4; i++) {
                mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badJson))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(401));
            }

            // 5th attempt locks the account
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(badJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(423));

            // Correct password is still rejected while locked
            LoginRequest goodReq = new LoginRequest();
            goodReq.setUsername("superadmin");
            goodReq.setPassword("Admin@2026");
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(goodReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(423));
        } finally {
            jdbcTemplate.update(
                "UPDATE user SET login_fail_count = 0, locked_until = NULL WHERE username = 'superadmin'");
        }
    }

    @Test
    void userinfo_with_valid_token_returns_user_data() throws Exception {
        // First login to get token
        LoginRequest req = new LoginRequest();
        req.setUsername("superadmin");
        req.setPassword("Admin@2026");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        mockMvc.perform(get("/api/v1/auth/userinfo")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("superadmin"))
                .andExpect(jsonPath("$.data.isSuperAdmin").value(true))
                .andExpect(jsonPath("$.data.routes").isArray());
    }
}
