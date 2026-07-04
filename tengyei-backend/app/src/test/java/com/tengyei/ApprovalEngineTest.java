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
