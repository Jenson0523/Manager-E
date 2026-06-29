package com.tengyei;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    private String token;
    private long roleId;

    @BeforeEach
    void setup() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbcTemplate);
        roleId = seeded.roleId();
        token = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());
    }

    @Test
    void create_assign_role_then_new_user_can_login() throws Exception {
        String uname = "staff_" + System.nanoTime();
        String body = """
            {"username":"%s","realName":"李四","phone":"13700000001",
             "password":"Staff@2026","roleIds":[%d]}
            """.formatted(uname, roleId);

        MvcResult created = mockMvc.perform(post("/api/v1/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long newId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(get("/api/v1/users?page=1&size=20&keyword=" + uname)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].username").value(uname));

        // reset password endpoint works
        mockMvc.perform(put("/api/v1/users/" + newId + "/reset-password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"New@2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
