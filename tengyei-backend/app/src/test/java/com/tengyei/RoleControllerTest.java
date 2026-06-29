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
class RoleControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    private String token;

    @BeforeEach
    void setup() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbcTemplate);
        token = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());
    }

    @Test
    void permissions_grouped_by_module() throws Exception {
        mockMvc.perform(get("/api/v1/permissions")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].module").exists())
                .andExpect(jsonPath("$.data[0].permissions").isArray());
    }

    @Test
    void create_role_then_assign_permissions() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/roles")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"部门经理\",\"code\":\"dept_mgr\",\"dataScope\":\"dept\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long roleId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        Long permId = jdbcTemplate.queryForObject(
            "SELECT id FROM permission WHERE code = 'user:view'", Long.class);

        mockMvc.perform(put("/api/v1/roles/" + roleId + "/permissions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissionIds\":[" + permId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/roles/" + roleId + "/permissions")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value(permId.intValue()));
    }
}
