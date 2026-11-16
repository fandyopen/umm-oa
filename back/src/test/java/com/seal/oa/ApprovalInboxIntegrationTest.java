package com.seal.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.JwtService;
import com.seal.oa.workflow.WorkflowApprovalContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:approval_inbox;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class ApprovalInboxIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    static final long REVIEWER = 99031, STARTER = 99032, DELEGATE = 99033, ROLE = 99033;
    static final String INSTANCE = "lc-inbox-test", TASK = "task::lc-inbox-test::review1::test";

    @BeforeEach void setup() throws Exception {
        jdbc.update("INSERT INTO sys_dept(dept_id,tenant_id,parent_id,dept_name) VALUES(99030,1,0,'测试总部'),(99031,1,99030,'测试审批部门'),(99032,1,0,'测试发起部门')");
        jdbc.update("INSERT INTO sys_role(role_id,role_name,role_key,data_scope) VALUES(?,'待审批授权测试','inbox_test','5')", ROLE);
        for (long user : List.of(REVIEWER, STARTER, DELEGATE)) {
            jdbc.update("""
                    INSERT INTO sys_user(user_id,tenant_id,dept_id,user_name,nick_name,password)
                    SELECT ?,1,?,?,?,password FROM sys_user WHERE user_id=4
                    """, user, user == STARTER ? 99032 : 99031, "inbox" + user,
                    user == REVIEWER ? "指定审批员" : user == STARTER ? "申请人" : "实际代审批员");
        }
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", DELEGATE, ROLE);
        String process = json.writeValueAsString(Map.of("nodes", List.of(
                Map.of("id", "review1", "name", "部门审批", "type", "approval", "approver", "inbox" + REVIEWER),
                Map.of("id", "review2", "name", "复核审批", "type", "approval", "approver", "inbox" + REVIEWER))));
        jdbc.update("INSERT INTO oa_form_definition(form_key,form_type,flow_name,process_data) VALUES('inbox-test','flow','测试审批',?)", process);
        jdbc.update("INSERT INTO oa_form_submission(id,form_key,data_json) VALUES(99031,'inbox-test','{\"申请内容\":\"冻结内容\"}')");
        jdbc.update("INSERT INTO oa_process_instance(id,tenant_id,process_key,business_type,business_id,starter_id,starter_name,status) VALUES(?,1,'form:inbox-test','lowcode',99031,?,'申请人','running')", INSTANCE, STARTER);
        jdbc.update("INSERT INTO oa_process_task(id,instance_id,task_name,assignee_id,assignee_name,status) VALUES(?,?,'部门审批',?,'指定审批员','todo')", TASK, INSTANCE, REVIEWER);
        jdbc.update("INSERT INTO oa_todo(tenant_id,user_id,title,business_type,business_id,process_instance_id,task_id,status) VALUES(1,?,'测试审批事项','lowcode',99031,?,?,'todo')", REVIEWER, INSTANCE, TASK);
    }

    @Test void defaultsToSuperAdminAndModuleSelectionOnlyGrantsReading() throws Exception {
        call(null, get("/project/approvals"), null, 401);
        call(DELEGATE, get("/project/approvals"), null, 403);
        Long admin = jdbc.queryForObject("SELECT user_id FROM sys_user WHERE user_name='13610316814'", Long.class);
        assertTrue(call(admin, get("/project/approvals/options"), null, 200).path("data").path("canApprove").asBoolean());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_module WHERE module_id=341600", Integer.class));
        pageGrant();
        assertFalse(call(DELEGATE, get("/project/approvals/options"), null, 200).path("data").path("canApprove").asBoolean());
        call(DELEGATE, get("/project/approvals/detail").param("taskId", TASK), null, 200);
        call(DELEGATE, post("/project/approvals/decision"), decision(TASK, true, "同意"), 403);
        assertEquals("todo", taskStatus());
    }

    @Test void actionGrantWithoutPageAndRevokedGrantsCannotApprove() throws Exception {
        actionGrant();
        call(DELEGATE, post("/project/approvals/decision"), decision(TASK, true, "同意"), 403);
        pageGrant();
        assertTrue(call(DELEGATE, get("/project/approvals/detail").param("taskId", TASK), null, 200).path("data").path("canApprove").asBoolean());
        jdbc.update("DELETE FROM sys_role_module_action WHERE role_id=? AND module_id=341600", ROLE);
        call(DELEGATE, post("/project/approvals/decision"), decision(TASK, true, "同意"), 403);
        assertEquals("todo", taskStatus());
    }

    @Test void onlyApprovalsAreListedWithBothPeopleAndDepartmentFilters() throws Exception {
        pageGrant();
        for (String type : List.of("crmCustomerFollowup", "crmOpportunityFollowup", "crmRisk", "hrPayroll", "hrPayslip", "hrHandover"))
            jdbc.update("INSERT INTO oa_todo(tenant_id,user_id,title,business_type,status) VALUES(1,?,'不是审批',?,'todo')", REVIEWER, type);
        JsonNode list = call(DELEGATE, get("/project/approvals").param("keyword", "测试审批事项").param("deptId", "99030").param("starterId", "" + STARTER).param("assigneeId", "" + REVIEWER), null, 200).path("data");
        assertEquals(1, list.path("total").asInt());
        JsonNode row = list.path("rows").get(0);
        assertEquals("申请人", row.path("starterName").asText());
        assertEquals("测试发起部门", row.path("starterDeptName").asText());
        assertEquals("指定审批员", row.path("assigneeName").asText());
        assertEquals("测试审批部门", row.path("approverDeptName").asText());
        assertEquals(0, call(DELEGATE, get("/project/approvals").param("deptId", "99032").param("keyword", "测试审批事项"), null, 200).path("data").path("total").asInt());
    }

    @Test void delegationKeepsAssignmentAndActualActorAndAdvancesOnlyOneNode() throws Exception {
        grant();
        JsonNode result = call(DELEGATE, post("/project/approvals/decision"), decision(TASK, true, "核对无误"), 200).path("data");
        assertEquals("done", taskStatus());
        assertNotEquals(TASK, result.path("taskId").asText());
        assertEquals("running", jdbc.queryForObject("SELECT status FROM oa_process_instance WHERE id=?", String.class, INSTANCE));
        assertEquals(REVIEWER, jdbc.queryForObject("SELECT assignee_id FROM oa_process_task WHERE id=?", Long.class, TASK));
        assertEquals(DELEGATE, jdbc.queryForObject("SELECT operator_id FROM oa_approval_decision WHERE task_id=?", Long.class, TASK));
        assertEquals(1, jdbc.queryForObject("SELECT delegated FROM oa_approval_decision WHERE task_id=?", Integer.class, TASK));
        JsonNode detail = call(DELEGATE, get("/project/approvals/detail").param("taskId", TASK), null, 200).path("data");
        assertEquals("实际代审批员", detail.path("approvalAudit").path("operatorName").asText());
        assertEquals("指定审批员", detail.path("approvalAudit").path("assigneeName").asText());
        assertEquals("核对无误", detail.path("approvalAudit").path("comment").asText());
        assertEquals(2, detail.path("approvalRecords").size());
        assertFalse(detail.path("canApprove").asBoolean());
        JsonNode done = call(DELEGATE, get("/project/approvals").param("tab", "done").param("keyword", "测试审批事项"), null, 200).path("data").path("rows").get(0);
        assertEquals("实际代审批员", done.path("operatorName").asText());
        JsonNode history = call(DELEGATE, post("/activiti/process/listHistory"), Map.of("processInstanceId", INSTANCE), 200);
        assertTrue(history.toString().contains("实际代审批员（代 指定审批员 审批）"));
        call(DELEGATE, post("/project/approvals/decision"), decision(TASK, false, "重复操作"), 409);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oa_approval_decision WHERE task_id=?", Integer.class, TASK));
        assertFalse(WorkflowApprovalContext.active(DELEGATE));
    }

    @Test void rejectionRequiresReasonAndTerminatesOriginalFlow() throws Exception {
        grant();
        call(DELEGATE, post("/project/approvals/decision"), decision(TASK, false, ""), 400);
        assertEquals("todo", taskStatus());
        call(DELEGATE, post("/project/approvals/decision"), decision(TASK, false, "补充资料"), 200);
        assertEquals("rejected", jdbc.queryForObject("SELECT status FROM oa_process_instance WHERE id=?", String.class, INSTANCE));
        assertEquals("rejected", jdbc.queryForObject("SELECT decision FROM oa_approval_decision WHERE task_id=?", String.class, TASK));
    }

    @Test void selfApprovalAndFakeClientFlagsAndOtherTenantsAreRejected() throws Exception {
        grant();
        call(DELEGATE, post("/project/activity/complete"), Map.of("taskId", TASK, "instanceId", INSTANCE, "approvalInbox", true, "variables", Map.of("pass", true)), 403);
        jdbc.update("UPDATE oa_process_instance SET starter_id=? WHERE id=?", DELEGATE, INSTANCE);
        assertFalse(call(DELEGATE, get("/project/approvals/detail").param("taskId", TASK), null, 200).path("data").path("canApprove").asBoolean());
        call(DELEGATE, post("/project/approvals/decision"), decision(TASK, true, "同意"), 403);
        call(DELEGATE, post("/project/approvals/decision"), Map.of("taskId", TASK, "pass", true, "selfApprovalAllowed", true, "superAdmin", true), 403);
        jdbc.update("UPDATE oa_process_instance SET tenant_id=2 WHERE id=?", INSTANCE);
        call(DELEGATE, get("/project/approvals/detail").param("taskId", TASK), null, 404);
        call(DELEGATE, post("/project/approvals/decision"), decision(TASK, true, "同意"), 404);
        assertEquals(0, call(DELEGATE, get("/project/approvals").param("keyword", "测试审批事项"), null, 200).path("data").path("total").asInt());
        assertFalse(WorkflowApprovalContext.active(DELEGATE));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void superAdminCanDecideOwnApplicationWithActualActorAudit(boolean pass) throws Exception {
        Long admin = jdbc.queryForObject("SELECT user_id FROM sys_user WHERE user_name='13610316814'", Long.class);
        jdbc.update("UPDATE oa_process_instance SET starter_id=?,starter_name='超级管理员' WHERE id=?", admin, INSTANCE);
        JsonNode row = call(admin, get("/project/approvals").param("keyword", "测试审批事项"), null, 200).path("data").path("rows").get(0);
        assertTrue(row.path("canApprove").asBoolean());
        assertTrue(call(admin, get("/project/approvals/detail").param("taskId", TASK), null, 200).path("data").path("canApprove").asBoolean());
        call(admin, post("/project/activity/complete"), Map.of("taskId", TASK, "instanceId", INSTANCE, "variables", Map.of("pass", pass)), 403);
        call(admin, post("/project/approvals/decision"), decision(TASK, pass, "超管代审本人申请"), 200);
        assertEquals("done", taskStatus());
        assertEquals(admin, jdbc.queryForObject("SELECT operator_id FROM oa_approval_decision WHERE task_id=?", Long.class, TASK));
        assertEquals(REVIEWER, jdbc.queryForObject("SELECT assignee_id FROM oa_approval_decision WHERE task_id=?", Long.class, TASK));
        assertEquals(pass ? "approved" : "rejected", jdbc.queryForObject("SELECT decision FROM oa_approval_decision WHERE task_id=?", String.class, TASK));
        assertEquals(pass ? "running" : "rejected", jdbc.queryForObject("SELECT status FROM oa_process_instance WHERE id=?", String.class, INSTANCE));
        assertFalse(WorkflowApprovalContext.active(admin));
        assertFalse(WorkflowApprovalContext.allowsSelfApproval(admin));
        call(admin, post("/project/approvals/decision"), decision(TASK, pass, "重复操作"), 409);
    }

    @Test void stoppedAndWithdrawnTasksCannotBeApproved() throws Exception {
        grant();
        jdbc.update("UPDATE oa_process_instance SET status='withdrawn' WHERE id=?", INSTANCE);
        call(DELEGATE, post("/project/approvals/decision"), decision(TASK, true, "同意"), 409);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM oa_approval_decision WHERE task_id=?", Integer.class, TASK));
    }

    @Test void normalReviewerAlsoRecordsActualActorWithoutDelegation() throws Exception {
        call(REVIEWER, post("/project/activity/complete"), Map.of("taskId", TASK, "instanceId", INSTANCE, "variables", Map.of("pass", true, "comment", "本人审批")), 200);
        assertEquals(REVIEWER, jdbc.queryForObject("SELECT operator_id FROM oa_approval_decision WHERE task_id=?", Long.class, TASK));
        assertEquals(0, jdbc.queryForObject("SELECT delegated FROM oa_approval_decision WHERE task_id=?", Integer.class, TASK));
    }

    private void pageGrant() { jdbc.update("INSERT INTO sys_role_module(role_id,module_id) VALUES(?,341600)", ROLE); }
    private void actionGrant() { jdbc.update("INSERT INTO sys_role_module_action(role_id,module_id,action_code) VALUES(?,341600,'approve')", ROLE); }
    private void grant() { pageGrant(); actionGrant(); }
    private String taskStatus() { return jdbc.queryForObject("SELECT status FROM oa_process_task WHERE id=?", String.class, TASK); }
    private Map<String, Object> decision(String task, boolean pass, String comment) { return Map.of("taskId", task, "pass", pass, "comment", comment); }
    private JsonNode call(Long user, MockHttpServletRequestBuilder request, Object body, int code) throws Exception {
        if (user != null) request.header("Authorization", "Bearer " + jwt.createToken(user, "inbox" + user));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        String response = mvc.perform(request).andExpect(jsonPath("$.code").value(code)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(response);
    }
}
