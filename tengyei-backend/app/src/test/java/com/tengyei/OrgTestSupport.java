package com.tengyei;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tengyei.auth.dto.LoginRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Seeds a company + admin (all permissions) and returns a logged-in token. */
public final class OrgTestSupport {

    public static final String ADMIN_PWD_HASH =
        "$2a$12$ufBfS4xdJAYPymNVNHkCbepoD4vzNhw.A2pR942oNeIfzpZFNO.R2"; // Admin@2026

    private OrgTestSupport() {}

    public record Seeded(long tenantId, long adminUserId, long roleId, String username) {}

    public static Seeded seedCompanyAdmin(JdbcTemplate jdbc) {
        String suffix = String.valueOf(System.nanoTime());
        KeyHolder ck = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO company (company_no, full_name, short_name, admin_name, admin_phone, " +
                "status, is_deleted, created_at, updated_at) VALUES (?,?,?,?,?,1,0,NOW(),NOW())",
                Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, "E" + suffix.substring(suffix.length() - 6));
            ps.setString(2, "测试企业" + suffix);
            ps.setString(3, "测试" + suffix);
            ps.setString(4, "管理员");
            ps.setString(5, "13900000000");
            return ps;
        }, ck);
        long tenantId = ((Number) ck.getKeys().get("ID")).longValue();

        String username = "admin_" + suffix;
        KeyHolder uk = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO `user` (tenant_id, user_no, username, password, real_name, phone, " +
                "is_super_admin, status, pwd_reset_required, is_deleted, created_at, updated_at) " +
                "VALUES (?,?,?,?,?,?,0,1,0,0,NOW(),NOW())",
                Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tenantId);
            ps.setString(2, "U" + tenantId + "-0001");
            ps.setString(3, username);
            ps.setString(4, ADMIN_PWD_HASH);
            ps.setString(5, "企业管理员");
            ps.setString(6, "13900000001");
            return ps;
        }, uk);
        long adminUserId = ((Number) uk.getKeys().get("ID")).longValue();

        KeyHolder rk = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO role (tenant_id, name, code, data_scope, is_preset, status, " +
                "is_deleted, created_at, updated_at) VALUES (?,?,?,?,1,1,0,NOW(),NOW())",
                Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tenantId);
            ps.setString(2, "企业管理员");
            ps.setString(3, "company_admin");
            ps.setString(4, "all");
            return ps;
        }, rk);
        long roleId = ((Number) rk.getKeys().get("ID")).longValue();

        jdbc.update("INSERT INTO user_role (user_id, role_id, created_at) VALUES (?,?,NOW())",
                adminUserId, roleId);
        // grant ALL permissions to this role
        jdbc.update(
            "INSERT INTO role_permission (role_id, permission_id, created_at) " +
            "SELECT ?, id, NOW() FROM permission WHERE tier = 'company'", roleId);

        return new Seeded(tenantId, adminUserId, roleId, username);
    }

    public static String login(MockMvc mockMvc, ObjectMapper om, String username) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword("Admin@2026");
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(req)))
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }
}
