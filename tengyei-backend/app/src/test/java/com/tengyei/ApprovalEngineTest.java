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

/** OA 审批引擎端到端：配置单节点(SPECIFIC_USER) 流程 -> 发起 -> 审批通过。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class ApprovalEngineTest {

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

    /** 选人下拉:仅审批权限(无 user:view/role:view)也能拉 /options,但 /users 仍 403 */
    @Test
    void pickerOptionsAccessibleWithApprovalPermOnly() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbcTemplate);
        String userD = "clerk_" + System.nanoTime();
        jdbcTemplate.update(
            "INSERT INTO user (tenant_id, user_no, username, password, real_name, phone, " +
            "is_super_admin, status, pwd_reset_required, is_deleted, created_at, updated_at) " +
            "VALUES (?,?,?,?,?,?,0,1,0,0,NOW(),NOW())",
            seeded.tenantId(), "U-CLK", userD, OrgTestSupport.ADMIN_PWD_HASH, "出纳D", "13911112222");
        Long uidD = jdbcTemplate.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, userD);
        jdbcTemplate.update(
            "INSERT INTO role (tenant_id, name, code, data_scope, is_preset, status, is_deleted, created_at, updated_at) " +
            "VALUES (?,?,?,?,0,1,0,NOW(),NOW())", seeded.tenantId(), "出纳", "clerk_" + System.nanoTime(), "all");
        Long clerkRoleId = jdbcTemplate.queryForObject(
            "SELECT id FROM role WHERE tenant_id = ? ORDER BY id DESC LIMIT 1", Long.class, seeded.tenantId());
        jdbcTemplate.update("INSERT INTO user_role (user_id, role_id, created_at) VALUES (?,?,NOW())", uidD, clerkRoleId);
        jdbcTemplate.update(
            "INSERT INTO role_permission (role_id, permission_id, created_at) " +
            "SELECT ?, id, NOW() FROM permission WHERE code IN ('approval:view','approval:apply')", clerkRoleId);
        String tokenD = OrgTestSupport.login(mockMvc, objectMapper, userD);

        mockMvc.perform(get("/api/v1/approval/options")
                .header("Authorization", "Bearer " + tokenD))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.users.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.roles.length()").value(org.hamcrest.Matchers.greaterThan(0)));
        // 管理接口不放开:仍需 user:view
        mockMvc.perform(get("/api/v1/users")
                .header("Authorization", "Bearer " + tokenD))
                .andExpect(jsonPath("$.code").value(403));
    }

    /** 撤回:申请人可撤回审批中的实例;详情越权:非相关人且无 manage 权限 -> 403 */
    @Test
    void cancelAndDetailPrivacy() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbcTemplate);
        String tokenA = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());

        // 局外人C:仅 approval:view + approval:cancel(无 manage,不在审批链上)
        String userC = "outsider_" + System.nanoTime();
        jdbcTemplate.update(
            "INSERT INTO user (tenant_id, user_no, username, password, real_name, phone, " +
            "is_super_admin, status, pwd_reset_required, is_deleted, created_at, updated_at) " +
            "VALUES (?,?,?,?,?,?,0,1,0,0,NOW(),NOW())",
            seeded.tenantId(), "U-OUT", userC, OrgTestSupport.ADMIN_PWD_HASH, "局外人C", "13977778888");
        Long uidC = jdbcTemplate.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, userC);
        jdbcTemplate.update(
            "INSERT INTO role (tenant_id, name, code, data_scope, is_preset, status, is_deleted, created_at, updated_at) " +
            "VALUES (?,?,?,?,0,1,0,NOW(),NOW())", seeded.tenantId(), "只读审批", "ro_appr_" + System.nanoTime(), "all");
        Long roRoleId = jdbcTemplate.queryForObject(
            "SELECT id FROM role WHERE tenant_id = ? ORDER BY id DESC LIMIT 1", Long.class, seeded.tenantId());
        jdbcTemplate.update("INSERT INTO user_role (user_id, role_id, created_at) VALUES (?,?,NOW())", uidC, roRoleId);
        jdbcTemplate.update(
            "INSERT INTO role_permission (role_id, permission_id, created_at) " +
            "SELECT ?, id, NOW() FROM permission WHERE code IN ('approval:view','approval:cancel')", roRoleId);
        String tokenC = OrgTestSupport.login(mockMvc, objectMapper, userC);

        String cfg = ("{\"nodes\":[{\"key\":\"n1\",\"name\":\"审批\",\"approverType\":\"SPECIFIC_USER\"," +
            "\"resolveMode\":\"FIRST\",\"orderBy\":1,\"condition\":null,\"targetUserId\":" + seeded.adminUserId() + "}]}")
            .replace("\"", "\\\"");
        mockMvc.perform(post("/api/v1/approval/flows")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"cxl\",\"formName\":\"撤回单\",\"processKey\":\"CXL\"," +
                    "\"configJson\":\"" + cfg + "\"}"))
                .andExpect(jsonPath("$.code").value(0));
        MvcResult r = mockMvc.perform(post("/api/v1/approval/instances")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"cxl\",\"formData\":{}}"))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        long id = objectMapper.readTree(r.getResponse().getContentAsString()).path("data").path("id").asLong();

        // 越权: C 看详情 403; 申请人(也是审批人)可看
        mockMvc.perform(get("/api/v1/approval/instances/" + id)
                .header("Authorization", "Bearer " + tokenC))
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/api/v1/approval/instances/" + id)
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.code").value(0));

        // C 撤回别人的单 -> 403; 申请人撤回 -> CANCELED; 再撤 -> 409
        mockMvc.perform(put("/api/v1/approval/instances/" + id + "/cancel")
                .header("Authorization", "Bearer " + tokenC))
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(put("/api/v1/approval/instances/" + id + "/cancel")
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/v1/approval/instances/" + id)
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
        mockMvc.perform(put("/api/v1/approval/instances/" + id + "/cancel")
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.code").value(409));
        // 撤回后审批人待办应为空
        mockMvc.perform(get("/api/v1/approval/todo")
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    /** 消息中心:待办通知审批人,通过后通知申请人;已读/全读 */
    @Test
    void noticesFlowWithApproval() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbcTemplate);
        String token = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());
        String cfg = ("{\"nodes\":[{\"key\":\"n1\",\"name\":\"审批\",\"approverType\":\"SPECIFIC_USER\"," +
            "\"resolveMode\":\"FIRST\",\"orderBy\":1,\"condition\":null,\"targetUserId\":" + seeded.adminUserId() + "}]}")
            .replace("\"", "\\\"");
        mockMvc.perform(post("/api/v1/approval/flows")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"ntc\",\"formName\":\"通知单\",\"processKey\":\"NTC\"," +
                    "\"configJson\":\"" + cfg + "\"}"))
                .andExpect(jsonPath("$.code").value(0));
        MvcResult r = mockMvc.perform(post("/api/v1/approval/instances")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"ntc\",\"formData\":{}}"))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        long id = objectMapper.readTree(r.getResponse().getContentAsString()).path("data").path("id").asLong();

        // 审批人(=自己)应收到待办通知
        mockMvc.perform(get("/api/v1/notices/unread-count")
                .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.count").value(1));
        mockMvc.perform(get("/api/v1/notices")
                .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data[0].type").value("APPROVAL_TODO"));

        // 通过后申请人收到结果通知
        mockMvc.perform(put("/api/v1/approval/instances/" + id + "/act")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/v1/notices/unread-count")
                .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.count").value(2));

        // 全部已读
        mockMvc.perform(put("/api/v1/notices/read-all")
                .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/v1/notices/unread-count")
                .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.count").value(0));
    }

    /** 表单字段定义随流程保存,发起人通过 /forms 取回用于动态渲染 */
    @Test
    void formFieldsRoundTrip() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbcTemplate);
        String token = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());
        String cfg = ("{\"nodes\":[{\"key\":\"n1\",\"name\":\"审批\",\"approverType\":\"SPECIFIC_USER\"," +
            "\"resolveMode\":\"FIRST\",\"orderBy\":1,\"condition\":null,\"targetUserId\":" + seeded.adminUserId() + "}]}")
            .replace("\"", "\\\"");
        String fields = ("[{\"key\":\"days\",\"label\":\"请假天数\",\"type\":\"number\",\"required\":true}]")
            .replace("\"", "\\\"");
        mockMvc.perform(post("/api/v1/approval/flows")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"leave2\",\"formName\":\"请假\",\"processKey\":\"LEAVE2\"," +
                    "\"configJson\":\"" + cfg + "\",\"fieldsJson\":\"" + fields + "\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/v1/approval/forms")
                .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].formType").value("leave2"))
                .andExpect(jsonPath("$.data[0].fieldsJson").value(org.hamcrest.Matchers.containsString("days")));
    }

    /** 代理：A 设置 B 为代理后,指向 A 的审批自动落到 B;超时:节点激活时按 timeoutHours 算 dueAt */
    @Test
    void delegateRedirectAndTimeoutDueAt() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbcTemplate);
        String tokenA = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());
        String userB = "deleg_" + System.nanoTime();
        jdbcTemplate.update(
            "INSERT INTO user (tenant_id, user_no, username, password, real_name, phone, " +
            "is_super_admin, status, pwd_reset_required, is_deleted, created_at, updated_at) " +
            "VALUES (?,?,?,?,?,?,0,1,0,0,NOW(),NOW())",
            seeded.tenantId(), "U-DELEG", userB, OrgTestSupport.ADMIN_PWD_HASH, "代理人B", "13955556666");
        Long uidB = jdbcTemplate.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, userB);
        jdbcTemplate.update("INSERT INTO user_role (user_id, role_id, created_at) VALUES (?,?,NOW())",
            uidB, seeded.roleId());
        String tokenB = OrgTestSupport.login(mockMvc, objectMapper, userB);

        // A 设置代理规则: 现在起 1 天内由 B 代理
        mockMvc.perform(put("/api/v1/approval/delegate")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"delegateId\":" + uidB + ",\"startAt\":\"" +
                    java.time.LocalDateTime.now().minusMinutes(1) + "\",\"endAt\":\"" +
                    java.time.LocalDateTime.now().plusDays(1) + "\",\"status\":1}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/v1/approval/delegate")
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data.delegateId").value(uidB));

        // 流程: SPECIFIC_USER 指向 A + 超时 24 小时
        String cfg = ("{\"nodes\":[{\"key\":\"n1\",\"name\":\"审批\",\"approverType\":\"SPECIFIC_USER\"," +
            "\"resolveMode\":\"FIRST\",\"orderBy\":1,\"condition\":null,\"targetUserId\":" + seeded.adminUserId() +
            ",\"timeoutHours\":24}]}").replace("\"", "\\\"");
        mockMvc.perform(post("/api/v1/approval/flows")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"deleg\",\"formName\":\"代理单\",\"processKey\":\"DELEG\"," +
                    "\"configJson\":\"" + cfg + "\"}"))
                .andExpect(jsonPath("$.code").value(0));
        MvcResult r = mockMvc.perform(post("/api/v1/approval/instances")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"deleg\",\"formData\":{}}"))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        long id = objectMapper.readTree(r.getResponse().getContentAsString()).path("data").path("id").asLong();

        // 节点应落在 B(代理), 名称含"代理", dueAt 已按超时算出
        mockMvc.perform(get("/api/v1/approval/instances/" + id)
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data.nodes[0].approverId").value(uidB))
                .andExpect(jsonPath("$.data.nodes[0].approverName").value(org.hamcrest.Matchers.containsString("代理")))
                .andExpect(jsonPath("$.data.nodes[0].dueAt").exists());
        // B 的待办里能看到且带 myDueAt(超时提醒数据源)
        mockMvc.perform(get("/api/v1/approval/todo")
                .header("Authorization", "Bearer " + tokenB))
                .andExpect(jsonPath("$.data[0].id").value(id))
                .andExpect(jsonPath("$.data[0].myDueAt").exists());
        // A 无权处理(已代理), B 通过 -> APPROVED
        mockMvc.perform(put("/api/v1/approval/instances/" + id + "/act")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\"}"))
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(put("/api/v1/approval/instances/" + id + "/act")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/v1/approval/instances/" + id)
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    /** 转交：A 把待办转给 B,B 审批通过;统计接口返回聚合数据 */
    @Test
    void transferThenStatistics() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbcTemplate);
        String tokenA = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());
        String userB = "recv_" + System.nanoTime();
        jdbcTemplate.update(
            "INSERT INTO user (tenant_id, user_no, username, password, real_name, phone, " +
            "is_super_admin, status, pwd_reset_required, is_deleted, created_at, updated_at) " +
            "VALUES (?,?,?,?,?,?,0,1,0,0,NOW(),NOW())",
            seeded.tenantId(), "U-RECV", userB, OrgTestSupport.ADMIN_PWD_HASH, "接收人B", "13933334444");
        Long uidB = jdbcTemplate.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, userB);
        jdbcTemplate.update("INSERT INTO user_role (user_id, role_id, created_at) VALUES (?,?,NOW())",
            uidB, seeded.roleId());
        String tokenB = OrgTestSupport.login(mockMvc, objectMapper, userB);

        String cfg = ("{\"nodes\":[{\"key\":\"n1\",\"name\":\"审批\",\"approverType\":\"SPECIFIC_USER\"," +
            "\"resolveMode\":\"FIRST\",\"orderBy\":1,\"condition\":null,\"targetUserId\":" + seeded.adminUserId() + "}]}")
            .replace("\"", "\\\"");
        mockMvc.perform(post("/api/v1/approval/flows")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"trans\",\"formName\":\"转交单\",\"processKey\":\"TRANS\"," +
                    "\"configJson\":\"" + cfg + "\"}"))
                .andExpect(jsonPath("$.code").value(0));
        MvcResult r = mockMvc.perform(post("/api/v1/approval/instances")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"trans\",\"formData\":{}}"))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        long id = objectMapper.readTree(r.getResponse().getContentAsString()).path("data").path("id").asLong();

        // A 转交给 B
        mockMvc.perform(put("/api/v1/approval/instances/" + id + "/transfer")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUserId\":" + uidB + "}"))
                .andExpect(jsonPath("$.code").value(0));
        // A 已无权处理,B 可通过
        mockMvc.perform(put("/api/v1/approval/instances/" + id + "/act")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\"}"))
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(put("/api/v1/approval/instances/" + id + "/act")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/v1/approval/instances/" + id)
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // 统计
        mockMvc.perform(get("/api/v1/approval/statistics")
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.byStatus.APPROVED").value(1));
    }

    /** 会签(ALL)：角色下两人须全部通过；或签(ANYONE)：一人通过即整单通过 */
    @Test
    void roleNode_allAndAnyoneModes() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbcTemplate);
        String tokenA = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());
        // 第二个审批人：同 company_admin 角色
        String userB = "cosign_" + System.nanoTime();
        jdbcTemplate.update(
            "INSERT INTO user (tenant_id, user_no, username, password, real_name, phone, " +
            "is_super_admin, status, pwd_reset_required, is_deleted, created_at, updated_at) " +
            "VALUES (?,?,?,?,?,?,0,1,0,0,NOW(),NOW())",
            seeded.tenantId(), "U-COSIGN", userB, OrgTestSupport.ADMIN_PWD_HASH, "会签人B", "13911112222");
        Long uidB = jdbcTemplate.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, userB);
        jdbcTemplate.update("INSERT INTO user_role (user_id, role_id, created_at) VALUES (?,?,NOW())",
            uidB, seeded.roleId());
        String tokenB = OrgTestSupport.login(mockMvc, objectMapper, userB);

        // ---- ALL 会签 ----
        String allConfig = ("{\"nodes\":[{\"key\":\"n1\",\"name\":\"会签\",\"approverType\":\"ROLE\"," +
            "\"resolveMode\":\"ALL\",\"orderBy\":1,\"condition\":null,\"targetRoleId\":" + seeded.roleId() + "}]}")
            .replace("\"", "\\\"");
        mockMvc.perform(post("/api/v1/approval/flows")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"cosign\",\"formName\":\"会签单\",\"processKey\":\"COSIGN\"," +
                    "\"configJson\":\"" + allConfig + "\"}"))
                .andExpect(jsonPath("$.code").value(0));
        MvcResult r1 = mockMvc.perform(post("/api/v1/approval/instances")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"cosign\",\"formData\":{}}"))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        long id1 = objectMapper.readTree(r1.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(put("/api/v1/approval/instances/" + id1 + "/act")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\"}"))
                .andExpect(jsonPath("$.code").value(0));
        // 一人通过后仍 PENDING（等第二人）
        mockMvc.perform(get("/api/v1/approval/instances/" + id1)
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        mockMvc.perform(put("/api/v1/approval/instances/" + id1 + "/act")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/v1/approval/instances/" + id1)
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // ---- ANYONE 或签：一人通过即整单通过 ----
        String anyConfig = ("{\"nodes\":[{\"key\":\"n1\",\"name\":\"或签\",\"approverType\":\"ROLE\"," +
            "\"resolveMode\":\"ANYONE\",\"orderBy\":1,\"condition\":null,\"targetRoleId\":" + seeded.roleId() + "}]}")
            .replace("\"", "\\\"");
        mockMvc.perform(post("/api/v1/approval/flows")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"orsign\",\"formName\":\"或签单\",\"processKey\":\"ORSIGN\"," +
                    "\"configJson\":\"" + anyConfig + "\"}"))
                .andExpect(jsonPath("$.code").value(0));
        MvcResult r2 = mockMvc.perform(post("/api/v1/approval/instances")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"orsign\",\"formData\":{}}"))
                .andExpect(jsonPath("$.code").value(0)).andReturn();
        long id2 = objectMapper.readTree(r2.getResponse().getContentAsString()).path("data").path("id").asLong();
        mockMvc.perform(put("/api/v1/approval/instances/" + id2 + "/act")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/v1/approval/instances/" + id2)
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    /** 平台层(tenant 0)同样可配置流程并走完审批：superadmin 持 PERM_*，审批人指定为自己(id=1) */
    @Test
    void platformTier_configureApplyApprove() throws Exception {
        String superToken = OrgTestSupport.login(mockMvc, objectMapper, "superadmin");
        String configJson = ("{\"nodes\":[{\"key\":\"n1\",\"name\":\"平台审批\"," +
            "\"approverType\":\"SPECIFIC_USER\",\"resolveMode\":\"FIRST\",\"orderBy\":1," +
            "\"condition\":null,\"targetUserId\":1}]}").replace("\"", "\\\"");

        mockMvc.perform(post("/api/v1/approval/flows")
                .header("Authorization", "Bearer " + superToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"platform_purchase\",\"formName\":\"平台采购\",\"processKey\":\"P_PURCHASE\"," +
                    "\"configJson\":\"" + configJson + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MvcResult applied = mockMvc.perform(post("/api/v1/approval/instances")
                .header("Authorization", "Bearer " + superToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"platform_purchase\",\"formData\":{\"amount\":500}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long instanceId = objectMapper.readTree(applied.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(put("/api/v1/approval/instances/" + instanceId + "/act")
                .header("Authorization", "Bearer " + superToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\",\"comment\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/approval/instances/" + instanceId)
                .header("Authorization", "Bearer " + superToken))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void applyThenApprove_flowsToApproved() throws Exception {
        String configJson = ("{\"nodes\":[{\"key\":\"n1\",\"name\":\"指定审批\"," +
            "\"approverType\":\"SPECIFIC_USER\",\"resolveMode\":\"FIRST\",\"orderBy\":1," +
            "\"condition\":null,\"targetUserId\":" + adminUserId + "}]}").replace("\"", "\\\"");

        mockMvc.perform(post("/api/v1/approval/flows")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"leave\",\"formName\":\"请假申请\",\"processKey\":\"LEAVE\"," +
                    "\"configJson\":\"" + configJson + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MvcResult applied = mockMvc.perform(post("/api/v1/approval/instances")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"leave\",\"formData\":{\"days\":2,\"reason\":\"test\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long instanceId = objectMapper.readTree(applied.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(get("/api/v1/approval/instances/" + instanceId)
                .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.nodes[0].status").value("APPROVING"));

        mockMvc.perform(get("/api/v1/approval/todo")
                .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data[0].id").value(instanceId));

        mockMvc.perform(put("/api/v1/approval/instances/" + instanceId + "/act")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\",\"comment\":\"同意\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/approval/instances/" + instanceId)
                .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.nodes[0].status").value("APPROVED"));
    }
}
