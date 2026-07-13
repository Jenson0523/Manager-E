package com.tengyei;

import com.fasterxml.jackson.databind.ObjectMapper;
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

/** 审批深化:驳回策略(退回发起人/退回上一节点) + 前/后加签 + 重新提交 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class ApprovalDeepeningTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    /** 建两级 SPECIFIC_USER 流程:node1=userA, node2=userB(驳回策略按参数) */
    private void createFlow(String token, String formType, long approver1, long approver2, String node2Policy) throws Exception {
        String cfg = ("{\"nodes\":[" +
            "{\"key\":\"n1\",\"name\":\"一级审批\",\"approverType\":\"SPECIFIC_USER\",\"resolveMode\":\"FIRST\"," +
            "\"orderBy\":1,\"condition\":null,\"targetUserId\":" + approver1 + "}," +
            "{\"key\":\"n2\",\"name\":\"二级审批\",\"approverType\":\"SPECIFIC_USER\",\"resolveMode\":\"FIRST\"," +
            "\"orderBy\":2,\"condition\":null,\"targetUserId\":" + approver2 +
            (node2Policy != null ? ",\"rejectPolicy\":\"" + node2Policy + "\"" : "") + "}]}")
            .replace("\"", "\\\"");
        mockMvc.perform(post("/api/v1/approval/flows")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"" + formType + "\",\"formName\":\"" + formType + "\"," +
                    "\"processKey\":\"" + formType.toUpperCase() + "\",\"configJson\":\"" + cfg + "\"}"))
                .andExpect(jsonPath("$.code").value(0));
    }

    private long apply(String token, String formType) throws Exception {
        String body = mockMvc.perform(post("/api/v1/approval/instances")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"" + formType + "\",\"formData\":{}}"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("data").get("id").asLong();
    }

    private void act(String token, long instanceId, String action) throws Exception {
        mockMvc.perform(put("/api/v1/approval/instances/" + instanceId + "/act")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"" + action + "\",\"comment\":\"t\"}"))
                .andExpect(jsonPath("$.code").value(0));
    }

    private String instanceStatus(long id) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM wf_instance WHERE id = ?", String.class, id);
    }

    /** 造一个同租户第二用户(拥有全部 company 权限,可当审批人) */
    private long seedSecondUser(long tenantId, long roleId, String username) {
        jdbcTemplate.update(
            "INSERT INTO `user` (tenant_id, user_no, username, password, real_name, phone, " +
            "is_super_admin, status, pwd_reset_required, is_deleted, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, '审批员乙', '13900000003', 0, 1, 0, 0, NOW(), NOW())",
            tenantId, "U" + tenantId + "-0003", username, OrgTestSupport.ADMIN_PWD_HASH);
        Long uid = jdbcTemplate.queryForObject(
            "SELECT id FROM `user` WHERE username = ?", Long.class, username);
        jdbcTemplate.update("INSERT INTO user_role (user_id, role_id, created_at) VALUES (?, ?, NOW())", uid, roleId);
        return uid;
    }

    @Test
    void emptyRoleNode_blocksApply_butNotMidFlowApprove() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbcTemplate);
        String admin = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());
        // 造一个没有任何成员的空角色
        jdbcTemplate.update(
            "INSERT INTO role (tenant_id, name, code, data_scope, is_preset, status, is_deleted, created_at, updated_at) " +
            "VALUES (?, '空角色', 'empty_role', 'self', 0, 1, 0, NOW(), NOW())", seeded.tenantId());
        long emptyRoleId = jdbcTemplate.queryForObject(
            "SELECT id FROM role WHERE tenant_id = ? AND code = 'empty_role'", Long.class, seeded.tenantId());

        // 流程A:首节点就是空角色 -> 发起时严格拦截(422)
        String cfgA = ("{\"nodes\":[{\"key\":\"n1\",\"name\":\"空角色审批\",\"approverType\":\"ROLE\"," +
            "\"resolveMode\":\"FIRST\",\"orderBy\":1,\"condition\":null,\"targetRoleId\":" + emptyRoleId + "}]}")
            .replace("\"", "\\\"");
        mockMvc.perform(post("/api/v1/approval/flows")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"ea\",\"formName\":\"空角色单\",\"processKey\":\"EA\",\"configJson\":\"" + cfgA + "\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/v1/approval/instances")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"ea\",\"formData\":{}}"))
                .andExpect(jsonPath("$.code").value(422));

        // 流程B:节点1=admin,节点2=空角色 -> 发起时只解析节点1可过;admin 通过时节点2自动跳过,不报错
        String cfgB = ("{\"nodes\":[" +
            "{\"key\":\"n1\",\"name\":\"人工审批\",\"approverType\":\"SPECIFIC_USER\",\"resolveMode\":\"FIRST\"," +
            "\"orderBy\":1,\"condition\":null,\"targetUserId\":" + seeded.adminUserId() + "}," +
            "{\"key\":\"n2\",\"name\":\"空角色审批\",\"approverType\":\"ROLE\",\"resolveMode\":\"FIRST\"," +
            "\"orderBy\":2,\"condition\":null,\"targetRoleId\":" + emptyRoleId + "}]}")
            .replace("\"", "\\\"");
        mockMvc.perform(post("/api/v1/approval/flows")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"eb\",\"formName\":\"中途空角色单\",\"processKey\":\"EB\",\"configJson\":\"" + cfgB + "\"}"))
                .andExpect(jsonPath("$.code").value(0));
        long id = apply(admin, "eb");
        act(admin, id, "APPROVE"); // 不应被节点2空角色打断
        assertEquals("APPROVED", instanceStatus(id));
        String skipComment = jdbcTemplate.queryForObject(
            "SELECT comment FROM wf_node WHERE instance_id = ? AND node_key = 'n2'", String.class, id);
        assertEquals(true, skipComment != null && skipComment.contains("自动跳过"));
    }

    @Test
    void rejectToInitiator_thenResubmit_flowsAgain() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbcTemplate);
        String admin = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());
        long userB = seedSecondUser(seeded.tenantId(), seeded.roleId(), "b_" + seeded.tenantId());
        String tokenB = OrgTestSupport.login(mockMvc, objectMapper, "b_" + seeded.tenantId());

        // node1=乙(TO_INITIATOR), node2=admin
        String cfg = ("{\"nodes\":[{\"key\":\"n1\",\"name\":\"一级\",\"approverType\":\"SPECIFIC_USER\"," +
            "\"resolveMode\":\"FIRST\",\"orderBy\":1,\"condition\":null,\"targetUserId\":" + userB +
            ",\"rejectPolicy\":\"TO_INITIATOR\"}]}").replace("\"", "\\\"");
        mockMvc.perform(post("/api/v1/approval/flows")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"ri\",\"formName\":\"退回单\",\"processKey\":\"RI\",\"configJson\":\"" + cfg + "\"}"))
                .andExpect(jsonPath("$.code").value(0));

        long id = apply(admin, "ri");
        act(tokenB, id, "REJECT");
        assertEquals("RETURNED", instanceStatus(id));

        // 非发起人不能重新提交
        mockMvc.perform(put("/api/v1/approval/instances/" + id + "/resubmit")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(jsonPath("$.code").value(403));

        // 发起人重新提交 -> 重新进入审批,乙再通过 -> 整单通过
        mockMvc.perform(put("/api/v1/approval/instances/" + id + "/resubmit")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formData\":{\"fix\":1}}"))
                .andExpect(jsonPath("$.code").value(0));
        assertEquals("PENDING", instanceStatus(id));
        act(tokenB, id, "APPROVE");
        assertEquals("APPROVED", instanceStatus(id));
    }

    @Test
    void rejectToPrev_reRunsPreviousNode() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbcTemplate);
        String admin = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());
        long userB = seedSecondUser(seeded.tenantId(), seeded.roleId(), "c_" + seeded.tenantId());
        String tokenB = OrgTestSupport.login(mockMvc, objectMapper, "c_" + seeded.tenantId());

        // node1=admin, node2=乙(TO_PREV)
        createFlow(admin, "rp", seeded.adminUserId(), userB, "TO_PREV");
        long id = apply(admin, "rp");
        act(admin, id, "APPROVE");           // 一级通过 -> 到乙
        act(tokenB, id, "REJECT");           // 乙驳回 -> 退回一级重审
        assertEquals("PENDING", instanceStatus(id));
        act(admin, id, "APPROVE");           // 一级再通过 -> 回到乙
        act(tokenB, id, "APPROVE");          // 乙通过 -> 整单通过
        assertEquals("APPROVED", instanceStatus(id));
    }

    @Test
    void addSign_preAndPost() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbcTemplate);
        String admin = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());
        long userB = seedSecondUser(seeded.tenantId(), seeded.roleId(), "d_" + seeded.tenantId());
        String tokenB = OrgTestSupport.login(mockMvc, objectMapper, "d_" + seeded.tenantId());

        // 单节点流程,审批人=admin
        String cfg = ("{\"nodes\":[{\"key\":\"n1\",\"name\":\"唯一节点\",\"approverType\":\"SPECIFIC_USER\"," +
            "\"resolveMode\":\"FIRST\",\"orderBy\":1,\"condition\":null,\"targetUserId\":" + seeded.adminUserId() + "}]}")
            .replace("\"", "\\\"");
        mockMvc.perform(post("/api/v1/approval/flows")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"as\",\"formName\":\"加签单\",\"processKey\":\"AS\",\"configJson\":\"" + cfg + "\"}"))
                .andExpect(jsonPath("$.code").value(0));

        // 前加签:乙先审,通过后回到 admin,admin 通过 -> 结束
        long id1 = apply(admin, "as");
        mockMvc.perform(put("/api/v1/approval/instances/" + id1 + "/addsign")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUserId\":" + userB + ",\"position\":\"PRE\"}"))
                .andExpect(jsonPath("$.code").value(0));
        // 此刻 admin 不能审(当前节点是加签人的)
        mockMvc.perform(put("/api/v1/approval/instances/" + id1 + "/act")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\"}"))
                .andExpect(jsonPath("$.code").value(403));
        act(tokenB, id1, "APPROVE");
        assertEquals("PENDING", instanceStatus(id1));
        act(admin, id1, "APPROVE");
        assertEquals("APPROVED", instanceStatus(id1));

        // 后加签:admin 审完后到乙,乙通过 -> 结束
        long id2 = apply(admin, "as");
        mockMvc.perform(put("/api/v1/approval/instances/" + id2 + "/addsign")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetUserId\":" + userB + ",\"position\":\"POST\"}"))
                .andExpect(jsonPath("$.code").value(0));
        act(admin, id2, "APPROVE");
        assertEquals("PENDING", instanceStatus(id2));
        act(tokenB, id2, "APPROVE");
        assertEquals("APPROVED", instanceStatus(id2));
    }
}
