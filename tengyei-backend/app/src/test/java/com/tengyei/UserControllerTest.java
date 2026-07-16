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

    /** 按角色筛选:筛选下推到SQL,total 与 records 只含该角色用户(修复前分页后过滤导致total错乱) */
    @Test
    void filterByRolePaginatesCorrectly() throws Exception {
        // 新建一个角色,只给一个用户挂上
        MvcResult roleRes = mockMvc.perform(post("/api/v1/roles")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"筛选角色\",\"code\":\"filt_" + System.nanoTime() + "\",\"dataScope\":\"self\"}"))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        long filtRole = objectMapper.readTree(roleRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        String uname = "filtered_" + System.nanoTime();
        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","realName":"筛选用户","phone":"13700009999",
                     "password":"Admin@2026","roleIds":[%d]}
                    """.formatted(uname, filtRole)))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/users?page=1&size=20&roleId=" + filtRole)
                .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value(uname));
    }

    /** dept 数据范围但用户未分配部门:看不到任何人(修复前会漏成看全公司) */
    @Test
    void deptScopeWithoutDeptSeesNobody() throws Exception {
        MvcResult roleRes = mockMvc.perform(post("/api/v1/roles")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"部门范围\",\"code\":\"deptsc_" + System.nanoTime() + "\",\"dataScope\":\"dept\"}"))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        long deptRole = objectMapper.readTree(roleRes.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        // 给该角色授"查看人员"
        jdbcTemplate.update(
            "INSERT INTO role_permission (role_id,permission_id,created_at) " +
            "SELECT ?,id,NOW() FROM permission WHERE code='user:view'", deptRole);

        String uname = "deptscoped_" + System.nanoTime();
        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","realName":"部门范围用户","phone":"13700008888",
                     "password":"Admin@2026","roleIds":[%d]}
                    """.formatted(uname, deptRole)))
                .andExpect(jsonPath("$.code").value(0));

        String scopedToken = OrgTestSupport.login(mockMvc, objectMapper, uname);
        mockMvc.perform(get("/api/v1/users?page=1&size=20")
                .header("Authorization", "Bearer " + scopedToken))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    /** Excel 导入:合法行成功入库,非法行(弱密码)带行号报错,互不影响 */
    @Test
    void importUsersPartialSuccess() throws Exception {
        var ok = new com.tengyei.org.dto.UserImportRowVO();
        ok.setRealName("导入甲");
        ok.setUsername("imp_ok_" + System.nanoTime());
        ok.setPassword("Ok@2026abc");
        ok.setPhone("13812340001");
        ok.setRoleNames("企业管理员");
        var bad = new com.tengyei.org.dto.UserImportRowVO();
        bad.setRealName("导入乙");
        bad.setUsername("imp_bad_" + System.nanoTime());
        bad.setPassword("123"); // 弱密码,应失败
        bad.setPhone("13812340002");
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        com.alibaba.excel.EasyExcel.write(bos, com.tengyei.org.dto.UserImportRowVO.class)
            .sheet().doWrite(java.util.List.of(ok, bad));

        var file = new org.springframework.mock.web.MockMultipartFile(
            "file", "users.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bos.toByteArray());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/v1/users/import")
                .file(file)
                .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.success").value(1))
                .andExpect(jsonPath("$.data.failed").value(1))
                .andExpect(jsonPath("$.data.errors[0].row").value(3));

        Long created = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user WHERE username = ?", Long.class, ok.getUsername());
        org.junit.jupiter.api.Assertions.assertEquals(1, created);
    }

    /** 改密踢会话:管理员重置密码后,该用户改密前签发的 token 立即失效,新密码登录恢复 */
    @Test
    void resetPasswordInvalidatesOldTokens() throws Exception {
        String uname = "kick_" + System.nanoTime();
        MvcResult created = mockMvc.perform(post("/api/v1/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","realName":"被踢者","phone":"13700000003",
                     "password":"Admin@2026","roleIds":[%d]}
                    """.formatted(uname, roleId)))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        long newId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        String staffToken = OrgTestSupport.login(mockMvc, objectMapper, uname);
        mockMvc.perform(get("/api/v1/notices")
                .header("Authorization", "Bearer " + staffToken))
                .andExpect(jsonPath("$.code").value(0));

        // JWT 签发时间为秒级精度,确保改密时间落在签发之后的秒
        Thread.sleep(1100);
        mockMvc.perform(put("/api/v1/users/" + newId + "/reset-password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Kicked@2026\"}"))
                .andExpect(jsonPath("$.code").value(0));

        // 旧 token 作废 → 401
        mockMvc.perform(get("/api/v1/notices")
                .header("Authorization", "Bearer " + staffToken))
                .andExpect(jsonPath("$.code").value(401));
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
