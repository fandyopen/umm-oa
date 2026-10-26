package com.seal.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.system.SystemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SystemService systemService;

    @Test
    void loginAndReadCoreResources() throws Exception {
        MvcResult captchaResult = mockMvc.perform(get("/captchaImage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.uuid").isNotEmpty())
                .andReturn();
        JsonNode captcha = objectMapper.readTree(captchaResult.getResponse().getContentAsString());

        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "username", "17630135660",
                "password", "admin123123",
                "uuid", captcha.get("uuid").asText(),
                "code", captcha.get("debugCode").asText()
        ));

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("token")
                .asText();

        mockMvc.perform(get("/getInfo").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.user.nickName").value("系统管理员"))
                .andExpect(jsonPath("$.roles[0]").value("admin"));

        mockMvc.perform(get("/project/project/list").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].name").value("企业数字化办公平台"));

        mockMvc.perform(get("/project/analysis/project/sale").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    void protectedEndpointRejectsAnonymousRequest() throws Exception {
        mockMvc.perform(get("/getInfo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void demoRoleAccountsExistAndCanAuthenticate() {
        String[] usernames = {
                "17673605591",
                "15685266238",
                "17689037697",
                "15646844893",
                "13689213154",
                "15650389435",
                "15609825919",
                "17655872713"
        };

        for (String username : usernames) {
            SystemService.UserAccount account = systemService.findLoginUser(username).orElseThrow();
            org.junit.jupiter.api.Assertions.assertTrue(
                    systemService.passwordMatches(account, "admin123123"),
                    "Demo password must match for " + username
            );
        }

        SystemService.UserAccount projectManager = systemService.findLoginUser("15650389435").orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(
                systemService.permissions(projectManager.userId()).contains("eos:gzt:xmzg")
        );
        org.junit.jupiter.api.Assertions.assertTrue(
                systemService.posts(projectManager.userId()).contains("project_manager")
        );
    }
}
