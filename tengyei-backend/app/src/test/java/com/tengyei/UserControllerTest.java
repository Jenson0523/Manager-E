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

    /** 越权防护:给用户分配角色时,平台角色(tenant 0)/其他公司角色被过滤,只有本公司角色生效 */
    @Test
    void assign_foreign_tenant_role_is_filtered() throws Exception {
        String uname = "victim_" + System.nanoTime();
        MvcResult created = mockMvc.perform(post("/api/v1/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","realName":"王五","phone":"13700000002",
                     "password":"Staff@2026","roleIds":[]}
                    """.formatted(uname)))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        long newId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 伪造一个平台角色(tenant 0),尝试与本公司角色一起分配
        jdbcTemplate.update(
            "INSERT INTO role (tenant_id, name, code, data_scope, is_preset, status, is_deleted, created_at, updated_at) " +
            "VALUES (0, '平台运营', 'plat_" + System.nanoTime() + "', 'all', 0, 1, 0, NOW(), NOW())");
        Long platformRoleId = jdbcTemplate.queryForObject(
            "SELECT id FROM role WHERE tenant_id = 0 ORDER BY id DESC LIMIT 1", Long.class);

        mockMvc.perform(put("/api/v1/users/" + newId + "/roles")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleIds\":[" + platformRoleId + "," + roleId + "]}"))
                .andExpect(jsonPath("$.code").value(0));

        Long foreign = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_role WHERE user_id = ? AND role_id = ?",
            Long.class, newId, platformRoleId);
        org.junit.jupiter.api.Assertions.assertEquals(0, foreign);
        Long own = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_role WHERE user_id = ? AND role_id = ?",
            Long.class, newId, roleId);
        org.junit.jupiter.api.Assertions.assertEquals(1, own);
    }
}
