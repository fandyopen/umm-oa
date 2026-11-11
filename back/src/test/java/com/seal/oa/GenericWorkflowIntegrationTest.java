package com.seal.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.system.ModuleActionService;
import com.seal.oa.workflow.WorkflowApiRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:generic_workflow_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class GenericWorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ModuleActionService actions;
    @Autowired WorkflowApiRegistry registry;
    @Autowired com.seal.oa.oa.CustomerFollowupReminderService customerReminders;
    String token;
    long applicantId;
    static final long CUSTOMER = 99881;

    @BeforeEach void setup() throws Exception {
        jdbc.update("INSERT INTO sys_user(user_id,tenant_id,dept_id,user_name,nick_name,phonenumber,password,status,del_flag) SELECT 9921,1,dept_id,'17600000001','审批人','17600000001',password,'0','0' FROM sys_user WHERE user_id=4 AND NOT EXISTS(SELECT 1 FROM sys_user WHERE user_name='17600000001')");
        token = login("13610316814");
        applicantId = jdbc.queryForObject("SELECT user_id FROM sys_user WHERE user_name='13610316814'", Long.class);
        jdbc.update("INSERT INTO oa_merchant(id,tenant_id,name,industry,owner_user_id,status,public_pool) VALUES (?,1,'通用流程客户','软件',?,'0','0')", CUSTOMER, applicantId);
        configure("track", followupConfig());
    }

    @Test void apiDirectoryIsScopedAndContainsMoreThanFollowup() throws Exception {
        JsonNode result = read(mvc.perform(get("/workflow/action/apis").param("moduleId", "340352").header("Authorization", "Bearer " + token)).andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertTrue(result.size() >= 3);
        assertTrue(result.findValuesAsText("name").contains("保存客户跟进"));
        result.forEach(api -> assertEquals(340352, api.path("moduleId").asLong()));
        assertTrue(registry.catalog(340772).isEmpty());
        assertTrue(registry.catalog(341201).stream().noneMatch(api -> List.of("submit", "sign").contains(api.get("requiredAction"))));
        mvc.perform(get("/workflow/action/apis").param("moduleId", "340352")).andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/workflow/action/apis").param("moduleId", "340352").header("Authorization", "Bearer " + login("17600000001"))).andExpect(jsonPath("$.code").value(403));
    }

    @Test void existingTrackIsConnectedWithoutAutomaticallyEnablingApproval() {
        assertEquals(WorkflowApiRegistry.HANDLER, actions.action(340352, "track").get("handlerKey"));
        assertEquals(0, ((Number) actions.action(340352, "track").get("flowDesignEnabled")).intValue());
    }

    @Test void directSubmissionIsIdempotentAndConflictingRetriesAreRejected() throws Exception {
        Map<String, Object> payload = followupPayload("直接保存");
        assertEquals("completed", submit("track", payload, "direct_save_0001", 200).path("status").asText());
        assertEquals("completed", submit("track", payload, "direct_save_0001", 200).path("status").asText());
        assertFollowups(1);
        submit("track", followupPayload("不同内容"), "direct_save_0001", 409);
        assertFollowups(1);
        assertEquals(applicantId, jdbc.queryForObject("SELECT owner_user_id FROM oa_customer_followup WHERE merchant_id=?", Long.class, CUSTOMER));
    }

    @Test void approvalFreezesConfigurationAndExecutesAsApplicantExactlyOnce() throws Exception {
        publish("track", true);
        JsonNode submitted = submit("track", followupPayload("审批后保存"), "approval_save_0001", 200);
        assertEquals("pendingApproval", submitted.path("status").asText());
        assertEquals(submitted.path("requestId"), submit("track", followupPayload("审批后保存"), "approval_save_0001", 200).path("requestId"));
        assertFollowups(0);
        String stored = jdbc.queryForObject("SELECT payload_json FROM oa_workflow_request WHERE id=?", String.class, submitted.path("requestId").asLong());
        assertEquals("沟通内容", read(stored).path("_workflowDisplay").get(0).path("label").asText());
        // Later button edits cannot redirect an already submitted business operation.
        configure("track", Map.of("endpoint", endpoint("deleteFollowup"), "pathMapping", Map.of("id", "missing")));
        complete(submitted, true, 200);
        assertFollowups(1);
        assertEquals(applicantId, jdbc.queryForObject("SELECT owner_user_id FROM oa_customer_followup WHERE merchant_id=?", Long.class, CUSTOMER));
        assertEquals("approved", jdbc.queryForObject("SELECT status FROM oa_workflow_request WHERE id=?", String.class, submitted.path("requestId").asLong()));
        // A repeated task completion must not execute the business write twice.
        complete(submitted, true, 200);
        assertFollowups(1);
    }

    @Test void approvalListsKeepFrozenTitlesAndDisplayFieldsAfterConfigurationChanges() throws Exception {
        publish("track", true);
        JsonNode submitted = submit("track", followupPayload("提交时的沟通内容"), "frozen_display_001", 200);
        long requestId = submitted.path("requestId").asLong();
        var changed = new java.util.LinkedHashMap<>(followupConfig());
        changed.put("titleTemplate", "之后修改的标题");
        changed.put("displayFields", List.of(Map.of("field", "nextPlan", "label", "之后修改的展示字段")));
        configure("track", changed);
        String reviewer = login("17600000001");
        for (String endpoint : List.of("taskWaitList", "taskDoneList")) {
            JsonNode response = read(mvc.perform(get("/project/activity/" + endpoint).param("pageSize", "100")
                            .header("Authorization", "Bearer " + reviewer))
                    .andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
            JsonNode task = java.util.stream.StreamSupport.stream(response.path("rows").spliterator(), false)
                    .filter(row -> row.path("formId").asLong() == requestId && "workflowAction".equals(row.path("businessType").asText()))
                    .findFirst().orElseThrow();
            assertEquals(submitted.path("title").asText(), task.path("form").path("title").asText());
            assertEquals(submitted.path("title").asText(), task.path("data").path("title").asText());
            assertEquals("沟通内容", task.path("form").path("_workflowDisplay").get(0).path("label").asText());
            assertEquals("提交时的沟通内容", task.path("form").path("_workflowDisplay").get(0).path("value").asText());
            if ("taskWaitList".equals(endpoint)) complete(submitted, true, 200);
        }
    }

    @Test void rejectedSubmissionDoesNotWriteBusinessData() throws Exception {
        publish("track", true);
        JsonNode submitted = submit("track", followupPayload("退回内容"), "reject_save_0001", 200);
        complete(submitted, false, 200);
        assertFollowups(0);
        assertEquals("rejected", jdbc.queryForObject("SELECT status FROM oa_workflow_request WHERE id=?", String.class, submitted.path("requestId").asLong()));
    }

    @Test void customerReminderCompletesOnlyWhenApprovedFollowupIsActuallySaved() throws Exception {
        customerReminders.synchronizeCustomer(CUSTOMER);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oa_todo WHERE business_type='crmCustomerFollowup' AND business_id=? AND status='todo'", Integer.class, CUSTOMER));
        publish("track", true);
        JsonNode submitted = submit("track", followupPayload("审批完成实际跟进"), "customer_reminder_01", 200);
        assertEquals("pendingApproval", submitted.path("status").asText());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oa_todo WHERE business_type='crmCustomerFollowup' AND business_id=? AND status='todo'", Integer.class, CUSTOMER));
        complete(submitted, true, 200);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM oa_todo WHERE business_type='crmCustomerFollowup' AND business_id=? AND status='todo'", Integer.class, CUSTOMER));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oa_todo WHERE business_type='crmCustomerFollowup' AND business_id=? AND status='done'", Integer.class, CUSTOMER));
    }

    @Test void approvalDrawerCanReadHistoryAndProgressBeforeAndAfterApproval() throws Exception {
        publish("track", true);
        JsonNode submitted = submit("track", followupPayload("审批历史接口"), "history_read_0001", 200);
        String instanceId = submitted.path("instanceId").asText();
        String reviewer = login("17600000001");
        mvc.perform(get("/project/activity/taskWaitList").header("Authorization", "Bearer " + reviewer))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.rows[0].instanceId").value(instanceId));
        for (String actor : List.of(token, reviewer)) {
            mvc.perform(post("/activiti/process/listHistory").header("Authorization", "Bearer " + actor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("processInstanceId", instanceId, "pageNum", 1, "pageSize", 999))))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.total").value(2))
                    .andExpect(jsonPath("$.rows[0].activityType").value("startEvent"))
                    .andExpect(jsonPath("$.rows[1].activityName").value("发起人"));
            mvc.perform(get("/activiti/process/listFlowElementsValid").header("Authorization", "Bearer " + actor)
                            .param("pProcessInstanceId", instanceId))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[2].id").value("review"))
                    .andExpect(jsonPath("$.data[2].current").value(true));
        }
        assertFollowups(0);
        complete(submitted, true, 200);
        mvc.perform(post("/activiti/process/listHistory").header("Authorization", "Bearer " + reviewer)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("processInstanceId", instanceId))))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.rows[2].activityName").value("审核"))
                .andExpect(jsonPath("$.rows[2].assigneeName").value(jdbc.queryForObject(
                        "SELECT nick_name FROM sys_user WHERE user_name='17600000001'", String.class)))
                .andExpect(jsonPath("$.rows[2].comment").value("同意"))
                .andExpect(jsonPath("$.rows[3].activityType").value("endEvent"));
        assertFollowups(1);
    }

    @Test void historyReadRejectsUnrelatedUsersAndOtherTenants() throws Exception {
        publish("track", true);
        String instanceId = submit("track", followupPayload("审批历史可见性"), "history_access_01", 200).path("instanceId").asText();
        jdbc.update("""
                INSERT INTO sys_user(user_id,tenant_id,dept_id,user_name,nick_name,phonenumber,password,status,del_flag)
                SELECT 9922,1,dept_id,'17600000002','无关用户','17600000002',password,'0','0' FROM sys_user WHERE user_id=4
                """);
        String outsider = login("17600000002");
        for (int expected : List.of(403, 404)) {
            if (expected == 404) jdbc.update("UPDATE sys_user SET tenant_id=2 WHERE user_id=9922");
            mvc.perform(post("/activiti/process/listHistory").header("Authorization", "Bearer " + outsider)
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("processInstanceId", instanceId))))
                    .andExpect(jsonPath("$.code").value(expected));
            mvc.perform(get("/activiti/process/listFlowElementsValid").header("Authorization", "Bearer " + outsider)
                            .param("pProcessInstanceId", instanceId))
                    .andExpect(jsonPath("$.code").value(expected));
        }
    }

    @Test void historyReadValidatesInstanceAndAuthentication() throws Exception {
        for (String id : List.of("", "missing-instance")) {
            int expected = id.isEmpty() ? 400 : 404;
            mvc.perform(post("/activiti/process/listHistory").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("processInstanceId", id))))
                    .andExpect(jsonPath("$.code").value(expected));
            mvc.perform(get("/activiti/process/listFlowElementsValid").header("Authorization", "Bearer " + token)
                            .param("pProcessInstanceId", id))
                    .andExpect(jsonPath("$.code").value(expected));
        }
        mvc.perform(post("/activiti/process/listHistory").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/activiti/process/listFlowElementsValid").param("pProcessInstanceId", "missing"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test void anotherBusinessInterfaceUsesNestedParameterMappingsWithoutANewExecutor() throws Exception {
        actions.createAction(340352, Map.of("actionCode", "new_customer_review", "actionName", "客户建档审批",
                "handlerKey", WorkflowApiRegistry.HANDLER, "configJson", Map.of("endpoint", endpoint("create"), "pathMapping", Map.of(),
                        "bodyMapping", Map.of("name", "form.company", "industry", "form.industry"), "titleTemplate", "客户建档：{form.company}")));
        publish("new_customer_review", true);
        JsonNode submitted = submit("new_customer_review", Map.of("form", Map.of("company", "通用配置新客户", "industry", "软件")), "another_api_0001", 200);
        assertEquals("客户建档：通用配置新客户", submitted.path("title").asText());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM oa_merchant WHERE name='通用配置新客户'", Integer.class));
        complete(submitted, true, 200);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oa_merchant WHERE name='通用配置新客户' AND industry='软件'", Integer.class));
        mvc.perform(post("/project/merchant/import").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("rows", List.of(Map.of("name", "批量绕过", "industry", "软件"))))))
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test void directApiCannotBypassEnabledApprovalButDisabledApprovalRemainsDirect() throws Exception {
        publish("track", true);
        mvc.perform(post("/project/merchant/" + CUSTOMER + "/followups").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(followupPayload("绕过"))))
                .andExpect(jsonPath("$.code").value(409));
        assertFollowups(0);
        publish("track", false);
        assertEquals("completed", submit("track", followupPayload("停用后直接保存"), "disabled_00001", 200).path("status").asText());
        assertFollowups(1);
    }

    @Test void customButtonsCanReuseAnExistingBusinessApiWithoutANewHandler() throws Exception {
        actions.createAction(340352, Map.of("actionCode", "visit_submit", "actionName", "提交拜访", "handlerKey", WorkflowApiRegistry.HANDLER, "configJson", followupConfig()));
        publish("visit_submit", true);
        assertEquals("pendingApproval", submit("visit_submit", followupPayload("新按钮复用"), "custom_button_01", 200).path("status").asText());
        assertFollowups(0);
        // The same API cannot be used through another button to evade the active approval.
        submit("track", followupPayload("另一个按钮绕过"), "alternate_00001", 409);
    }

    @Test void invalidConfigurationsAndReservedPayloadIdentityAreRejectedOrIgnored() throws Exception {
        assertThrows(com.seal.oa.common.ApiException.class, () -> configure("track", Map.of("endpoint", "POST https://example.com/save")));
        assertThrows(com.seal.oa.common.ApiException.class, () -> configure("track", Map.of("endpoint", endpoint("addFollowup"), "pathMapping", Map.of())));
        assertThrows(com.seal.oa.common.ApiException.class, () -> actions.createAction(341001, Map.of("actionCode", "bad_scope", "actionName", "跨模块", "handlerKey", WorkflowApiRegistry.HANDLER, "configJson", followupConfig())));
        Map<String, Object> payload = new java.util.LinkedHashMap<>(followupPayload("防伪造"));
        payload.put("_workflowApplicantId", 9921);
        submit("track", payload, "spoofed_user_01", 200);
        assertEquals(applicantId, jdbc.queryForObject("SELECT owner_user_id FROM oa_customer_followup WHERE merchant_id=?", Long.class, CUSTOMER));
    }

    @Test void invalidBusinessDataIsNotAcceptedAsAnApprovalApplication() throws Exception {
        publish("track", true);
        submit("track", followupPayload(""), "empty_data_0001", 400);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM oa_workflow_request WHERE module_id=340352", Integer.class));
        assertFollowups(0);
    }

    @Test void businessFailureRollsBackApprovalAndCanBeRetriedWithoutFalseSuccess() throws Exception {
        publish("track", true);
        JsonNode submitted = submit("track", followupPayload("执行时失败"), "failure_save_001", 200);
        // Commit only in-memory fixtures, so HTTP service transactions can really roll back.
        org.springframework.test.context.transaction.TestTransaction.flagForCommit();
        org.springframework.test.context.transaction.TestTransaction.end();
        try {
            jdbc.update("UPDATE sys_user SET status='1' WHERE user_id=?", applicantId);
            complete(submitted, true, 403);
            assertFollowups(0);
            assertEquals("running", jdbc.queryForObject("SELECT status FROM oa_workflow_request WHERE id=?", String.class, submitted.path("requestId").asLong()));
            assertEquals("todo", jdbc.queryForObject("SELECT status FROM oa_process_task WHERE id=?", String.class, submitted.path("taskId").asText()));
            jdbc.update("UPDATE sys_user SET status='0' WHERE user_id=?", applicantId);
            complete(submitted, true, 200);
            assertFollowups(1);
        } finally {
            jdbc.update("UPDATE sys_user SET status='0' WHERE user_id=?", applicantId);
            jdbc.update("DELETE FROM oa_customer_followup WHERE merchant_id=?", CUSTOMER);
            jdbc.update("DELETE FROM oa_merchant WHERE id=?", CUSTOMER);
            jdbc.update("DELETE FROM oa_todo WHERE process_instance_id=?", submitted.path("instanceId").asText());
            jdbc.update("DELETE FROM oa_process_task WHERE instance_id=?", submitted.path("instanceId").asText());
            jdbc.update("DELETE FROM oa_process_instance WHERE id=?", submitted.path("instanceId").asText());
            jdbc.update("DELETE FROM oa_workflow_request WHERE id=?", submitted.path("requestId").asLong());
            jdbc.update("DELETE FROM oa_workflow_action_submission WHERE user_id=? AND submission_key='failure_save_001'", applicantId);
            jdbc.update("DELETE FROM oa_module_action_flow WHERE module_id=340352 AND action_code='track'");
            jdbc.update("UPDATE sys_module_action SET flow_design_enabled=0 WHERE module_id=340352 AND action_code='track'");
            org.springframework.test.context.transaction.TestTransaction.start();
        }
    }

    String endpoint(String method) { return registry.catalog(340352).stream().filter(api -> api.get("name").equals(method) || method.equals("addFollowup") && api.get("name").equals("保存客户跟进")).findFirst().orElseThrow().get("id").toString(); }
    Map<String, Object> followupConfig() { return Map.of("endpoint", endpoint("addFollowup"), "pathMapping", Map.of("id", "customerId"), "titleTemplate", "客户跟进：{customerName}", "displayFields", List.of(Map.of("field", "content", "label", "沟通内容"))); }
    Map<String, Object> followupPayload(String content) { return Map.of("customerId", CUSTOMER, "customerName", "通用流程客户", "content", content, "method", "电话", "followupTime", "2026-11-10 10:00:00"); }
    void configure(String code, Map<String, Object> config) { actions.updateAction(340352, code, Map.of("handlerKey", WorkflowApiRegistry.HANDLER, "configJson", config)); }
    void assertFollowups(int expected) { assertEquals(expected, jdbc.queryForObject("SELECT COUNT(*) FROM oa_customer_followup WHERE merchant_id=?", Integer.class, CUSTOMER)); }
    JsonNode submit(String code, Map<String, Object> payload, String key, int expected) throws Exception {
        return read(mvc.perform(post("/workflow/action/execute").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("moduleId", 340352, "actionCode", code, "payload", payload, "submissionKey", key))))
                .andExpect(jsonPath("$.code").value(expected)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }
    void publish(String code, boolean enabled) throws Exception {
        actions.updateAction(340352, code, Map.of("flowDesignEnabled", true));
        Map<String, Object> process = Map.of("nodes", List.of(Map.of("id", "start", "type", "start", "name", "发起人"), Map.of("id", "review", "type", "approval", "name", "审核", "approver", "17600000001"), Map.of("id", "end", "type", "end", "name", "结束")), "edges", List.of(Map.of("source", "start", "target", "review"), Map.of("source", "review", "target", "end")));
        mvc.perform(post("/activiti/mongo/setProcess").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("menuId", 340352, "menuName", "客户档案", "actionCode", code, "approvalEnabled", enabled, "processData", process))))
                .andExpect(jsonPath("$.code").value(200));
    }
    void complete(JsonNode submission, boolean pass, int expected) throws Exception {
        mvc.perform(post("/activiti/process/complete").header("Authorization", "Bearer " + login("17600000001")).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("taskId", submission.path("taskId").asText(), "instanceId", submission.path("instanceId").asText(), "variables", Map.of("pass", pass, "comment", pass ? "同意" : "退回")))))
                .andExpect(jsonPath("$.code").value(expected));
    }
    String login(String username) throws Exception { return read(mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username", username, "password", "admin123123")))).andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("token").asText(); }
    JsonNode read(String value) throws Exception { return json.readTree(value); }
}
