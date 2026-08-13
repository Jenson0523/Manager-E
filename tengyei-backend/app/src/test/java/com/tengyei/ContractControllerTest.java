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

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class ContractControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void companyAdmin_canCreateListAndDeleteContract() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbc);
        String token = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());

        // 编号留空 -> 服务端自动生成
        String id = mockMvc.perform(post("/api/v1/contracts")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"年度采购框架协议\",\"partyB\":\"某供应商\",\"type\":\"PURCHASE\",\"amount\":120000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        long contractId = objectMapper.readTree(id).path("data").path("id").asLong();

        String no = jdbc.queryForObject(
            "SELECT contract_no FROM biz_contract WHERE id = ?", String.class, contractId);
        org.junit.jupiter.api.Assertions.assertTrue(
            no != null && no.startsWith("HT"), "合同编号应自动生成为 HT 开头,实际=" + no);

        // 负责人未指定时应落到创建者本人,否则 self 范围的人建完就看不见
        Long ownerId = jdbc.queryForObject(
            "SELECT owner_id FROM biz_contract WHERE id = ?", Long.class, contractId);
        org.junit.jupiter.api.Assertions.assertEquals(seeded.adminUserId(), ownerId);

        mockMvc.perform(get("/api/v1/contracts").param("keyword", "年度采购")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].contractNo").value(no))
                .andExpect(jsonPath("$.data.records[0].ownerName").value("企业管理员"));

        mockMvc.perform(delete("/api/v1/contracts/" + contractId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void duplicateContractNo_isRejected() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbc);
        String token = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());
        String body = "{\"contractNo\":\"HT-DUP-001\",\"name\":\"甲\",\"partyB\":\"乙方\"}";

        mockMvc.perform(post("/api/v1/contracts").header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/v1/contracts").header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(422));
    }

    /** 编号永久退役:删掉合同后原编号也不能再用(V21 唯一键覆盖软删记录) */
    @Test
    void deletedContractNo_cannotBeReused() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbc);
        String token = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());
        String body = "{\"contractNo\":\"HT-RETIRED-001\",\"name\":\"甲\",\"partyB\":\"乙方\"}";

        String res = mockMvc.perform(post("/api/v1/contracts").header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(res).path("data").path("id").asLong();

        mockMvc.perform(delete("/api/v1/contracts/" + id).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));

        // 软删后重用同一编号应被拒(而非撞唯一键抛 500)
        mockMvc.perform(post("/api/v1/contracts").header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(422));
    }

    /** 自动编号必须跳过已退役(含软删)的号段,否则生成的号会撞唯一键 */
    @Test
    void autoContractNo_skipsRetiredNumbers() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbc);
        String token = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());

        String first = mockMvc.perform(post("/api/v1/contracts").header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content("{\"name\":\"甲\",\"partyB\":\"乙方\"}"))
                .andReturn().getResponse().getContentAsString();
        long firstId = objectMapper.readTree(first).path("data").path("id").asLong();
        String firstNo = jdbc.queryForObject(
            "SELECT contract_no FROM biz_contract WHERE id = ?", String.class, firstId);

        mockMvc.perform(delete("/api/v1/contracts/" + firstId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));

        String second = mockMvc.perform(post("/api/v1/contracts").header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content("{\"name\":\"乙\",\"partyB\":\"乙方\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        long secondId = objectMapper.readTree(second).path("data").path("id").asLong();
        String secondNo = jdbc.queryForObject(
            "SELECT contract_no FROM biz_contract WHERE id = ?", String.class, secondId);

        org.junit.jupiter.api.Assertions.assertNotEquals(firstNo, secondNo,
            "已删除合同的编号不应被重新分配");
    }

    @Test
    void viewOnlyUser_canRead_butCannotCreateOrDelete() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbc);
        long tenantId = seeded.tenantId();
        long viewerId = insertUser(tenantId, "viewer_" + System.nanoTime());
        long roleId = insertRole(tenantId, "all");
        jdbc.update("INSERT INTO user_role (user_id, role_id, created_at) VALUES (?,?,NOW())",
                viewerId, roleId);
        jdbc.update("INSERT INTO role_permission (role_id, permission_id, created_at) " +
                "SELECT ?, id, NOW() FROM permission WHERE code = 'contract:view'", roleId);

        String token = OrgTestSupport.login(mockMvc, objectMapper, usernameOf(viewerId));

        mockMvc.perform(get("/api/v1/contracts").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/v1/contracts").header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"越权新建\",\"partyB\":\"乙方\"}"))
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(delete("/api/v1/contracts/1").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void selfScopeUser_seesOnlyOwnContracts_andCannotDeleteOthers() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbc);
        long tenantId = seeded.tenantId();
        String adminToken = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());

        long salesId = insertUser(tenantId, "sales_" + System.nanoTime());
        long selfRoleId = insertRole(tenantId, "self");
        jdbc.update("INSERT INTO user_role (user_id, role_id, created_at) VALUES (?,?,NOW())",
                salesId, selfRoleId);
        jdbc.update("INSERT INTO role_permission (role_id, permission_id, created_at) " +
                "SELECT ?, id, NOW() FROM permission " +
                "WHERE code IN ('contract:view','contract:manage','contract:disable')", selfRoleId);

        // 管理员建一张归属自己的合同
        String adminRes = mockMvc.perform(post("/api/v1/contracts")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"管理员的合同\",\"partyB\":\"甲方\"}"))
                .andReturn().getResponse().getContentAsString();
        long adminContractId = objectMapper.readTree(adminRes).path("data").path("id").asLong();

        String salesToken = OrgTestSupport.login(mockMvc, objectMapper, usernameOf(salesId));
        mockMvc.perform(post("/api/v1/contracts")
                .header("Authorization", "Bearer " + salesToken)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"业务员的合同\",\"partyB\":\"乙方\"}"))
                .andExpect(jsonPath("$.code").value(0));

        // self 范围只看得到自己经办的那一张
        mockMvc.perform(get("/api/v1/contracts").header("Authorization", "Bearer " + salesToken))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("业务员的合同"));

        // 范围外的合同不能删(读列表看不到,写也必须挡住)
        mockMvc.perform(delete("/api/v1/contracts/" + adminContractId)
                .header("Authorization", "Bearer " + salesToken))
                .andExpect(jsonPath("$.code").value(403));
    }

    /** 只有 manage 的账号不能把合同置为终止(终止＝作废,属 contract:disable) */
    @Test
    void manageOnlyUser_cannotTerminateContract() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbc);
        long tenantId = seeded.tenantId();
        long editorId = insertUser(tenantId, "editor_" + System.nanoTime());
        long roleId = insertRole(tenantId, "all");
        jdbc.update("INSERT INTO user_role (user_id, role_id, created_at) VALUES (?,?,NOW())",
                editorId, roleId);
        jdbc.update("INSERT INTO role_permission (role_id, permission_id, created_at) " +
                "SELECT ?, id, NOW() FROM permission WHERE code IN ('contract:view','contract:manage')", roleId);

        String token = OrgTestSupport.login(mockMvc, objectMapper, usernameOf(editorId));
        String res = mockMvc.perform(post("/api/v1/contracts").header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"待终止合同\",\"partyB\":\"乙方\",\"status\":\"EFFECTIVE\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(res).path("data").path("id").asLong();

        // 走状态接口终止 -> 403
        mockMvc.perform(put("/api/v1/contracts/" + id + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content("{\"status\":\"TERMINATED\"}"))
                .andExpect(jsonPath("$.code").value(403));

        // 走保存接口绕后终止 -> 同样 403
        mockMvc.perform(post("/api/v1/contracts").header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"id\":" + id + ",\"name\":\"待终止合同\",\"partyB\":\"乙方\",\"status\":\"TERMINATED\"}"))
                .andExpect(jsonPath("$.code").value(403));

        // 允许的状态流转不受影响
        mockMvc.perform(put("/api/v1/contracts/" + id + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(jsonPath("$.code").value(0));
    }

    /** 负责人只能是本企业在职用户,否则会把他租户员工的姓名/部门带进本租户合同 */
    @Test
    void ownerFromAnotherTenant_isRejected() throws Exception {
        var mine = OrgTestSupport.seedCompanyAdmin(jdbc);
        var other = OrgTestSupport.seedCompanyAdmin(jdbc);
        String token = OrgTestSupport.login(mockMvc, objectMapper, mine.username());

        mockMvc.perform(post("/api/v1/contracts").header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"越租户合同\",\"partyB\":\"乙方\",\"ownerId\":" + other.adminUserId() + "}"))
                .andExpect(jsonPath("$.code").value(422));
    }

    /** 无合同权限的账号不应在「业务应用」看到合同入口(否则点进去才撞 403) */
    @Test
    void moduleMenu_hiddenForUserWithoutContractPermission() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbc);
        long tenantId = seeded.tenantId();

        long outsiderId = insertUser(tenantId, "outsider_" + System.nanoTime());
        long roleId = insertRole(tenantId, "all");
        jdbc.update("INSERT INTO user_role (user_id, role_id, created_at) VALUES (?,?,NOW())",
                outsiderId, roleId);
        jdbc.update("INSERT INTO role_permission (role_id, permission_id, created_at) " +
                "SELECT ?, id, NOW() FROM permission WHERE code = 'user:view'", roleId);

        String outsiderToken = OrgTestSupport.login(mockMvc, objectMapper, usernameOf(outsiderId));
        mockMvc.perform(get("/api/v1/modules/active")
                .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[?(@.moduleCode == 'contract')]").isEmpty());

        // 有合同权限的管理员仍能看到入口
        String adminToken = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());
        mockMvc.perform(get("/api/v1/modules/active")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data[?(@.moduleCode == 'contract')]").isNotEmpty());
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

    private long insertRole(long tenantId, String dataScope) {
        KeyHolder rk = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO role (tenant_id, name, code, data_scope, is_preset, status, " +
                "is_deleted, created_at, updated_at) VALUES (?,?,?,?,0,1,0,NOW(),NOW())",
                Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tenantId);
            ps.setString(2, "合同测试角色");
            ps.setString(3, "contract_test_" + System.nanoTime());
            ps.setString(4, dataScope);
            return ps;
        }, rk);
        return ((Number) rk.getKeys().get("ID")).longValue();
    }
}
