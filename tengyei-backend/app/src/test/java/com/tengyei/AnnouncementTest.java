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
}
