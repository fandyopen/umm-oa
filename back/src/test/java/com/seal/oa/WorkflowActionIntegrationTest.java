package com.seal.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.system.ModuleActionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = """
        spring.datasource.url=jdbc:h2:mem:umm_oa_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1
        """)
@AutoConfigureMockMvc
@Transactional
class WorkflowActionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ModuleActionService moduleActionService;

    @Test
    void actionApprovalCreatesTodoAndExecutesAfterApproval() throws Exception {
        String adminToken = login("17630135660");
        mockMvc.perform(get("/system/module/341001/actions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data[0].actionCode").value("invite"));
        mockMvc.perform(get("/system/module/341001/actions")
                        .param("workflowOnly", "true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data[0].handlerKey").isNotEmpty());
        mockMvc.perform(get("/system/module/340396")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
        publishFlow(adminToken, "invite", true);
        mockMvc.perform(post("/activiti/mongo/detailProcess")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tenantAppId", 1,
                                "menuId", 341001,
                                "actionCode", "invite"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionCode").value("invite"))
                .andExpect(jsonPath("$.approvalEnabled").value(true))
                .andExpect(jsonPath("$.processData.nodes[1].type").value("approval"));
        String phone = "13900001111";

        MvcResult submitResult = mockMvc.perform(post("/workflow/action/execute")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "moduleId", 341001,
                                "actionCode", "invite",
                                "payload", Map.of(
                                        "nickName", "审批邀请成员",
                                        "phonenumber", phone,
                                        "deptId", 324
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("pendingApproval"))
                .andExpect(jsonPath("$.data.instanceId").isNotEmpty())
                .andExpect(jsonPath("$.data.taskId").isNotEmpty())
                .andReturn();
        JsonNode submitted = objectMapper.readTree(
                submitResult.getResponse().getContentAsString()
        ).path("data");
        assertUserCount(phone, 0);

        String approverToken = login("17600000001");
        mockMvc.perform(get("/activiti/mongoWorkflow/taskWaitList")
                        .header("Authorization", "Bearer " + approverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", greaterThan(0)));

        mockMvc.perform(post("/activiti/process/complete")
                        .header("Authorization", "Bearer " + approverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "taskId", submitted.path("taskId").asText(),
                                "instanceId", submitted.path("instanceId").asText(),
                                "variables", Map.of("pass", true, "comment", "同意")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertUserCount(phone, 1);
        String requestStatus = jdbc.queryForObject("""
                SELECT status FROM oa_workflow_request WHERE id = ?
                """, String.class, submitted.path("requestId").asLong());
        org.junit.jupiter.api.Assertions.assertEquals("approved", requestStatus);
    }

    @Test
    void disabledApprovalExecutesActionImmediately() throws Exception {
        String adminToken = login("17630135660");
        publishFlow(adminToken, "invite", false);
        String phone = "13900002222";

        mockMvc.perform(post("/workflow/action/execute")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "moduleId", 341001,
                                "actionCode", "invite",
                                "payload", Map.of(
                                        "nickName", "直接邀请成员",
                                        "phonenumber", phone,
                                        "deptId", 324
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("completed"));

        assertUserCount(phone, 1);
    }

    @Test
    void customActionUsesConfiguredHandlerAcrossBootstrap() throws Exception {
        String adminToken = login("17630135660");
        mockMvc.perform(post("/system/module/341001/actions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actionCode", "flow_test",
                                "actionName", "流程测试",
                                "handlerKey", "workflow.noop",
                                "configJson", "{}",
                                "status", "0"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        moduleActionService.bootstrap();
        JsonNode actions = objectMapper.readTree(mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/system/module/341001/actions")
                                .header("Authorization", "Bearer " + adminToken))
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        boolean found = false;
        for (JsonNode action : actions) {
            if ("flow_test".equals(action.path("actionCode").asText())) {
                found = true;
                assertEquals(0, action.path("builtin").asInt());
                assertEquals("workflow.noop", action.path("handlerKey").asText());
            }
        }
        assertTrue(found);

        publishFlow(adminToken, "flow_test", true);
        MvcResult submitResult = mockMvc.perform(post("/workflow/action/execute")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "moduleId", 341001,
                                "actionCode", "flow_test",
                                "payload", Map.of("remark", "handlKey registry test")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("pendingApproval"))
                .andReturn();
        JsonNode submitted = objectMapper.readTree(
                submitResult.getResponse().getContentAsString()
        ).path("data");

        String approverToken = login("17600000001");
        mockMvc.perform(post("/activiti/process/complete")
                        .header("Authorization", "Bearer " + approverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "taskId", submitted.path("taskId").asText(),
                                "instanceId", submitted.path("instanceId").asText(),
                                "variables", Map.of("pass", true, "comment", "同意")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String result = jdbc.queryForObject("""
                SELECT result_json FROM oa_workflow_request WHERE id = ?
                """, String.class, submitted.path("requestId").asLong());
        assertTrue(result.contains("流程审批已完成"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/system/module/341001/actions/flow_test")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private void publishFlow(String token, String actionCode, boolean approvalEnabled) throws Exception {
        Map<String, Object> processData = Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "start", "type", "start", "name", "发起人"),
                        Map.of(
                                "id", "approval_1",
                                "type", "approval",
                                "name", "人事经理审批",
                                "approver", "17600000001"
                        ),
                        Map.of("id", "end", "type", "end", "name", "流程结束")
                ),
                "edges", java.util.List.of(
                        Map.of("source", "start", "target", "approval_1"),
                        Map.of("source", "approval_1", "target", "end")
                )
        );
        mockMvc.perform(post("/activiti/mongo/setProcess")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tenantAppId", 1,
                                "menuId", 341001,
                                "menuName", "成员管理",
                                "actionCode", actionCode,
                                "approvalEnabled", approvalEnabled,
                                "processData", processData
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private String login(String username) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "admin123123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("token")
                .asText();
    }

    private void assertUserCount(String phone, int expected) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE phonenumber = ?",
                Integer.class,
                phone
        );
        org.junit.jupiter.api.Assertions.assertEquals(expected, count);
    }
}
