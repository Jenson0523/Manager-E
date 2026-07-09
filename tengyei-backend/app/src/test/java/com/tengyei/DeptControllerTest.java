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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class DeptControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    private String token;
    private long adminUserId;

    @BeforeEach
    void setup() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbcTemplate);
        token = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());
        adminUserId = seeded.adminUserId();
    }

    @Test
    void create_then_tree_returns_hierarchy() throws Exception {
        mockMvc.perform(post("/api/v1/depts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"研发部\",\"parentId\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/depts/tree")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].name").value("研发部"));
    }

    @Test
    void create_with_leader_auto_binds_leader_to_dept() throws Exception {
        mockMvc.perform(post("/api/v1/depts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"市场部\",\"parentId\":0,\"leaderId\":" + adminUserId + "}"))
                .andExpect(jsonPath("$.code").value(0));

        Long deptId = jdbcTemplate.queryForObject(
                "SELECT id FROM dept WHERE name = '市场部'", Long.class);
        Integer isPrimary = jdbcTemplate.queryForObject(
                "SELECT is_primary FROM user_dept WHERE user_id = ? AND dept_id = ?",
                Integer.class, adminUserId, deptId);
        assertEquals(1, isPrimary);

        // 重复保存(编辑)不应报错或产生重复行
        mockMvc.perform(put("/api/v1/depts/" + deptId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"市场部\",\"leaderId\":" + adminUserId + "}"))
                .andExpect(jsonPath("$.code").value(0));
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_dept WHERE user_id = ? AND dept_id = ?",
                Long.class, adminUserId, deptId);
        assertEquals(1L, count);
    }
}
