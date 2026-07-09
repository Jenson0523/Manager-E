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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 横幅公告:平台定向/全员发布、公司自发自见、越权拦截、系统计算横幅(待办审批) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class AnnouncementTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void platformTargetingAndCompanySelfAndComputedBanner() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbcTemplate);
        String companyToken = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());
        String platformToken = OrgTestSupport.login(mockMvc, objectMapper, "superadmin");

        // 平台发全员公告 -> 公司能看到
        mockMvc.perform(post("/api/v1/announcements")
                .header("Authorization", "Bearer " + platformToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"全员系统升级公告\",\"content\":\"周末停机\",\"level\":\"WARN\"," +
                    "\"targetScope\":\"ALL_COMPANIES\"}"))
                .andExpect(jsonPath("$.code").value(0));
        // 平台发定向公告(目标=不存在的企业999) -> 本公司不应看到
        mockMvc.perform(post("/api/v1/announcements")
                .header("Authorization", "Bearer " + platformToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"定向他司公告\",\"targetScope\":\"COMPANIES\",\"targetIds\":[999]}"))
                .andExpect(jsonPath("$.code").value(0));
        // 公司自发公告
        mockMvc.perform(post("/api/v1/announcements")
                .header("Authorization", "Bearer " + companyToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"公司内部通知\",\"level\":\"INFO\"}"))
                .andExpect(jsonPath("$.code").value(0));
        // 公司越权发全员 -> 403
        mockMvc.perform(post("/api/v1/announcements")
                .header("Authorization", "Bearer " + companyToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"越权\",\"targetScope\":\"ALL_COMPANIES\"}"))
                .andExpect(jsonPath("$.code").value(403));

        // 公司内定向:发给不存在的部门888 -> 自己看不到;发给自己的角色 -> 看得到
        mockMvc.perform(post("/api/v1/announcements")
                .header("Authorization", "Bearer " + companyToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"他部门通知\",\"audienceType\":\"DEPT\",\"audienceIds\":[888]}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/v1/announcements")
                .header("Authorization", "Bearer " + companyToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"本角色通知\",\"audienceType\":\"ROLE\",\"audienceIds\":[" + seeded.roleId() + "]}"))
                .andExpect(jsonPath("$.code").value(0));

        // 公司 /active: 看到 全员公告+自发公告+本角色定向,看不到 定向他司/他部门
        mockMvc.perform(get("/api/v1/announcements/active")
                .header("Authorization", "Bearer " + companyToken))
                .andExpect(jsonPath("$.data[*].title", hasItem("全员系统升级公告")))
                .andExpect(jsonPath("$.data[*].title", hasItem("公司内部通知")))
                .andExpect(jsonPath("$.data[*].title", hasItem("本角色通知")))
                .andExpect(jsonPath("$.data[*].title", not(hasItem("他部门通知"))))
                .andExpect(jsonPath("$.data[*].title", not(hasItem("定向他司公告"))));
        // 平台 /active: 看不到公司自发的
        mockMvc.perform(get("/api/v1/announcements/active")
                .header("Authorization", "Bearer " + platformToken))
                .andExpect(jsonPath("$.data[*].title", not(hasItem("公司内部通知"))));

        // 详情:发布的公告(公司自发)带发布人姓名和角色;定向他司的详情对本公司 403
        String selfListJson = mockMvc.perform(get("/api/v1/announcements")
                .header("Authorization", "Bearer " + companyToken))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        long selfId = objectMapper.readTree(selfListJson).get("data").get(0).get("id").asLong();
        mockMvc.perform(get("/api/v1/announcements/" + selfId)
                .header("Authorization", "Bearer " + companyToken))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.publisherName").isNotEmpty())
                .andExpect(jsonPath("$.data.publisherRoles", hasItem("企业管理员")))
                .andExpect(jsonPath("$.data.source").value("本公司"));
        String platListJson = mockMvc.perform(get("/api/v1/announcements")
                .header("Authorization", "Bearer " + platformToken))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        long otherId = -1;
        for (var node : objectMapper.readTree(platListJson).get("data")) {
            if ("COMPANIES".equals(node.get("targetScope").asText())) otherId = node.get("id").asLong();
        }
        mockMvc.perform(get("/api/v1/announcements/" + otherId)
                .header("Authorization", "Bearer " + companyToken))
                .andExpect(jsonPath("$.code").value(403));

        // 系统计算横幅:造一条待办审批 -> /active 出现待办提醒(带跳转链接)
        String cfg = ("{\"nodes\":[{\"key\":\"n1\",\"name\":\"审批\",\"approverType\":\"SPECIFIC_USER\"," +
            "\"resolveMode\":\"FIRST\",\"orderBy\":1,\"condition\":null,\"targetUserId\":" + seeded.adminUserId() + "}]}")
            .replace("\"", "\\\"");
        mockMvc.perform(post("/api/v1/approval/flows")
                .header("Authorization", "Bearer " + companyToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"bn\",\"formName\":\"横幅单\",\"processKey\":\"BN\"," +
                    "\"configJson\":\"" + cfg + "\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/v1/approval/instances")
                .header("Authorization", "Bearer " + companyToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formType\":\"bn\",\"formData\":{}}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/v1/announcements/active")
                .header("Authorization", "Bearer " + companyToken))
                .andExpect(jsonPath("$.data[*].linkUrl", hasItem("/company/approval")));
    }

    @Test
    void viewOnlyUser_canListAndViewDetail_butCannotManageOrDisable() throws Exception {
        var seeded = OrgTestSupport.seedCompanyAdmin(jdbcTemplate);
        String adminToken = OrgTestSupport.login(mockMvc, objectMapper, seeded.username());
        mockMvc.perform(post("/api/v1/announcements")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"给全员的通知\",\"level\":\"INFO\"}"))
                .andExpect(jsonPath("$.code").value(0));
        String listJson = mockMvc.perform(get("/api/v1/announcements")
                .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        long annId = objectMapper.readTree(listJson).get("data").get(0).get("id").asLong();

        // 仅授 view 权限的普通员工(测试租户全新,角色码不会冲突)
        jdbcTemplate.update(
            "INSERT INTO role (tenant_id, name, code, data_scope, is_preset, status, is_deleted, created_at, updated_at) " +
            "VALUES (?, '普通员工', 'viewer_role', 'self', 0, 1, 0, NOW(), NOW())", seeded.tenantId());
        long roleId = jdbcTemplate.queryForObject(
            "SELECT id FROM role WHERE tenant_id = ? AND code = 'viewer_role'", Long.class, seeded.tenantId());
        jdbcTemplate.update(
            "INSERT INTO role_permission (role_id, permission_id, created_at) " +
            "SELECT ?, id, NOW() FROM permission WHERE code = 'announcement:view'", roleId);
        jdbcTemplate.update(
            "INSERT INTO `user` (tenant_id, user_no, username, password, real_name, phone, " +
            "is_super_admin, status, pwd_reset_required, is_deleted, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, '普通员工', '13900000002', 0, 1, 0, 0, NOW(), NOW())",
            seeded.tenantId(), "U" + seeded.tenantId() + "-0002", "viewer_" + seeded.tenantId(),
            OrgTestSupport.ADMIN_PWD_HASH);
        Long viewerUserId = jdbcTemplate.queryForObject(
            "SELECT id FROM `user` WHERE username = ?", Long.class, "viewer_" + seeded.tenantId());
        jdbcTemplate.update("INSERT INTO user_role (user_id, role_id, created_at) VALUES (?, ?, NOW())",
            viewerUserId, roleId);
        String viewerToken = OrgTestSupport.login(mockMvc, objectMapper, "viewer_" + seeded.tenantId());

        // 能看列表 + 能看详情(历史记录)
        mockMvc.perform(get("/api/v1/announcements")
                .header("Authorization", "Bearer " + viewerToken))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/v1/announcements/" + annId)
                .header("Authorization", "Bearer " + viewerToken))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("给全员的通知"));

        // 不能发布、不能停用、不能删除
        mockMvc.perform(post("/api/v1/announcements")
                .header("Authorization", "Bearer " + viewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"越权发布\",\"level\":\"INFO\"}"))
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(put("/api/v1/announcements/" + annId + "/status")
                .header("Authorization", "Bearer " + viewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":0}"))
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(delete("/api/v1/announcements/" + annId)
                .header("Authorization", "Bearer " + viewerToken))
                .andExpect(jsonPath("$.code").value(403));

        // 管理员(拥有 disable 权限)可以停用
        mockMvc.perform(put("/api/v1/announcements/" + annId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":0}"))
                .andExpect(jsonPath("$.code").value(0));
    }
}
