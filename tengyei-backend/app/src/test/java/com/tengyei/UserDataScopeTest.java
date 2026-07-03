package com.tengyei;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class UserDataScopeTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void selfScopeUser_cannotWriteOthers_butCanWriteSelf() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbc);
        long tenantId = seeded.tenantId();

        long operatorId = insertUser(tenantId, "self_op_" + System.nanoTime());
        long targetId = insertUser(tenantId, "target_" + System.nanoTime());

        long selfRoleId = insertSelfScopeRole(tenantId);
        jdbc.update("INSERT INTO user_role (user_id, role_id, created_at) VALUES (?,?,NOW())",
                operatorId, selfRoleId);
        jdbc.update(
            "INSERT INTO role_permission (role_id, permission_id, created_at) " +
            "SELECT ?, id, NOW() FROM permission WHERE code IN ('user:edit','user:view')", selfRoleId);

        String token = OrgTestSupport.login(mockMvc, objectMapper, usernameOf(operatorId));

        // 越权：对他人改状态 -> 403
        mockMvc.perform(put("/api/v1/users/" + targetId + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content("{\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        // 合法：对本人改状态 -> 成功
        mockMvc.perform(put("/api/v1/users/" + operatorId + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content("{\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private String usernameOf(long userId) {
        return jdbc.queryForObject("SELECT username FROM `user` WHERE id = ?", String.class, userId);
    }

    private long insertUser(long tenantId, String username) {
        KeyHolder uk = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO `user` (tenant_id, user_no, username, password, real_name, phone, " +
                "is_super_admin, status, pwd_reset_required, is_deleted, created_at, updated_at) " +
                "VALUES (?,?,?,?,?,?,0,1,0,0,NOW(),NOW())",
                Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tenantId);
            ps.setString(2, "U" + tenantId + "-" + System.nanoTime());
            ps.setString(3, username);
            ps.setString(4, OrgTestSupport.ADMIN_PWD_HASH);
            ps.setString(5, "测试用户");
            ps.setString(6, "13700000000");
            return ps;
        }, uk);
        return ((Number) uk.getKeys().get("ID")).longValue();
    }

    private long insertSelfScopeRole(long tenantId) {
        KeyHolder rk = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO role (tenant_id, name, code, data_scope, is_preset, status, " +
                "is_deleted, created_at, updated_at) VALUES (?,?,?,?,0,1,0,NOW(),NOW())",
                Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tenantId);
            ps.setString(2, "仅本人");
            ps.setString(3, "self_scope_" + System.nanoTime());
            ps.setString(4, "self");
            return ps;
        }, rk);
        return ((Number) rk.getKeys().get("ID")).longValue();
    }
}
