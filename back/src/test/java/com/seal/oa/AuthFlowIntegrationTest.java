package com.seal.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = """
        spring.datasource.url=jdbc:h2:mem:umm_oa_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1
        """)
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void loginAndReadRemainingCoreResources() throws Exception {
        String authorization = "Bearer " + login("17630135660");

        mockMvc.perform(get("/getInfo").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.user.nickName").value("方铭渴"))
                .andExpect(jsonPath("$.roles[0]").value("admin"));

        mockMvc.perform(get("/system/user/list").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(35));

        mockMvc.perform(get("/project/merchant/list").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(get("/project/business/list?pageNum=1&pageSize=10")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void retiredModulesAreAbsentAndTheirDataIsEmpty() throws Exception {
        String authorization = "Bearer " + login("17630135660");

        MvcResult routersResult = mockMvc.perform(
                        get("/getRouters").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode roots = objectMapper.readTree(routersResult.getResponse().getContentAsString())
                .path("data");
        assertEquals(6, roots.size());
        for (JsonNode root : roots) {
            String name = root.path("moduleName").asText();
            assertFalse("项目".equals(name));
            assertFalse("合同与采购".equals(name));
            assertFalse("钱票收支".equals(name));
        }

        mockMvc.perform(get("/system/module/340396")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
        mockMvc.perform(get("/system/module/340367")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
        mockMvc.perform(get("/system/module/340774")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));

        assertEquals(0, count("oa_project"));
        assertEquals(0, count("oa_contract"));
        assertEquals(0, count("oa_purchase_apply"));
        assertEquals(0, count("oa_fund_plan"));
        assertEquals(0, count("oa_expense"));
        assertEquals(0, count("oa_warehouse"));
    }

    @Test
    void organizationAndWorkflowPermissionsRemainUsable() throws Exception {
        String adminAuthorization = "Bearer " + login("17630135660");

        mockMvc.perform(get("/system/module/341001/actions?workflowOnly=true")
                        .header("Authorization", adminAuthorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].handlerKey").isNotEmpty());

        String hrAuthorization = "Bearer " + login("17600000001");
        mockMvc.perform(get("/getInfo").header("Authorization", hrAuthorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("hr_manager"))
                .andExpect(jsonPath("$.permissions").isArray());

        mockMvc.perform(get("/system/user/list").header("Authorization", hrAuthorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/system/dept/admin/tree").header("Authorization", hrAuthorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private String login(String username) throws Exception {
        MvcResult login = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "admin123123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString())
                .path("token")
                .asText();
    }

    private int count(String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
