package com.seal.oa.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:umm_oa_customer_reminders;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class CustomerFollowupReminderIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired CustomerFollowupReminderService reminders;
    @Autowired PlatformTransactionManager transactionManager;
    String admin;
    String leader;
    String member;
    String peer;

    @BeforeEach
    void setup() throws Exception {
        jdbc.update("INSERT INTO sys_role (role_id, role_name, role_key, data_scope) VALUES (9701, '客户提醒测试', 'customer_reminder_test', '1')");
        for (long module : List.of(340352L, 340644L, 341101L, 341201L)) {
            jdbc.update("INSERT INTO sys_role_module (role_id, module_id) VALUES (9701, ?)", module);
        }
        for (String action : List.of("add", "edit", "remove", "export", "track")) {
            jdbc.update("INSERT INTO sys_role_module_action (role_id, module_id, action_code) VALUES (9701, 340352, ?)", action);
        }
        jdbc.update("INSERT INTO sys_dept (dept_id, parent_id, dept_name) VALUES (9700, 0, '上级部门'), (9701, 9700, '客户一部'), (9702, 9701, '下级部门'), (9703, 9700, '平级部门')");
        for (long id = 9701; id <= 9707; id++) {
            long dept = id <= 9703 ? 9701 : id <= 9705 ? 9702 : id == 9706 ? 9703 : 9700;
            jdbc.update("""
                    INSERT INTO sys_user (user_id, dept_id, user_name, nick_name, password)
                    SELECT ?, ?, ?, ?, password FROM sys_user WHERE user_id = 4
                    """, id, dept, "customer-test-" + id, "客户成员" + id);
            jdbc.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, 9701)", id);
        }
        jdbc.update("INSERT INTO sys_dept_principal (dept_id, user_id) VALUES (9700, 9707), (9701, 9701), (9702, 9704)");
        admin = login("13610316814");
        leader = login("customer-test-9701");
        member = login("customer-test-9702");
        peer = login("customer-test-9703");
    }

    @Test
    void savingAssignedCustomerImmediatelyNotifiesOnlyOwnerAndDecoratesTask() throws Exception {
        long id = create(9702);
        assertEquals(1, count(id, "todo"));
        assertEquals(9702L, jdbc.queryForObject("SELECT user_id FROM oa_todo WHERE business_type = ? AND business_id = ?",
                Long.class, CustomerFollowupReminderService.BUSINESS_TYPE, id));
        JsonNode task = tasks(member, id).get(0);
        assertEquals(id, task.path("form").path("customerId").asLong());
        assertEquals("客户首次跟进", task.path("processName").asText());
        assertEquals("请新增客户跟进", task.path("taskName").asText());
        assertEquals(0, tasks(peer, id).size());
        assertEquals(0, tasks(admin, id).size());
    }

    @Test
    void savesRestartsAndLaterDaysKeepOnePendingReminderAndOriginalPushTime() throws Exception {
        long id = create(9702);
        jdbc.update("UPDATE oa_todo SET create_time='2026-01-01 10:00:00' WHERE business_type=? AND business_id=?",
                CustomerFollowupReminderService.BUSINESS_TYPE, id);
        var pushed = jdbc.queryForObject("SELECT create_time FROM oa_todo WHERE business_type = ? AND business_id = ?", java.sql.Timestamp.class, CustomerFollowupReminderService.BUSINESS_TYPE, id);
        Map<String, Object> body = customerBody(9702);
        body.put("id", id);
        call(admin, put("/project/merchant"), body, 200);
        reminders.synchronizeCustomer(id);
        reminders.reconcilePendingReminders();
        reminders.reconcilePendingReminders();
        assertEquals(1, count(id, "todo"));
        reminders.synchronizeCustomer(id);
        reminders.synchronizeCustomer(id);
        assertEquals(1, count(id, "todo"));
        assertEquals(pushed, jdbc.queryForObject("SELECT create_time FROM oa_todo WHERE business_type = ? AND business_id = ?", java.sql.Timestamp.class, CustomerFollowupReminderService.BUSINESS_TYPE, id));
    }

    @Test
    void legacyDailyDuplicatesAreConsolidatedAndTransferBackCreatesAFreshTask() throws Exception {
        long id = create(9702);
        String originalTask = tasks(member, id).get(0).path("taskId").asText();
        jdbc.update("""
                INSERT INTO oa_todo(user_id, title, business_type, business_id, task_id, status)
                VALUES (9702, '旧版每日提醒', ?, ?, 'legacy-daily-task', 'todo')
                """, CustomerFollowupReminderService.BUSINESS_TYPE, id);
        reminders.reconcilePendingReminders();
        assertEquals(1, count(id, "todo"));
        assertEquals(originalTask, tasks(member, id).get(0).path("taskId").asText());
        call(admin, post("/project/merchant/" + id + "/assign"), Map.of("ownerUserId", 9703), 200);
        call(admin, post("/project/merchant/" + id + "/assign"), Map.of("ownerUserId", 9702), 200);
        assertEquals(1, count(id, "todo"));
        assertNotEquals(originalTask, tasks(member, id).get(0).path("taskId").asText());
    }

    @Test
    void followupCompletesPendingTaskAndStopsFurtherReminders() throws Exception {
        long id = create(9702);
        reminders.synchronizeCustomer(id);
        followup(member, id, "2026-11-01 10:00:00");
        assertEquals(0, count(id, "todo"));
        assertEquals(1, count(id, "done"));
        reminders.synchronizeCustomer(id);
        assertEquals(1, count(id, null));
        assertEquals(0, tasks(member, id).size());
        JsonNode done = call(member, get("/project/activity/taskDoneList"), null, 200).path("rows");
        assertTrue(StreamSupport.stream(done.spliterator(), false).anyMatch(row -> row.path("form").path("customerId").asLong() == id));
    }

    @Test
    void genericCompleteBackDelegateAndTerminateCannotBypassActualFollowup() throws Exception {
        long id = create(9702);
        call(member, post("/project/merchant/" + id + "/followups"), Map.of("content", "   "), 400);
        JsonNode task = tasks(member, id).get(0);
        for (String endpoint : List.of("complete", "back", "delegate", "end/process")) {
            call(member, post("/project/activity/" + endpoint), Map.of("taskId", task.path("taskId").asText()), 409);
        }
        call(member, post("/project/activity/complete"), Map.of("todoId", task.path("id").asLong()), 409);
        assertEquals(1, count(id, "todo"));
    }

    @Test
    void reassignmentCancelsOldOwnerAndNotifiesNewOwnerOnce() throws Exception {
        long id = create(9702);
        call(admin, post("/project/merchant/" + id + "/assign"), Map.of("ownerUserId", 9703), 200);
        assertEquals(1, count(id, "cancelled"));
        assertEquals(1, count(id, "todo"));
        assertEquals(0, tasks(member, id).size());
        assertEquals(1, tasks(peer, id).size());
        call(member, get("/project/merchant/" + id), null, 403);
        call(admin, post("/project/merchant/" + id + "/assign"), Map.of("ownerUserId", 9703), 200);
        assertEquals(2, count(id, null));
    }

    @Test
    void poolClaimHandoverAndDeleteKeepReminderOwnershipInSync() throws Exception {
        long id = create(9702);
        call(admin, post("/project/merchant/" + id + "/release"), null, 200);
        assertEquals(0, count(id, "todo"));
        reminders.synchronizeCustomer(id);
        assertEquals(1, count(id, null));
        call(member, post("/project/merchant/" + id + "/claim"), null, 200);
        assertEquals(1, count(id, "todo"));
        call(admin, post("/project/merchant/handover"), Map.of("fromUserId", 9702, "toUserId", 9703), 200);
        assertEquals(0, tasks(member, id).size());
        assertEquals(1, tasks(peer, id).size());
        call(admin, delete("/project/merchant/" + id), null, 200);
        assertEquals(0, count(id, "todo"));
    }

    @Test
    void inactiveOwnerAndDisabledCustomerDoNotReceiveNewReminders() throws Exception {
        long id = create(9702);
        jdbc.update("UPDATE sys_user SET status = '1' WHERE user_id = 9702");
        reminders.synchronizeCustomer(id);
        assertEquals(0, count(id, "todo"));
        jdbc.update("UPDATE sys_user SET status = '0' WHERE user_id = 9702");
        jdbc.update("UPDATE oa_merchant SET cooperation_status = '合作中' WHERE id = ?", id);
        call(admin, delete("/project/merchant/" + id), null, 200);
        reminders.synchronizeCustomer(id);
        assertEquals(1, count(id, null));
    }

    @Test
    void backdatedFollowupAndProfileEditsPreserveLatestFollowupAndDeletingLastRestartsReminders() throws Exception {
        long id = create(9702);
        followup(member, id, "2026-11-02 10:00:00");
        followup(member, id, "2026-11-01 09:00:00");
        assertTrue(call(member, get("/project/merchant/" + id), null, 200).path("data").path("lastFollowupTime").asText().startsWith("2026-11-02"));
        Map<String, Object> body = customerBody(9702);
        body.put("id", id);
        call(member, put("/project/merchant"), body, 200);
        assertEquals(0, count(id, "todo"));
        List<Long> records = jdbc.queryForList("SELECT id FROM oa_customer_followup WHERE merchant_id = ? ORDER BY followup_time DESC", Long.class, id);
        call(member, delete("/project/merchant/followup/" + records.get(0)), null, 200);
        assertTrue(call(member, get("/project/merchant/" + id), null, 200).path("data").path("lastFollowupTime").asText().startsWith("2026-11-01"));
        call(member, delete("/project/merchant/followup/" + records.get(1)), null, 200);
        assertEquals(1, count(id, "todo"));
        assertEquals(1, count(id, "done"));
        assertEquals(2, count(id, null));
    }

    @Test
    void ordinaryMembersSeeOnlyAssignedCustomersEvenWithBroadRoleScope() throws Exception {
        long own = create(9702);
        long peers = create(9703);
        long supervisors = create(9701);
        long unrelated = create(9706);
        for (long hidden : List.of(peers, supervisors, unrelated)) {
            call(member, get("/project/merchant/" + hidden), null, 403);
            call(member, get("/project/merchant/" + hidden + "/followups"), null, 403);
            call(member, post("/project/merchant/" + hidden + "/followups"), Map.of("content", "越权"), 403);
            assertEquals(0, call(member, get("/project/merchant/list").param("customerId", String.valueOf(hidden)), null, 200).path("total").asInt());
        }
        assertEquals(1, call(member, get("/project/merchant/list").param("customerId", String.valueOf(own)), null, 200).path("total").asInt());
        JsonNode options = call(member, get("/project/merchant/options"), null, 200).path("data");
        assertEquals(List.of(own), StreamSupport.stream(options.spliterator(), false).map(row -> row.path("id").asLong()).toList());
        assertEquals(0, call(member, get("/project/merchant/export").param("customerId", String.valueOf(peers)), null, 200).path("data").size());
        call(member, post("/project/merchant/" + own + "/assign"), Map.of("ownerUserId", 9703), 403);
    }

    @Test
    void departmentPrincipalSeesOwnAndNestedSubordinatesButNotParentsOrOtherTeams() throws Exception {
        for (long owner : List.of(9701L, 9702L, 9703L, 9704L, 9705L)) {
            long id = create(owner);
            call(leader, get("/project/merchant/" + id), null, 200);
        }
        for (long owner : List.of(9706L, 9707L)) {
            long id = create(owner);
            call(leader, get("/project/merchant/" + id), null, 403);
            call(admin, get("/project/merchant/" + id), null, 200);
        }
        JsonNode options = call(leader, get("/project/merchant/owner/options"), null, 200).path("data");
        assertEquals(5, options.size());
        jdbc.update("DELETE FROM sys_dept_principal WHERE dept_id = 9701");
        long subordinate = create(9705);
        call(leader, get("/project/merchant/" + subordinate), null, 403);
    }

    @Test
    void creatorDoesNotRetainAccessAfterCustomerIsReassignedOutsideTheirTeam() throws Exception {
        long id = call(leader, post("/project/merchant"), customerBody(9702), 200).path("data").asLong();
        call(admin, post("/project/merchant/" + id + "/assign"), Map.of("ownerUserId", 9706), 200);
        call(leader, get("/project/merchant/" + id), null, 403);
        assertEquals(0, call(leader, get("/project/merchant/list").param("customerId", String.valueOf(id)), null, 200).path("total").asInt());
    }

    @Test
    void concurrentSynchronizationsCreateOnlyOneReminder() throws Exception {
        TransactionTemplate independent = new TransactionTemplate(transactionManager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        String serial = "CONCURRENT-" + java.util.UUID.randomUUID();
        long id = independent.execute(ignored -> {
            jdbc.update("INSERT INTO oa_merchant (serial, name, owner_user_id) VALUES (?, '并发提醒测试', 1)", serial);
            return jdbc.queryForObject("SELECT id FROM oa_merchant WHERE serial = ?", Long.class, serial);
        });
        var executor = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<CompletableFuture<Void>> jobs = java.util.stream.IntStream.range(0, 4).mapToObj(index ->
                    CompletableFuture.runAsync(() -> {
                        try {
                            start.await();
                            reminders.synchronizeCustomer(id);
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                    }, executor)).toList();
            start.countDown();
            CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
            assertEquals(1, count(id, "todo"));
        } finally {
            executor.shutdownNow();
            independent.executeWithoutResult(ignored -> {
                jdbc.update("DELETE FROM oa_todo WHERE business_type = ? AND business_id = ?", CustomerFollowupReminderService.BUSINESS_TYPE, id);
                jdbc.update("DELETE FROM oa_merchant WHERE id = ?", id);
            });
        }
    }

    @Test
    void importedHistoricalFollowupDoesNotGenerateFirstFollowupTodo() throws Exception {
        Map<String, Object> body = customerBody(9702);
        body.put("lastFollowupTime", "2026-11-01 10:00:00");
        long id = call(admin, post("/project/merchant"), body, 200).path("data").asLong();
        reminders.synchronizeCustomer(id);
        assertEquals(0, count(id, null));
    }

    private Map<String, Object> customerBody(long owner) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "客户提醒测试" + owner);
        body.put("industry", "软件与信息服务");
        body.put("ownerUserId", owner);
        return body;
    }
    private long create(long owner) throws Exception {
        Map<String, Object> body = customerBody(owner);
        body.put("name", body.get("name") + "-" + java.util.UUID.randomUUID());
        return call(admin, post("/project/merchant"), body, 200).path("data").asLong();
    }
    private void followup(String auth, long id, String time) throws Exception {
        call(auth, post("/project/merchant/" + id + "/followups"), Map.of("content", "已联系客户，确认招聘需求", "followupTime", time), 200);
    }
    private int count(long id, String state) {
        String sql = "SELECT COUNT(*) FROM oa_todo WHERE business_type = ? AND business_id = ?";
        return state == null ? jdbc.queryForObject(sql, Integer.class, CustomerFollowupReminderService.BUSINESS_TYPE, id)
                : jdbc.queryForObject(sql + " AND status = ?", Integer.class, CustomerFollowupReminderService.BUSINESS_TYPE, id, state);
    }
    private List<JsonNode> tasks(String auth, long customer) throws Exception {
        JsonNode rows = call(auth, get("/project/activity/taskWaitList").param("pageSize", "100"), null, 200).path("rows");
        return StreamSupport.stream(rows.spliterator(), false).filter(row ->
                CustomerFollowupReminderService.BUSINESS_TYPE.equals(row.path("businessType").asText())
                        && row.path("businessId").asLong() == customer).toList();
    }
    private String login(String name) throws Exception {
        return "Bearer " + call(null, post("/login"), Map.of("username", name, "password", "admin123123"), 200).path("token").asText();
    }
    private JsonNode call(String auth, MockHttpServletRequestBuilder request, Object body, int expected) throws Exception {
        if (auth != null) request.header("Authorization", auth);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        JsonNode result = mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(expected, result.path("code").asInt(), result.toString());
        return result;
    }
}
