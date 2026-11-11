package com.seal.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.oa.BusinessFollowupReminderService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:business_followup_reminders;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class BusinessFollowupReminderIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ModuleActionService actions;
    String admin, owner, peer;
    static final String TYPE = BusinessFollowupReminderService.BUSINESS_TYPE;

    @BeforeEach void setup() throws Exception {
        jdbc.update("INSERT INTO sys_role(role_id, role_name, role_key, data_scope) VALUES (9801, '商机待办测试', 'business_reminder_test', '5')");
        jdbc.update("INSERT INTO sys_role_module(role_id, module_id) VALUES (9801, 340590)");
        for (String action : List.of("add", "edit", "remove", "track")) {
            jdbc.update("INSERT INTO sys_role_module_action(role_id, module_id, action_code) VALUES (9801, 340590, ?)", action);
        }
        for (long id : List.of(9801L, 9802L)) {
            jdbc.update("""
                    INSERT INTO sys_user(user_id, dept_id, user_name, nick_name, password)
                    SELECT ?, 326, ?, ?, password FROM sys_user WHERE user_id=4
                    """, id, "opportunity-owner-" + id, "商机负责人" + id);
            jdbc.update("INSERT INTO sys_user_role(user_id, role_id) VALUES (?, 9801)", id);
        }
        admin = login("13610316814");
        owner = login("opportunity-owner-9801");
        peer = login("opportunity-owner-9802");
    }

    @Test void creationNotifiesOnlySelectedOwnerAndOpeningDetailsDoesNotComplete() throws Exception {
        long id = create();
        JsonNode task = tasks(owner, id).get(0);
        assertEquals(1, count(id, "todo"));
        assertEquals(0, tasks(peer, id).size());
        assertEquals(0, tasks(admin, id).size());
        assertEquals("商机首次跟进", task.path("processName").asText());
        assertEquals("请推进商机阶段", task.path("taskName").asText());
        assertEquals(id, task.path("form").path("businessId").asLong());
        assertEquals("客户测试", task.path("form").path("customerName").asText());
        assertEquals(10000, task.path("form").path("expectedAmount").asInt());
        assertFalse(task.path("createTime").asText().isBlank());
        assertFalse(task.path("applyUserName").asText().isBlank());
        call(owner, get("/project/business/" + id), null, 200);
        assertEquals(1, count(id, "todo"));
        assertEquals(1, call(owner, get("/project/activity/taskWaitList").param("queryAll", "待办测试商机"), null, 200).path("total").asInt());
    }

    @Test void selfAssignmentAlsoNotifiesTheCreator() throws Exception {
        long id = call(owner, post("/project/business"), body(), 200).path("data").asLong();
        assertEquals(1, tasks(owner, id).size());
    }

    @Test void ordinaryEditsPreservePushTimeAndTransfersCreateOnlyOneFreshPendingTask() throws Exception {
        long id = create();
        jdbc.update("UPDATE oa_todo SET create_time='2026-01-01 10:00:00' WHERE business_type=? AND business_id=?", TYPE, id);
        JsonNode first = tasks(owner, id).get(0);
        call(admin, put("/project/business"), Map.of("id", id, "name", "改名商机"), 200);
        call(admin, put("/project/business"), Map.of("id", id, "remark", "修改备注"), 200);
        JsonNode unchanged = tasks(owner, id).get(0);
        assertEquals(first.path("createTime"), unchanged.path("createTime"));
        assertEquals(first.path("taskId"), unchanged.path("taskId"));
        assertEquals("商机待跟进：改名商机", unchanged.path("title").asText());
        call(admin, put("/project/business"), Map.of("id", id, "ownerUserId", 9802), 200);
        assertEquals(0, tasks(owner, id).size());
        assertEquals(1, tasks(peer, id).size());
        call(admin, put("/project/business"), Map.of("id", id, "ownerUserId", 9802), 200);
        assertEquals(1, count(id, "todo"));
        assertEquals(1, count(id, "cancelled"));
        call(admin, put("/project/business"), Map.of("id", id, "ownerUserId", 9801), 200);
        assertEquals(1, tasks(owner, id).size());
        assertNotEquals(first.path("taskId"), tasks(owner, id).get(0).path("taskId"));
    }

    @Test void firstActualProgressCompletesAndOrdinaryEditsDoNotReopen() throws Exception {
        long id = create();
        advance(owner, id, "方案报价", 400);
        assertEquals(1, count(id, "todo"));
        advance(peer, id, "需求确认", 403);
        assertEquals(1, count(id, "todo"));
        advance(owner, id, "需求确认", 200);
        assertEquals(0, count(id, "todo"));
        assertEquals(1, count(id, "done"));
        call(admin, put("/project/business"), Map.of("id", id, "ownerUserId", 9802), 200);
        assertEquals(0, count(id, "todo"));
        assertEquals(1, count(id, null));
        JsonNode done = call(owner, get("/project/activity/taskDoneList"), null, 200).path("rows");
        assertTrue(StreamSupport.stream(done.spliterator(), false).anyMatch(task -> TYPE.equals(task.path("businessType").asText()) && task.path("businessId").asLong() == id));
    }

    @Test void genericApprovalOperationsCannotCompleteFollowupReminders() throws Exception {
        long id = create();
        JsonNode task = tasks(owner, id).get(0);
        for (String endpoint : List.of("complete", "back", "delegate", "end/process")) {
            call(owner, post("/project/activity/" + endpoint), Map.of("taskId", task.path("taskId").asText()), 409);
        }
        call(owner, post("/project/activity/complete"), Map.of("todoId", task.path("id").asLong()), 409);
        assertEquals(1, count(id, "todo"));
    }

    @Test void endingOrDeletingOpportunityCancelsItsOutstandingReminder() throws Exception {
        long ended = create();
        call(admin, put("/project/business"), Map.of("id", ended, "statusLabel", "已输单"), 200);
        assertEquals(0, count(ended, "todo"));
        assertEquals(1, count(ended, "cancelled"));
        long deleted = create();
        call(admin, delete("/project/business/" + deleted), null, 200);
        assertEquals(0, count(deleted, "todo"));
        assertEquals(1, count(deleted, "cancelled"));
        long next = create();
        assertNotEquals(deleted, next);
        assertEquals(1, count(next, "todo"));
    }

    @Test void configuredApprovalCreatesBusinessAndReminderOnlyAfterApprovalAndRetriesAreIdempotent() throws Exception {
        configureCreateApproval();
        Map<String, Object> submission = Map.of("moduleId", 340590, "actionCode", "add", "payload", body(), "submissionKey", "opportunity_create_001");
        JsonNode pending = call(admin, post("/workflow/action/execute"), submission, 200).path("data");
        assertEquals("pendingApproval", pending.path("status").asText());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM oa_todo WHERE business_type=?", Integer.class, TYPE));
        call(peer, post("/activiti/process/complete"), Map.of("taskId", pending.path("taskId").asText(), "instanceId", pending.path("instanceId").asText(), "variables", Map.of("pass", true)), 200);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oa_todo WHERE business_type=? AND user_id=9801 AND status='todo'", Integer.class, TYPE));
        call(admin, post("/workflow/action/execute"), submission, 200);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oa_todo WHERE business_type=?", Integer.class, TYPE));
    }

    @Test void rejectedCreationDoesNotSendAFollowupReminder() throws Exception {
        configureCreateApproval();
        JsonNode pending = call(admin, post("/workflow/action/execute"), Map.of("moduleId", 340590, "actionCode", "add", "payload", body(), "submissionKey", "opportunity_reject_001"), 200).path("data");
        call(peer, post("/activiti/process/complete"), Map.of("taskId", pending.path("taskId").asText(), "instanceId", pending.path("instanceId").asText(), "variables", Map.of("pass", false)), 200);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM oa_todo WHERE business_type=?", Integer.class, TYPE));
    }

    void configureCreateApproval() throws Exception {
        actions.updateAction(340590, "add", Map.of("handlerKey", WorkflowApiRegistry.HANDLER, "configJson", Map.of("endpoint", "POST /project/business"), "flowDesignEnabled", true));
        Map<String, Object> process = Map.of("nodes", List.of(Map.of("id", "start", "type", "start", "name", "发起人"), Map.of("id", "review", "type", "approval", "name", "审核", "approver", "opportunity-owner-9802"), Map.of("id", "end", "type", "end", "name", "结束")), "edges", List.of(Map.of("source", "start", "target", "review"), Map.of("source", "review", "target", "end")));
        call(admin, post("/activiti/mongo/setProcess"), Map.of("menuId", 340590, "menuName", "商机管理", "actionCode", "add", "approvalEnabled", true, "processData", process), 200);
    }
    Map<String, Object> body() { return Map.of("name", "待办测试商机", "code", "REMINDER-" + java.util.UUID.randomUUID(), "customerName", "客户测试", "expectedAmount", 10000, "ownerUserId", 9801, "statusLabel", "推进中"); }
    long create() throws Exception { return call(admin, post("/project/business"), body(), 200).path("data").asLong(); }
    void advance(String actor, long id, String stage, int expected) throws Exception { call(actor, post("/project/business/stage/insert"), Map.of("businessId", id, "state", stage, "recordTime", "2026-11-11 10:00:00", "remark", "确认需求"), expected); }
    int count(long id, String state) {
        String sql = "SELECT COUNT(*) FROM oa_todo WHERE business_type=? AND business_id=?";
        return state == null ? jdbc.queryForObject(sql, Integer.class, TYPE, id) : jdbc.queryForObject(sql + " AND status=?", Integer.class, TYPE, id, state);
    }
    List<JsonNode> tasks(String actor, long id) throws Exception {
        JsonNode rows = call(actor, get("/project/activity/taskWaitList").param("pageSize", "100"), null, 200).path("rows");
        return StreamSupport.stream(rows.spliterator(), false).filter(task -> TYPE.equals(task.path("businessType").asText()) && task.path("businessId").asLong() == id).toList();
    }
    String login(String name) throws Exception { return "Bearer " + call(null, post("/login"), Map.of("username", name, "password", "admin123123"), 200).path("token").asText(); }
    JsonNode call(String actor, MockHttpServletRequestBuilder request, Object body, int expected) throws Exception {
        if (actor != null) request.header("Authorization", actor);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        JsonNode result = mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertEquals(expected, result.path("code").asInt(), result.toString());
        return result;
    }
}
