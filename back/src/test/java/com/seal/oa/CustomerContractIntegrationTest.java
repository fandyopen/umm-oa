package com.seal.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.common.ApiException;
import com.seal.oa.oa.CrmContractService;
import com.seal.oa.system.ModuleActionService;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:umm_oa_contracts;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class CustomerContractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired CrmContractService contracts;
    @Autowired ModuleActionService moduleActions;
    String admin;
    String reviewer;
    String outsider;
    final String base = "/project/crm/contracts";
    final LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));

    @BeforeEach
    void setup() throws Exception {
        jdbc.update("INSERT INTO sys_role (role_id, role_name, role_key, data_scope) VALUES (991, '合同审批测试', 'contract_test', '5')");
        for (long id : List.of(901L, 902L)) {
            jdbc.update("""
                    INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, status, del_flag)
                    SELECT ?, 1, 325, ?, ?, password, '0', '0' FROM sys_user WHERE user_id = 4
                    """, id, "17690000" + id, "合同审批员" + id);
            jdbc.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, 991)", id);
        }
        jdbc.update("INSERT INTO sys_role_module (role_id, module_id) VALUES (991, 341202)");
        jdbc.update("INSERT INTO sys_role_module_action (role_id, module_id, action_code) VALUES (991, 341202, 'approve')");
        admin = login("13610316814");
        reviewer = login("17690000901");
        outsider = login("17690000902");
        publishContractFlow("17690000901");
    }

    @Test
    void moduleTreeShowsOneContractEntryAndKeepsLegacyPermissionModulesHidden() throws Exception {
        JsonNode roots = call(admin, get("/getRouters"), null, 200).path("data");
        JsonNode root = find(roots, 340310);
        assertNotNull(root);
        assertEquals("客户与合同管理", root.path("moduleName").asText());
        JsonNode group = find(root.path("children"), 341200);
        assertTrue(group.path("hidden").asBoolean());
        assertEquals("/crm/contracts/list", group.path("redirect").asText());
        assertEquals(4, group.path("children").size());
        JsonNode list = find(root.path("children"), 341201);
        assertEquals(340310, list.path("parentId").asInt());
        assertEquals("contracts/list", list.path("path").asText());
        assertFalse(list.path("hidden").asBoolean());
        for (long module : List.of(341202L, 341203L, 341204L, 341205L)) {
            assertTrue(find(roots, module).path("hidden").asBoolean());
        }
        assertEquals(340310, find(root.path("children"), 341101).path("parentId").asInt());
        assertEquals("eos/crm/contract/fees", find(roots, 341203).path("component").asText());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_module_action WHERE module_id = 341202 AND action_code = 'approve'", Integer.class));
    }

    @Test
    void entryMigrationPreservesActionsAndDoesNotRestoreRevokedAccess() {
        jdbc.update("INSERT INTO sys_role (role_id, role_name, role_key, data_scope) VALUES (992, '财务入口迁移测试', 'contract_navigation_test', '5')");
        for (long module : List.of(341203L, 341204L, 341205L)) {
            jdbc.update("INSERT INTO sys_role_module (role_id, module_id) VALUES (992, ?)", module);
        }
        jdbc.update("INSERT INTO sys_role_module_action (role_id, module_id, action_code) VALUES (992, 341203, 'receive')");
        jdbc.update("DELETE FROM sys_permission_migration WHERE migration_key = 'contract_navigation_consolidation_v1'");
        moduleActions.bootstrap();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_module WHERE role_id = 991 AND module_id = 340341", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_module WHERE role_id = 992 AND module_id IN (341201, 341101)", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_module_action WHERE role_id = 992", Integer.class));
        assertEquals("5", jdbc.queryForObject("SELECT data_scope FROM sys_role WHERE role_id = 992", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_module_action WHERE role_id = 991 AND module_id = 341202 AND action_code = 'approve'", Integer.class));
        jdbc.update("DELETE FROM sys_role_module WHERE role_id = 991 AND module_id = 340341");
        jdbc.update("DELETE FROM sys_role_module WHERE role_id = 992 AND module_id IN (341201, 341101)");
        moduleActions.bootstrap();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_module WHERE role_id = 991 AND module_id = 340341", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_module WHERE role_id = 992 AND module_id IN (341201, 341101)", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_module WHERE role_id = 992 AND module_id IN (341203, 341204, 341205)", Integer.class));
    }

    @Test
    void unifiedContractFilterRunsBeforePaginationAndIncludesLegacyTasks() throws Exception {
        long customer = createCustomer("统一待办筛选客户");
        List<Long> contractIds = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            long id = createDraft(customer).path("id").asLong();
            action(admin, id, "submit", Map.of("revision", 0));
            contractIds.add(id);
        }
        long legacyId = createDraft(customer).path("id").asLong();
        contractIds.add(legacyId);
        jdbc.update("INSERT INTO oa_todo (tenant_id, user_id, title, business_type, business_id, task_id, status) VALUES (1, 901, '历史合同审批筛选', 'crmContractApproval', ?, 'legacy-contract-filter', 'todo')", legacyId);
        for (int index = 0; index < 12; index++) {
            jdbc.update("INSERT INTO oa_todo (tenant_id, user_id, title, business_type, business_id, task_id, status) VALUES (1, 901, '无关客户风险', 'crmRisk', ?, ?, 'todo')", customer, "unrelated-risk-" + index);
        }
        List<Long> foundIds = new ArrayList<>();
        for (int page = 1; page <= 2; page++) {
            JsonNode response = call(reviewer, get("/project/activity/taskWaitList").param("businessCategory", "contract")
                    .param("pageNum", String.valueOf(page)).param("pageSize", "2"), null, 200);
            assertEquals(4, response.path("total").asInt());
            assertEquals(16, response.path("unfilteredTotal").asInt());
            assertEquals(2, response.path("rows").size());
            for (JsonNode row : response.path("rows")) foundIds.add(row.path("form").path("contractId").asLong());
        }
        assertEquals(contractIds.stream().sorted().toList(), foundIds.stream().sorted().toList());
        JsonNode legacy = call(reviewer, get("/project/activity/taskWaitList").param("businessCategory", "contract").param("queryAll", "历史合同审批筛选"), null, 200);
        assertEquals(1, legacy.path("total").asInt());
        assertEquals("crmContractApproval", legacy.path("rows").get(0).path("businessType").asText());
        assertEquals(0, call(outsider, get("/project/activity/taskWaitList").param("businessCategory", "contract"), null, 200).path("total").asInt());
        assertEquals(0, call(reviewer, get("/project/activity/taskWaitList").param("businessCategory", "contract").param("queryAll", "不存在的合同筛选"), null, 200).path("total").asInt());
    }

    @Test
    void approvalSigningAndReceiptUpdateCustomerAndPreserveTaskBoundaries() throws Exception {
        long customerId = createCustomer("合同生命周期客户");
        JsonNode draft = createDraft(customerId);
        long id = draft.path("id").asLong();
        JsonNode pending = action(admin, id, "submit", Map.of("revision", 0, "approvalUserId", 901));
        assertEquals("pending", pending.path("status").asText());
        call(admin, put(base + "/" + id), draft, 409);
        call(outsider, post(base + "/" + id + "/approve"), Map.of("revision", 1, "decision", "approved"), 403);
        JsonNode tasks = call(reviewer, get("/project/activity/taskWaitList"), null, 200).path("rows");
        JsonNode task = null;
        for (JsonNode candidate : tasks) if ("workflowAction".equals(candidate.path("businessType").asText())
                && candidate.path("form").path("contractId").asLong() == id) task = candidate;
        assertNotNull(task);
        assertEquals(id, task.path("form").path("contractId").asLong());
        assertEquals(pending.path("workflow").path("taskId").asText(), task.path("taskId").asText());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM oa_todo WHERE business_type = 'crmContractApproval' AND business_id = ?", Integer.class, id));
        JsonNode approved = action(reviewer, id, "approve", Map.of("revision", 1, "decision", "approved", "comment", "收费与保证期已核对"));
        assertEquals("approved", approved.path("status").asText());
        call(admin, post(base + "/" + id + "/sign"), Map.of("revision", 2, "signedDate", today.plusDays(1).toString()), 400);
        JsonNode active = action(admin, id, "sign", Map.of("revision", 2, "signedDate", today.toString()));
        assertEquals(1, active.path("effectiveVersion").asInt());
        assertEquals(new BigDecimal("1000.00"), jdbc.queryForObject("SELECT contract_amount FROM oa_merchant WHERE id = ?", BigDecimal.class, customerId));
        call(admin, post("/project/merchant/" + customerId + "/release"), null, 409);
        call(admin, put("/project/merchant"), Map.of("id", customerId, "publicPool", "1"), 409);
        call(admin, delete("/project/merchant/" + customerId), null, 409);
        call(admin, post(base + "/" + id + "/receipts"), Map.of("termKey", "first", "amount", 1001, "receiptDate", today.toString()), 400);
        call(admin, post(base + "/" + id + "/receipts"), Map.of("termKey", "first", "amount", 400, "receiptDate", today.toString()), 200);
        assertEquals(new BigDecimal("400.00"), jdbc.queryForObject("SELECT received_amount FROM oa_merchant WHERE id = ?", BigDecimal.class, customerId));
        assertEquals("合作中", jdbc.queryForObject("SELECT cooperation_status FROM oa_merchant WHERE id = ?", String.class, customerId));
        call(admin, post(base + "/" + id + "/finish"), Map.of("revision", active.path("revision").asInt()), 409);
    }

    @Test
    void draftAmendmentKeepsEffectiveAmountsAndImmutableSnapshotsUntilSigning() throws Exception {
        long customer = createCustomer("版本隔离客户");
        JsonNode active = activate(createDraft(customer));
        long id = active.path("id").asLong();
        call(admin, post(base + "/" + id + "/receipts"), Map.of("termKey", "first", "amount", 400, "receiptDate", today.toString()), 200);
        JsonNode draft = action(admin, id, "revision", Map.of("revision", active.path("revision").asInt()));
        Map<String, Object> changed = mapper.convertValue(draft, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
        changed.put("amount", 1500);
        changed.put("feeTerms", List.of(term(1500)));
        JsonNode updated = call(admin, put(base + "/" + id), changed, 200).path("data");
        assertEquals(1000, updated.path("effectiveAmount").asInt());
        assertEquals(600, updated.path("outstandingAmount").asInt());
        assertEquals(1000, updated.path("versions").get(0).path("snapshot").path("amount").asInt());
        Map<String, Object> invalid = new LinkedHashMap<>(changed);
        invalid.put("revision", updated.path("revision").asInt());
        invalid.put("feeTerms", List.of());
        call(admin, put(base + "/" + id), invalid, 409);
        JsonNode pending = action(admin, id, "submit", Map.of("revision", updated.path("revision").asInt(), "approvalUserId", 901));
        assertEquals(1000, pending.path("effectiveAmount").asInt());
        JsonNode approved = action(reviewer, id, "approve", Map.of("revision", pending.path("revision").asInt(), "decision", "approved"));
        JsonNode signed = action(admin, id, "sign", Map.of("revision", approved.path("revision").asInt(), "signedDate", today.toString()));
        assertEquals(2, signed.path("effectiveVersion").asInt());
        assertEquals(1500, signed.path("effectiveAmount").asInt());
        assertEquals(1100, signed.path("outstandingAmount").asInt());
        assertEquals(2, signed.path("versions").size());
    }

    @Test
    void remindersDeduplicateAndCloseOnlyAfterSourceConditionResolves() throws Exception {
        long customer = createCustomer("风险闭环客户");
        JsonNode active = activate(createDraft(customer));
        long id = active.path("id").asLong();
        JsonNode risks = call(admin, get("/project/crm/risk/list").param("customerId", String.valueOf(customer)), null, 200);
        assertEquals(2, risks.path("rows").size());
        call(admin, get("/project/crm/risk/list").param("customerId", String.valueOf(customer)), null, 200);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM oa_todo WHERE business_type = 'crmRisk' AND business_id = ? AND status = 'todo'", Integer.class, customer));
        String reminderId = "crm-contract-" + id + "-fee-first";
        call(admin, post(base + "/" + id + "/reminder-log"), Map.of("taskId", reminderId, "content", "已联系客户财务确认付款计划"), 200);
        JsonNode detail = call(admin, get(base + "/" + id), null, 200).path("data");
        assertEquals(2, detail.path("reminders").size());
        assertEquals(1, detail.path("reminderLogs").size());
        assertEquals("已联系客户财务确认付款计划", detail.path("reminderLogs").get(0).path("content").asText());
        assertEquals(2, call(admin, get(base + "/reminders").param("customerId", String.valueOf(customer)), null, 200).path("rows").size());
        call(admin, post(base + "/" + id + "/receipts"), Map.of("termKey", "first", "amount", 1000, "receiptDate", today.toString()), 200);
        assertEquals(1, call(admin, get(base + "/reminders").param("customerId", String.valueOf(customer)), null, 200).path("rows").size());
        assertEquals(1, call(admin, get(base + "/" + id), null, 200).path("data").path("reminders").size());
        assertEquals("done", jdbc.queryForObject("SELECT status FROM oa_todo WHERE task_id = ?", String.class, reminderId));
        action(admin, id, "finish", Map.of("revision", active.path("revision").asInt()));
        assertEquals(0, call(admin, get(base + "/reminders").param("customerId", String.valueOf(customer)), null, 200).path("rows").size());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM oa_todo WHERE business_type = 'crmRisk' AND business_id = ? AND status = 'todo'", Integer.class, customer));
    }

    @Test
    void renewalClosesOldExpiryAndRiskOwnershipFollowsCustomer() throws Exception {
        long customer = createCustomer("续约交接客户");
        JsonNode active = activate(createDraft(customer));
        long id = active.path("id").asLong();
        call(admin, get("/project/crm/risk/list").param("customerId", String.valueOf(customer)), null, 200);
        call(admin, post("/project/merchant/" + customer + "/assign"), Map.of("ownerUserId", 901), 200);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM oa_todo WHERE business_type = 'crmRisk' AND business_id = ? AND user_id = 901 AND status = 'todo'", Integer.class, customer));
        JsonNode risks = call(admin, get(base + "/reminders").param("customerId", String.valueOf(customer)), null, 200).path("rows");
        for (JsonNode risk : risks) assertEquals(901, risk.path("ownerUserId").asLong());
        Map<String, Object> renewal = payload(customer);
        renewal.put("parentContractId", id);
        renewal.put("startDate", today.plusDays(16).toString());
        renewal.put("endDate", today.plusYears(1).toString());
        Map<String, Object> futureTerm = term(1000);
        futureTerm.put("dueDate", today.plusDays(60).toString());
        renewal.put("feeTerms", List.of(futureTerm));
        activate(call(admin, post(base), renewal, 200).path("data"));
        assertEquals("done", jdbc.queryForObject("SELECT status FROM oa_todo WHERE task_id = ?", String.class, "crm-contract-" + id + "-expiry"));
        risks = call(admin, get(base + "/reminders").param("customerId", String.valueOf(customer)), null, 200).path("rows");
        assertEquals(1, risks.size());
        assertEquals("payment_overdue", risks.get(0).path("riskType").asText());
    }

    @Test
    void approvalOnlyAccessIsAssignedAndMutationRequiresActionPermission() throws Exception {
        long customer = createCustomer("合同权限客户");
        JsonNode draft = createDraft(customer);
        long id = draft.path("id").asLong();
        call(reviewer, get(base + "/" + id), null, 403);
        JsonNode pending = action(admin, id, "submit", Map.of("revision", 0, "approvalUserId", 901));
        assertEquals(id, call(reviewer, get(base + "/" + id), null, 200).path("data").path("id").asLong());
        assertEquals(0, call(outsider, get(base).param("view", "approvals"), null, 200).path("total").asInt());
        call(outsider, get(base + "/" + id), null, 403);
        jdbc.update("INSERT INTO sys_role_module (role_id, module_id) VALUES (991, 341203)");
        call(reviewer, post(base + "/" + id + "/receipts"), Map.of("termKey", "first", "amount", 10, "receiptDate", today.toString()), 403);
        call(reviewer, post(base + "/" + id + "/approve"), Map.of("revision", pending.path("revision").asInt(), "decision", "rejected", "comment", ""), 400);
        JsonNode rejected = action(reviewer, id, "approve", Map.of("revision", pending.path("revision").asInt(), "decision", "rejected", "comment", "请补充收费细则"));
        assertEquals("rejected", rejected.path("status").asText());
        call(admin, delete(base + "/" + id), null, 409);
    }

    @Test
    void submissionRejectsUnbalancedFeesAndStaleUpdates() throws Exception {
        long customer = createCustomer("条款校验客户");
        Map<String, Object> payload = payload(customer);
        payload.put("feeTerms", List.of(term(900)));
        JsonNode draft = call(admin, post(base), payload, 200).path("data");
        long id = draft.path("id").asLong();
        call(admin, post(base + "/" + id + "/submit"), Map.of("revision", 0, "approvalUserId", 901), 400);
        payload.put("revision", 20);
        call(admin, put(base + "/" + id), payload, 409);
        call(admin, delete(base + "/" + id), null, 200);
    }

    @Test
    void malformedContractIdsReturnValidationErrorsInsteadOfServerErrors() throws Exception {
        for (String id : List.of("NaN", "undefined", "Infinity", "1.5", "9223372036854775808")) {
            JsonNode result = call(admin, get(base + "/" + id), null, 400);
            assertTrue(result.path("msg").asText().contains("id"));
        }
        call(admin, get(base + "/9223372036854775807"), null, 404);
    }

    @Test
    void submitActionIsDiscoverableAndUsesDesignedApproverInsteadOfCallerInput() throws Exception {
        JsonNode actions = call(admin, get("/system/module/341201/actions").param("workflowOnly", "true"), null, 200).path("data");
        boolean found = false;
        for (JsonNode action : actions) if ("submit".equals(action.path("actionCode").asText())) {
            found = true;
            assertEquals("提交审批", action.path("actionName").asText());
            assertEquals("crm.contract.submit", action.path("handlerKey").asText());
            assertEquals(0, action.path("builtin").asInt());
        }
        assertTrue(found);
        JsonNode draft = createDraft(createCustomer("流程审批人客户"));
        JsonNode pending = action(admin, draft.path("id").asLong(), "submit", Map.of("revision", 0, "approvalUserId", 902));
        assertEquals(901, pending.path("approvalUserId").asLong());
        assertTrue(pending.path("workflow").path("instanceId").asText().startsWith("biz-"));
        assertTrue(pending.path("versions").get(0).path("snapshot").path("workflowRequestId").asLong() > 0);
        call(admin, post(base + "/" + draft.path("id").asLong() + "/submit"), Map.of("revision", 0), 409);
        assertEquals(1, call(admin, get(base + "/" + draft.path("id").asLong()), null, 200).path("data").path("versions").size());
    }

    @Test
    void closingContractDesignBlocksNewSubmissionsButKeepsRunningApproval() throws Exception {
        JsonNode pendingDraft = createDraft(createCustomer("关闭设计在途客户"));
        JsonNode pending = action(admin, pendingDraft.path("id").asLong(), "submit", Map.of("revision", 0));
        call(admin, put("/system/module/341201/actions/submit"), Map.of("flowDesignEnabled", false), 200);
        JsonNode newDraft = createDraft(createCustomer("关闭设计新提交客户"));
        call(admin, post(base + "/" + newDraft.path("id").asLong() + "/submit"), Map.of("revision", 0), 400);
        JsonNode unchanged = call(admin, get(base + "/" + newDraft.path("id").asLong()), null, 200).path("data");
        assertEquals("draft", unchanged.path("status").asText());
        assertEquals(0, unchanged.path("revision").asInt());
        JsonNode approved = action(reviewer, pending.path("id").asLong(), "approve",
                Map.of("revision", pending.path("revision").asInt(), "decision", "approved", "comment", "在途审批继续处理"));
        assertEquals("approved", approved.path("status").asText());
    }

    @Test
    void missingOrDisabledContractFlowCannotSilentlyApproveOrFreezeDraft() throws Exception {
        JsonNode draft = createDraft(createCustomer("未配置流程客户"));
        long id = draft.path("id").asLong();
        jdbc.update("UPDATE oa_module_action_flow SET approval_enabled = 0 WHERE module_id = 341201 AND action_code = 'submit'");
        call(admin, post(base + "/" + id + "/submit"), Map.of("revision", 0), 400);
        call(admin, post("/workflow/action/execute"), Map.of("moduleId", 341201, "actionCode", "submit", "payload", Map.of("contractId", id, "revision", 0)), 400);
        jdbc.update("DELETE FROM oa_module_action_flow WHERE module_id = 341201 AND action_code = 'submit'");
        call(admin, post(base + "/" + id + "/submit"), Map.of("revision", 0), 400);
        JsonNode unchanged = call(admin, get(base + "/" + id), null, 200).path("data");
        assertEquals("draft", unchanged.path("status").asText());
        assertEquals(0, unchanged.path("versions").size());
    }

    @Test
    void twoStepApprovalSharesTasksAndFreezesProcessDesignUntilFinalApproval() throws Exception {
        publishContractFlow("17690000901", "17690000902");
        long id = createDraft(createCustomer("两级审批客户")).path("id").asLong();
        JsonNode pending = action(admin, id, "submit", Map.of("revision", 0));
        String instanceId = pending.path("workflow").path("instanceId").asText();
        publishContractFlow("17690000901");
        JsonNode middle = action(reviewer, id, "approve", Map.of("revision", pending.path("revision").asInt(), "decision", "approved", "comment", "一级审核通过"));
        assertEquals("pending", middle.path("status").asText());
        assertEquals(902, middle.path("approvalUserId").asLong());
        assertEquals(2, middle.path("workflow").path("history").size());
        assertEquals("一级审核通过", middle.path("workflow").path("history").get(0).path("comment").asText());
        call(admin, post(base + "/" + id + "/sign"), Map.of("revision", middle.path("revision").asInt(), "signedDate", today.toString()), 409);
        call(reviewer, post("/activiti/process/complete"), Map.of("instanceId", instanceId, "taskId", middle.path("workflow").path("taskId").asText(), "variables", Map.of("pass", true)), 403);
        call(outsider, post("/activiti/process/complete"), Map.of("instanceId", instanceId, "taskId", middle.path("workflow").path("taskId").asText(), "variables", Map.of("pass", true, "comment", "最终审批通过")), 200);
        JsonNode approved = call(admin, get(base + "/" + id), null, 200).path("data");
        assertEquals("approved", approved.path("status").asText());
        assertEquals("最终审批通过", approved.path("approvalComment").asText());
        assertEquals(0, approved.path("effectiveVersion").asInt());
        assertEquals("approved", jdbc.queryForObject("SELECT status FROM oa_process_instance WHERE id = ?", String.class, instanceId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM oa_todo WHERE process_instance_id = ? AND status = 'todo'", Integer.class, instanceId));
        assertEquals(2, call(reviewer, get(base + "/" + id), null, 200).path("data").path("workflow").path("history").size());
    }

    @Test
    void genericActionAndGenericTodoRejectThenResubmitAsANewImmutableVersion() throws Exception {
        long id = createDraft(createCustomer("统一待办退回客户")).path("id").asLong();
        JsonNode request = call(admin, post("/workflow/action/execute"), Map.of("moduleId", 341201, "actionCode", "submit", "payload", Map.of("contractId", id, "revision", 0)), 200).path("data");
        assertEquals("pendingApproval", request.path("status").asText());
        Map<String, Object> task = Map.of("instanceId", request.path("instanceId").asText(), "taskId", request.path("taskId").asText(), "variables", Map.of("pass", false, "comment", ""));
        call(reviewer, post("/activiti/process/complete"), task, 400);
        call(reviewer, post("/project/activity/complete"), Map.of("instanceId", request.path("instanceId").asText(), "taskId", request.path("taskId").asText(), "variables", Map.of("pass", false, "comment", "请完善保证期")), 200);
        JsonNode rejected = call(admin, get(base + "/" + id), null, 200).path("data");
        assertEquals("rejected", rejected.path("status").asText());
        assertEquals("请完善保证期", rejected.path("versions").get(0).path("approvalComment").asText());
        JsonNode originalSnapshot = rejected.path("versions").get(0).path("snapshot").deepCopy();
        JsonNode pending = action(admin, id, "submit", Map.of("revision", rejected.path("revision").asInt()));
        assertEquals(2, pending.path("versionNo").asInt());
        assertNotEquals(request.path("instanceId").asText(), pending.path("workflow").path("instanceId").asText());
        assertEquals(originalSnapshot, pending.path("versions").get(1).path("snapshot"));
        assertEquals("rejected", pending.path("versions").get(1).path("workflow").path("status").asText());
        JsonNode approved = action(reviewer, id, "approve", Map.of("revision", pending.path("revision").asInt(), "decision", "approved"));
        assertEquals("approved", approved.path("status").asText());
    }

    @Test
    void designedSelfApprovalIsRejected() throws Exception {
        publishContractFlow("13610316814");
        long id = createDraft(createCustomer("禁止流程自审批客户")).path("id").asLong();
        JsonNode result = call(admin, post(base + "/" + id + "/submit"), Map.of("revision", 0), 400);
        assertTrue(result.path("msg").asText().contains("本人审批"));
    }

    @Test
    void unsupportedContractDelegationCannotCloseOrCompleteTheCurrentTask() throws Exception {
        long id = createDraft(createCustomer("审批转办保护客户")).path("id").asLong();
        JsonNode pending = action(admin, id, "submit", Map.of("revision", 0));
        String taskId = pending.path("workflow").path("taskId").asText();
        call(reviewer, post("/project/activity/delegate"), Map.of("taskId", taskId, "delegateToUser", "17690000902", "type", "workflowAction"), 409);
        call(reviewer, post("/project/activity/delegate"), Map.of("taskId", taskId, "instanceId", pending.path("workflow").path("instanceId").asText(), "delegateToUser", "17690000902"), 409);
        assertEquals("todo", jdbc.queryForObject("SELECT status FROM oa_process_task WHERE id = ?", String.class, taskId));
        assertEquals("pending", call(admin, get(base + "/" + id), null, 200).path("data").path("status").asText());
    }

    @Test
    void staleTaskCannotAdvanceAnotherNodeAssignedToTheSameReviewer() throws Exception {
        publishContractFlow("17690000901", "17690000901");
        long id = createDraft(createCustomer("同人连续节点客户")).path("id").asLong();
        JsonNode pending = action(admin, id, "submit", Map.of("revision", 0));
        long requestId = pending.path("workflow").path("requestId").asLong();
        String instanceId = pending.path("workflow").path("instanceId").asText();
        String oldTaskId = pending.path("workflow").path("taskId").asText();
        JsonNode middle = action(reviewer, id, "approve", Map.of("revision", pending.path("revision").asInt(), "decision", "approved"));
        assertEquals("pending", middle.path("status").asText());
        assertEquals(901, middle.path("approvalUserId").asLong());
        ApiException exception = assertThrows(ApiException.class, () -> contracts.validateWorkflowDecision(
                requestId, Map.of("contractId", id, "versionNo", 1), instanceId, oldTaskId, 901L, true, "重复旧请求"));
        assertTrue(exception.getMessage().contains("节点已变更"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM oa_process_task WHERE instance_id = ?", Integer.class, instanceId));
        assertNotEquals(oldTaskId, middle.path("workflow").path("taskId").asText());
    }

    @Test
    void signingApprovalKeepsContractInactiveUntilAllNodesPassAndFreezesTheFlow() throws Exception {
        long customer = createCustomer("签署审批闭环客户");
        JsonNode approved = approveDraft(createDraft(customer));
        long id = approved.path("id").asLong();
        publishSignFlow("17690000901", "17690000902");
        JsonNode pending = action(admin, id, "sign", Map.of("revision", approved.path("revision").asInt(), "signedDate", today.toString()));
        assertEquals("approved", pending.path("status").asText());
        assertTrue(pending.path("signingPending").asBoolean());
        assertEquals(0, pending.path("effectiveVersion").asInt());
        assertEquals(0, pending.path("effectiveAmount").decimalValue().signum());
        assertEquals(0, jdbc.queryForObject("SELECT contract_amount FROM oa_merchant WHERE id = ?", BigDecimal.class, customer).signum());
        assertEquals("approved", pending.path("workflow").path("status").asText());
        JsonNode task = pending.path("signingWorkflow");
        JsonNode todos = call(reviewer, get("/project/activity/taskWaitList").param("businessCategory", "contract"), null, 200).path("rows");
        assertTrue(todos.toString().contains(task.path("taskId").asText()));
        JsonNode record = call(reviewer, get(base + "/" + id), null, 200).path("data");
        assertTrue(record.path("signingPending").asBoolean());
        completeSigning(reviewer, task, true, "第一节点通过", 200);
        JsonNode middle = call(admin, get(base + "/" + id), null, 200).path("data");
        assertTrue(middle.path("signingPending").asBoolean());
        assertEquals(0, middle.path("effectiveVersion").asInt());
        publishSignFlow("17690000901");
        call(admin, put("/system/module/341201/actions/sign"), Map.of("flowDesignEnabled", false), 200);
        completeSigning(outsider, middle.path("signingWorkflow"), true, "第二节点通过", 200);
        JsonNode active = call(admin, get(base + "/" + id), null, 200).path("data");
        assertEquals("active", active.path("status").asText());
        assertEquals(1, active.path("effectiveVersion").asInt());
        assertFalse(active.path("signingPending").asBoolean());
        assertEquals("approved", active.path("signingWorkflow").path("status").asText());
        assertEquals(2, active.path("signingWorkflow").path("history").size());
        assertEquals(today.toString(), active.path("versions").get(0).path("signedDate").asText().substring(0, 10));
        assertEquals(new BigDecimal("1000.00"), jdbc.queryForObject("SELECT contract_amount FROM oa_merchant WHERE id = ?", BigDecimal.class, customer));
        completeSigning(outsider, middle.path("signingWorkflow"), true, "重复完成", 200);
        assertEquals(active.path("revision"), call(admin, get(base + "/" + id), null, 200).path("data").path("revision"));
    }

    @Test
    void signingCannotBypassApprovalOrChangeVersionWhilePending() throws Exception {
        JsonNode approved = approveDraft(createDraft(createCustomer("签署防绕过客户")));
        long id = approved.path("id").asLong();
        publishSignFlow("17690000901");
        JsonNode pending = action(admin, id, "sign", Map.of("revision", approved.path("revision").asInt(), "signedDate", today.toString()));
        Map<String, Object> data = Map.of("revision", pending.path("revision").asInt(), "signedDate", today.toString());
        call(admin, post(base + "/" + id + "/sign"), data, 409);
        call(admin, post(base + "/" + id + "/revision"), data, 409);
        call(admin, post("/workflow/action/execute"), Map.of("moduleId", 341201, "actionCode", "sign", "payload", Map.of("contractId", id, "revision", pending.path("revision").asInt(), "signedDate", today.toString())), 409);
        call(admin, put("/system/module/341201/actions/sign"), Map.of("flowDesignEnabled", false), 200);
        call(admin, post(base + "/" + id + "/sign"), data, 409);
        completeSigning(reviewer, pending.path("signingWorkflow"), true, "通过", 200);
        assertEquals("active", call(admin, get(base + "/" + id), null, 200).path("data").path("status").asText());
    }

    @Test
    void rejectedSigningCanBeResubmittedAndRetainsEveryAttempt() throws Exception {
        JsonNode approved = approveDraft(createDraft(createCustomer("签署退回重提客户")));
        long id = approved.path("id").asLong();
        publishSignFlow("17690000901");
        JsonNode pending = action(admin, id, "sign", Map.of("revision", approved.path("revision").asInt(), "signedDate", today.toString()));
        completeSigning(outsider, pending.path("signingWorkflow"), true, "无权处理", 403);
        completeSigning(reviewer, pending.path("signingWorkflow"), false, "", 400);
        completeSigning(reviewer, pending.path("signingWorkflow"), false, "请核实签署原件", 200);
        JsonNode rejected = call(admin, get(base + "/" + id), null, 200).path("data");
        assertEquals("approved", rejected.path("status").asText());
        assertEquals(0, rejected.path("effectiveVersion").asInt());
        assertEquals("rejected", rejected.path("signingWorkflow").path("status").asText());
        JsonNode again = action(admin, id, "sign", Map.of("revision", rejected.path("revision").asInt(), "signedDate", today.toString()));
        assertNotEquals(pending.path("signingWorkflow").path("requestId"), again.path("signingWorkflow").path("requestId"));
        assertEquals(2, again.path("versions").get(0).path("signingHistory").size());
        completeSigning(reviewer, again.path("signingWorkflow"), true, "原件已核实", 200);
        JsonNode active = call(admin, get(base + "/" + id), null, 200).path("data");
        assertEquals("active", active.path("status").asText());
        assertEquals("rejected", active.path("versions").get(0).path("signingHistory").get(0).path("status").asText());
    }

    @Test
    void signingRequiresValidIndependentReviewersWhenPublished() throws Exception {
        JsonNode approved = approveDraft(createDraft(createCustomer("签署配置校验客户")));
        long id = approved.path("id").asLong();
        Map<String, Object> data = Map.of("revision", approved.path("revision").asInt(), "signedDate", today.toString());
        call(admin, put("/system/module/341201/actions/sign"), Map.of("flowDesignEnabled", true), 200);
        publishSignFlow("13610316814");
        // MockMvc shares this test's outer transaction; emulate a failed HTTP request rollback.
        java.sql.Connection connection = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(jdbc.getDataSource());
        java.sql.Savepoint requestSavepoint = connection.setSavepoint();
        call(admin, post(base + "/" + id + "/sign"), data, 400);
        connection.rollback(requestSavepoint);
        connection.releaseSavepoint(requestSavepoint);
        publishSignFlow("17690000901");
        jdbc.update("DELETE FROM sys_role_module_action WHERE role_id = 991 AND module_id = 341202 AND action_code = 'approve'");
        requestSavepoint = connection.setSavepoint();
        call(admin, post(base + "/" + id + "/sign"), data, 400);
        connection.rollback(requestSavepoint);
        connection.releaseSavepoint(requestSavepoint);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM oa_crm_contract WHERE effective_version > 0", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM oa_workflow_request WHERE action_code = 'sign'", Integer.class));
        assertEquals("approved", call(admin, get(base + "/" + id), null, 200).path("data").path("status").asText());
    }

    @Test
    void enabledDesignAndDraftAloneNeverStartSigningApproval() throws Exception {
        call(admin, put("/system/module/341201/actions/sign"), Map.of("flowDesignEnabled", true), 200);
        JsonNode first = approveDraft(createDraft(createCustomer("仅开启签署设计客户")));
        long firstId = first.path("id").asLong();
        assertFalse(first.path("signApprovalEnabled").asBoolean());
        JsonNode direct = action(admin, firstId, "sign", Map.of("revision", first.path("revision").asInt(), "signedDate", today.toString()));
        assertEquals("active", direct.path("status").asText());
        call(admin, post("/activiti/mongo/setProcess"), Map.of("menuId", 341201, "actionCode", "sign",
                "draft", true, "approvalEnabled", true, "processData", Map.of("nodes", List.of())), 200);
        JsonNode second = approveDraft(createDraft(createCustomer("签署草稿未发布客户")));
        assertFalse(second.path("signApprovalEnabled").asBoolean());
        JsonNode afterDraft = action(admin, second.path("id").asLong(), "sign", Map.of("revision", second.path("revision").asInt(), "signedDate", today.toString()));
        assertEquals("active", afterDraft.path("status").asText());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM oa_workflow_request WHERE action_code = 'sign'", Integer.class));
    }

    @Test
    void unpublishedOrDisabledSigningApprovalUsesTheExistingSigningBehavior() throws Exception {
        publishSignFlow("17690000901");
        jdbc.update("UPDATE oa_process_design SET status = 'draft' WHERE id = (SELECT process_design_id FROM oa_module_action_flow WHERE module_id = 341201 AND action_code = 'sign')");
        JsonNode first = approveDraft(createDraft(createCustomer("未发布签署流程客户")));
        JsonNode active = action(admin, first.path("id").asLong(), "sign", Map.of("revision", first.path("revision").asInt(), "signedDate", today.toString()));
        assertEquals("active", active.path("status").asText());
        publishSignFlow("17690000901");
        jdbc.update("UPDATE oa_module_action_flow SET approval_enabled = 0 WHERE module_id = 341201 AND action_code = 'sign'");
        JsonNode second = approveDraft(createDraft(createCustomer("已关闭签署审批客户")));
        assertFalse(second.path("signApprovalEnabled").asBoolean());
        assertEquals("active", action(admin, second.path("id").asLong(), "sign", Map.of("revision", second.path("revision").asInt(), "signedDate", today.toString())).path("status").asText());
    }

    @Test
    void signingExecutorMigrationPreservesEditableSettingsAndRunsOnlyOnce() {
        jdbc.update("UPDATE sys_module_action SET handler_key = NULL, action_name = '用印签署', permission_code = 'custom:sign', flow_design_enabled = 1, action_sort = 8 WHERE module_id = 341201 AND action_code = 'sign'");
        jdbc.update("DELETE FROM sys_permission_migration WHERE migration_key = 'contract_sign_workflow_v1'");
        moduleActions.bootstrap();
        Map<String, Object> row = moduleActions.action(341201, "sign");
        assertEquals("crm.contract.sign", row.get("handlerKey"));
        assertEquals("用印签署", row.get("actionName"));
        assertEquals("custom:sign", row.get("permissionCode"));
        assertTrue(ModuleActionService.enabled(row.get("flowDesignEnabled")));
        assertEquals(8, ((Number) row.get("actionSort")).intValue());
        assertEquals(Boolean.TRUE, row.get("handlerManaged"));
        jdbc.update("UPDATE sys_module_action SET flow_design_enabled = 0 WHERE module_id = 341201 AND action_code = 'sign'");
        moduleActions.bootstrap();
        assertFalse(ModuleActionService.enabled(moduleActions.action(341201, "sign").get("flowDesignEnabled")));
    }

    private JsonNode approveDraft(JsonNode draft) throws Exception {
        long id = draft.path("id").asLong();
        JsonNode pending = action(admin, id, "submit", Map.of("revision", draft.path("revision").asInt()));
        return action(reviewer, id, "approve", Map.of("revision", pending.path("revision").asInt(), "decision", "approved"));
    }

    private void completeSigning(String auth, JsonNode workflow, boolean pass, String comment, int expected) throws Exception {
        call(auth, post("/activiti/process/complete"), Map.of("instanceId", workflow.path("instanceId").asText(),
                "taskId", workflow.path("taskId").asText(), "variables", Map.of("pass", pass, "comment", comment)), expected);
    }

    private void publishContractFlow(String... approvers) throws Exception {
        publishContractButtonFlow("submit", approvers);
    }

    private void publishSignFlow(String... approvers) throws Exception {
        call(admin, put("/system/module/341201/actions/sign"), Map.of("flowDesignEnabled", true), 200);
        publishContractButtonFlow("sign", approvers);
    }

    private void publishContractButtonFlow(String actionCode, String... approvers) throws Exception {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        nodes.add(Map.of("id", "start", "type", "start", "name", "发起人"));
        String previous = "start";
        for (int index = 0; index < approvers.length; index++) {
            String nodeId = "approval_" + index;
            nodes.add(Map.of("id", nodeId, "type", "approval", "name", "合同审批" + (index + 1), "approver", approvers[index]));
            edges.add(Map.of("source", previous, "target", nodeId));
            previous = nodeId;
        }
        nodes.add(Map.of("id", "end", "type", "end", "name", "流程结束"));
        edges.add(Map.of("source", previous, "target", "end"));
        call(admin, post("/activiti/mongo/setProcess"), Map.of("tenantAppId", 1, "menuId", 341201,
                "menuName", "合同列表", "actionCode", actionCode, "approvalEnabled", true,
                "processData", Map.of("nodes", nodes, "edges", edges)), 200);
    }

    private long createCustomer(String name) throws Exception {
        return call(admin, post("/project/merchant"), Map.of("name", name, "industry", "软件与信息服务", "ownerUserId", 1), 200).path("data").asLong();
    }
    private Map<String, Object> term(int amount) {
        return new LinkedHashMap<>(Map.of("key", "first", "title", "服务费", "chargeMode", "fixed", "amount", amount, "trigger", "候选人入职", "dueDate", today.minusDays(1).toString(), "guaranteeDays", 90));
    }
    private Map<String, Object> payload(long customer) {
        return new LinkedHashMap<>(Map.of("customerId", customer, "name", "年度招聘合同", "serviceType", "猎头招聘", "amount", 1000, "startDate", today.minusDays(30).toString(), "endDate", today.plusDays(15).toString(), "feeTerms", new ArrayList<>(List.of(term(1000)))));
    }
    private JsonNode createDraft(long customer) throws Exception { return call(admin, post(base), payload(customer), 200).path("data"); }
    private JsonNode activate(JsonNode draft) throws Exception {
        long id = draft.path("id").asLong();
        JsonNode pending = action(admin, id, "submit", Map.of("revision", draft.path("revision").asInt(), "approvalUserId", 901));
        JsonNode approved = action(reviewer, id, "approve", Map.of("revision", pending.path("revision").asInt(), "decision", "approved"));
        return action(admin, id, "sign", Map.of("revision", approved.path("revision").asInt(), "signedDate", today.toString()));
    }
    private JsonNode action(String auth, long id, String action, Object data) throws Exception { return call(auth, post(base + "/" + id + "/" + action), data, 200).path("data"); }
    private String login(String name) throws Exception { return "Bearer " + call(null, post("/login"), Map.of("username", name, "password", "admin123123"), 200).path("token").asText(); }
    private JsonNode call(String auth, MockHttpServletRequestBuilder request, Object body, int expected) throws Exception {
        if (auth != null) request.header("Authorization", auth);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        JsonNode result = mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(expected, result.path("code").asInt(), result.toString());
        return result;
    }
    private JsonNode find(JsonNode nodes, long id) {
        for (JsonNode node : nodes) { if (node.path("moduleId").asLong() == id) return node; JsonNode nested = find(node.path("children"), id); if (nested != null) return nested; }
        return null;
    }
}
