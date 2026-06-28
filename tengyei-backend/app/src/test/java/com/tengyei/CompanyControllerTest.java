package com.tengyei;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tengyei.auth.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class CompanyControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String superToken() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("superadmin");
        req.setPassword("Admin@2026");
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    @Test
    void super_admin_can_create_list_and_toggle_company() throws Exception {
        String token = superToken();
        String unique = "C" + System.currentTimeMillis();
        String body = """
            {"fullName":"%s 科技有限公司","shortName":"%s",
             "adminName":"张三","adminPhone":"13900000001",
             "adminUsername":"admin_%s","adminPassword":"Init@2026"}
            """.formatted(unique, unique, unique);

        MvcResult created = mockMvc.perform(post("/api/v1/companies")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andReturn();

        long id = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(get("/api/v1/companies?page=1&size=20")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isArray());

        mockMvc.perform(put("/api/v1/companies/" + id + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void company_creation_requires_super_admin_authority() throws Exception {
        // No token → 401 envelope from entry point
        mockMvc.perform(post("/api/v1/companies")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
