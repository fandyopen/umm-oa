package com.seal.oa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.oa.CrmContractService;
import com.seal.oa.oa.CustomerFinanceService;
import com.seal.oa.system.ModuleService;
import com.seal.oa.workflow.WorkflowActionContext;
import com.seal.oa.workflow.WorkflowActionRequestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = """
        spring.datasource.url=jdbc:h2:mem:finance_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1
        """)
@Transactional
@AutoConfigureMockMvc
class CustomerFinanceIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CustomerFinanceService finance;
    @Autowired private WorkflowActionRequestService requests;
    @Autowired private CrmContractService contracts;
    @Autowired private ModuleService modules;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    private long customerId;

    @BeforeEach void prepare() {
        AuthContext.set(new AuthContext.CurrentUser(1L, "13610316814"));
        jdbc.update("INSERT INTO oa_merchant(name,owner_user_id,owner_user_name,public_pool,status,create_by) VALUES(?,1,'系统管理员','0','0','test')",
                "财务集成客户-" + UUID.randomUUID());
        customerId = jdbc.queryForObject("SELECT MAX(id) FROM oa_merchant", Long.class);
    }

    @AfterEach void clear() { AuthContext.clear(); }

    @Test
    void financeMenuMigratesToHeaderWithoutChangingButtonOrWorkflowSettings() {
        jdbc.update("DELETE FROM sys_role_module WHERE module_id BETWEEN 341401 AND 341408");
        jdbc.update("DELETE FROM sys_module WHERE module_id BETWEEN 341401 AND 341408");
        jdbc.update("INSERT INTO sys_role_module (role_id,module_id) SELECT 7,341400 WHERE NOT EXISTS (SELECT 1 FROM sys_role_module WHERE role_id=7 AND module_id=341400)");
        jdbc.update("UPDATE sys_module SET parent_id=340310,path='finance',module_sort=6 WHERE module_id=341400");
        jdbc.update("UPDATE sys_module_action SET action_name='自定义回款确认',flow_design_enabled=1 WHERE module_id=341400 AND action_code='receipt_confirm'");
        var buttons = jdbc.queryForList("SELECT * FROM sys_module_action WHERE module_id=341400 ORDER BY action_code");
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("customer-finance-menu.sql"));
            return null;
        });
        var roots = modules.routers(1L, true, java.util.Set.of(), java.util.Set.of());
        var menu = roots.stream().filter(row -> number(row.get("moduleId")) == 341400L).findFirst().orElseThrow();
        assertEquals(0L, number(menu.get("parentId")));
        assertEquals("/finance", menu.get("path"));
        assertEquals("/finance/ledger", menu.get("redirect"));
        assertEquals("eos/crm/finance/index", menu.get("component"));
        @SuppressWarnings("unchecked")
        var children = (java.util.List<Map<String,Object>>) menu.get("children");
        assertEquals(java.util.List.of("ledger", "invoice", "receipt", "refund", "adjustment", "cost", "collections", "reconcile", "payroll", "commission"),
                children.stream().map(child -> child.get("path")).toList());
        assertEquals(10L, children.stream().map(child -> child.get("component")).distinct().count());
        assertTrue(children.stream().allMatch(child -> Boolean.FALSE.equals(child.get("hidden"))));
        assertEquals(8, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_module WHERE role_id=7 AND module_id BETWEEN 341401 AND 341408", Integer.class));
        var allowed = new java.util.HashSet<>(jdbc.queryForList("SELECT module_id FROM sys_role_module WHERE role_id=7", Long.class));
        var roleMenu = modules.routers(1L, false, java.util.Set.of("finance:ledger:list"), allowed).stream()
                .filter(row -> number(row.get("moduleId")) == 341400L).findFirst().orElseThrow();
        assertEquals(children.stream().filter(child -> number(child.get("moduleId")) != 341409L && number(child.get("moduleId")) != 341410L).toList(), roleMenu.get("children"));
        assertTrue(roots.stream().anyMatch(row -> number(row.get("moduleId")) == 341300L));
        assertEquals(buttons, jdbc.queryForList("SELECT * FROM sys_module_action WHERE module_id=341400 ORDER BY action_code"));

        jdbc.update("UPDATE sys_module SET module_sort=8 WHERE module_id=341400");
        jdbc.update("UPDATE sys_module SET module_name='自定义开票',hidden=1,module_sort=20 WHERE module_id=341402");
        jdbc.update("DELETE FROM sys_role_module WHERE role_id=7 AND module_id=341402");
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("customer-finance-menu.sql"));
            return null;
        });
        assertEquals(8, jdbc.queryForObject("SELECT module_sort FROM sys_module WHERE module_id=341400", Integer.class));
        assertEquals("自定义开票", jdbc.queryForObject("SELECT module_name FROM sys_module WHERE module_id=341402", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT hidden FROM sys_module WHERE module_id=341402", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_module WHERE role_id=7 AND module_id=341402", Integer.class));
        assertEquals(buttons, jdbc.queryForList("SELECT * FROM sys_module_action WHERE module_id=341400 ORDER BY action_code"));
    }

    @Test
    void receiptsEndpointCombinesContractAndManualReceiptsWithoutDraftsOrDuplicateRows() throws Exception {
        long source = confirmedReceivable("100.00");
        Map<String,Object> manualReceipt = draft("receipt", source, "50.00");
        approveDirect(manualReceipt, "receipt_confirm");
        draft("receipt", source, "5.00");

        jdbc.update("INSERT INTO oa_crm_contract(customer_id,code,name,service_type,amount,start_date,end_date,status,terms_json) VALUES(?,?,?,'猎头服务',100,'2026-01-01','2027-12-31','active','[]')",
                customerId, "FIN-RECEIPTS-" + UUID.randomUUID(), "原到账查询合同");
        long contractId = jdbc.queryForObject("SELECT MAX(id) FROM oa_crm_contract", Long.class);
        jdbc.update("INSERT INTO oa_crm_contract_receipt(contract_id,term_key,amount,receipt_date) VALUES(?,'fee-main',20,'2026-11-01')", contractId);
        jdbc.update("INSERT INTO oa_crm_contract_receipt(contract_id,term_key,amount,receipt_date) VALUES(?,'fee-main',-5,'2026-11-02')", contractId);

        Map<String,String> query = Map.of("customerId", String.valueOf(customerId), "pageNum", "1", "pageSize", "1000");
        var rows = finance.receipts(query);
        assertEquals(2, rows.size());
        var manual = rows.stream().filter(row -> row.get("originalRecordId") != null).findFirst().orElseThrow();
        assertEquals(manualReceipt.get("id"), manual.get("originalRecordId"));
        assertEquals(source, number(manual.get("receivableId")));
        assertEquals(1, rows.stream().filter(row -> row.get("originalReceiptId") != null).count());
        assertEquals(rows, finance.exportRows("receipts", query));
        assertEquals(1, finance.receipts(Map.of("customerId", String.valueOf(customerId), "contractId", String.valueOf(contractId))).size());
        assertTrue(finance.receipts(Map.of("customerId", String.valueOf(customerId), "startDate", "2026-11-02")).isEmpty());

        AuthContext.set(new AuthContext.CurrentUser(999999L, "unrelated-user"));
        assertTrue(finance.receipts(query).isEmpty());
        AuthContext.set(new AuthContext.CurrentUser(1L, "13610316814"));
        var login = mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", "13610316814", "password", "admin123123"))))
                .andExpect(status().isOk()).andReturn();
        String token = mapper.readTree(login.getResponse().getContentAsByteArray()).path("token").asText();
        mvc.perform(get("/project/crm/finance/receipts").header("Authorization", "Bearer " + token)
                        .param("customerId", String.valueOf(customerId)).param("pageNum", "1").param("pageSize", "1000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void partialReceiptInvoiceRefundAndWriteoffPreserveCashHistory() {
        Map<String,Object> receivable = finance.saveReceivable(0, Map.of(
                "customerId", customerId, "title", "渠道服务费", "amount", new BigDecimal("100.00"),
                "dueDate", "2026-12-01", "sourceEvent", "渠道服务", "revision", 0));
        long receivableId = number(receivable.get("id"));
        finance.confirmReceivable(receivableId, Map.of("revision", 0), true);

        Map<String,Object> receipt = draft("receipt", receivableId, "50.00");
        approveDirect(receipt, "receipt_confirm");
        assertEquals(new BigDecimal("50.00"), new BigDecimal(String.valueOf(ledger(receivableId).get("netReceivedAmount"))));
        assertEquals(new BigDecimal("50.00"), new BigDecimal(String.valueOf(ledger(receivableId).get("outstandingAmount"))));

        Map<String,Object> invoice = draft("invoice", receivableId, "40.00");
        approveDirect(invoice, "invoice_submit");
        Map<String,Object> issued = Map.of("revision", finance.detail(number(invoice.get("id"))).get("revision"),
                "businessDate", "2026-11-01", "invoiceNo", "INV-" + UUID.randomUUID(),
                "attachment", proof());
        finance.operate(number(invoice.get("id")), "issue", issued);

        Map<String,Object> refund = draft("refund", receivableId, "20.00");
        refund.put("originalRecordId", receipt.get("id"));
        refund.put("reason", "重复到账退回");
        finance.save("refund", number(refund.get("id")), refund);
        refund = finance.detail(number(refund.get("id")));
        approveDirect(refund, "refund_submit");
        finance.operate(number(refund.get("id")), "pay", Map.of("revision", finance.detail(number(refund.get("id"))).get("revision"),
                "businessDate", "2026-11-02", "attachment", proof()));

        Map<String,Object> writeoff = draft("writeoff", receivableId, "10.00");
        writeoff.put("reason", "小额尾差核销");
        finance.save("writeoff", number(writeoff.get("id")), writeoff);
        writeoff = finance.detail(number(writeoff.get("id")));
        approveDirect(writeoff, "adjust_confirm");
        Map<String,Object> after = ledger(receivableId);
        assertEquals(new BigDecimal("30.00"), new BigDecimal(String.valueOf(after.get("netReceivedAmount"))));
        assertEquals(new BigDecimal("10.00"), new BigDecimal(String.valueOf(after.get("writeoffAmount"))));
        assertEquals(new BigDecimal("60.00"), new BigDecimal(String.valueOf(after.get("outstandingAmount"))));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM oa_finance_record WHERE record_type IN ('receipt','refund') AND receivable_id=? AND status='confirmed'", Integer.class, receivableId));
    }

    @Test
    void submittedReceiptsAndInvoicesCannotExceedConfirmedReceivable() {
        Map<String,Object> receivable = finance.saveReceivable(0, Map.of(
                "customerId", customerId, "title", "项目费用", "amount", new BigDecimal("10.00"),
                "dueDate", "2026-12-01", "revision", 0));
        long id = number(receivable.get("id"));
        finance.confirmReceivable(id, Map.of("revision", 0), true);

        Map<String,Object> receipt = draft("receipt", id, "10.00");
        approveDirect(receipt, "receipt_confirm");
        Map<String,Object> excessReceipt = draft("receipt", id, "0.01");
        assertTrue(text(excessReceipt.get("submissionBlockedReason")).contains("已收齐"));
        assertEquals(new BigDecimal("0.00"), excessReceipt.get("receiptAvailable"));
        assertTrue(assertThrows(ApiException.class, () -> approveDirect(excessReceipt, "receipt_confirm")).getMessage().contains("已收齐"));

        Map<String,Object> invoice = draft("invoice", id, "10.00");
        approveDirect(invoice, "invoice_submit");
        Map<String,Object> excessInvoice = draft("invoice", id, "0.01");
        assertThrows(RuntimeException.class, () -> approveDirect(excessInvoice, "invoice_submit"));
    }

    @Test
    void confirmedPaymentClosesTheOpenCollectionPlan() {
        Map<String,Object> receivable = finance.saveReceivable(0, Map.of(
                "customerId", customerId, "title", "催收闭环校验", "amount", new BigDecimal("25.00"),
                "dueDate", "2026-12-01", "revision", 0));
        long id = number(receivable.get("id"));
        finance.confirmReceivable(id, Map.of("revision", 0), true);
        finance.collect(Map.of("receivableId", id, "ownerUserId", 4, "nextDate", java.time.LocalDate.now().plusDays(7).toString(), "content", "跟进回款"));

        approveDirect(draft("receipt", id, "25.00"), "receipt_confirm");

        assertTrue(finance.collections(Map.of()).stream().anyMatch(row -> "closed".equals(row.get("status"))
                && number(row.get("receivableId")) == id));
    }

    @Test
    void genericWorkflowFreezesRecordAndReservesBalanceUntilRejection() {
        long source = confirmedReceivable("100.00");
        Map<String,Object> pending = draft("receipt", source, "70.00");
        long request = submit(pending, "receipt_confirm");
        assertEquals("approving", finance.detail(number(pending.get("id"))).get("status"));
        assertEquals(Boolean.TRUE, requests.payload(request).get("financeRecord"));
        assertEquals(new BigDecimal("30.00"), ledger(source).get("receiptAvailable"));
        Map<String,Object> excess = draft("writeoff", source, "40.00");
        assertTrue(text(excess.get("submissionBlockedReason")).contains("待审申请已占用 70.00"));
        assertEquals(new BigDecimal("30.00"), excess.get("receiptAvailable"));
        assertTrue(assertThrows(ApiException.class, () -> approveDirect(excess, "adjust_confirm")).getMessage().contains("当前可提交金额为 30.00"));
        assertFalse(text(finance.detail(number(pending.get("id"))).get("submissionBlockedReason")).contains("占用"));

        requests.updateWorkflowState("test-rejected", request, "审批未通过", "test-task", "财务审核");
        assertEquals("rejected", finance.detail(number(pending.get("id"))).get("status"));
        assertEquals(new BigDecimal("100.00"), ledger(source).get("receiptAvailable"));
        assertEquals(0, ((BigDecimal) ledger(source).get("netReceivedAmount")).signum());
        assertEquals("", finance.detail(number(excess.get("id"))).get("submissionBlockedReason"));
        approveDirect(finance.detail(number(excess.get("id"))), "adjust_confirm");
    }

    @Test
    @SuppressWarnings("unchecked")
    void submissionGuidanceOnlyLinksConfirmedReceiptsFromTheSameSourceAndKeepsDrafts() {
        long source = confirmedReceivable("50.00"), otherSource = confirmedReceivable("50.00");
        Map<String,Object> receipt = draft("receipt", source, "50.00");
        approveDirect(receipt, "receipt_confirm");
        approveDirect(draft("receipt", otherSource, "50.00"), "receipt_confirm");
        draft("receipt", source, "1.00");
        Map<String,Object> blocked = draft("receipt", source, "5.00");
        Map<String,Object> row = ((List<Map<String,Object>>) finance.records("receipt", Map.of()).get("rows")).stream()
                .filter(r -> number(r.get("id")) == number(blocked.get("id"))).findFirst().orElseThrow();
        List<Map<String,Object>> related = (List<Map<String,Object>>) row.get("confirmedReceipts");
        assertEquals(1, related.size());
        assertEquals(receipt.get("id"), related.get(0).get("id"));
        assertEquals(new BigDecimal("50.00"), related.get(0).get("amount"));
        assertEquals("draft", finance.detail(number(blocked.get("id"))).get("status"));
        assertTrue(text(row.get("submissionBlockedReason")).contains("已收齐"));
        AuthContext.set(new AuthContext.CurrentUser(999999L, "unrelated-user"));
        assertTrue(((List<?>) finance.records("receipt", Map.of()).get("rows")).isEmpty());
        assertThrows(ApiException.class, () -> finance.detail(number(blocked.get("id"))));
    }

    @Test
    void submissionGuidanceSeparatesExcessAmountFromPendingReservation() {
        long source = confirmedReceivable("100.00");
        submit(draft("receipt", source, "70.00"), "receipt_confirm");
        Map<String,Object> excess = draft("receipt", source, "101.00");
        assertTrue(text(excess.get("submissionBlockedReason")).contains("超过未收余额 100.00"));
        assertTrue(assertThrows(ApiException.class, () -> approveDirect(excess, "receipt_confirm")).getMessage().contains("超过未收余额"));
        Map<String,Object> allowed = draft("receipt", source, "30.00");
        assertEquals("", allowed.get("submissionBlockedReason"));
        approveDirect(allowed, "receipt_confirm");
        assertEquals(new BigDecimal("30.00"), ledger(source).get("netReceivedAmount"));
    }

    @Test
    void genericWorkflowApprovalExecutesReceiptExactlyOnce() {
        long source = confirmedReceivable("100.00");
        long request = submit(draft("receipt", source, "60.00"), "receipt_confirm");
        requests.updateWorkflowState("test-approved", request, "审批通过", "test-task", "完成");
        requests.updateWorkflowState("test-approved", request, "审批通过", "test-task", "完成");
        assertEquals(new BigDecimal("60.00"), ledger(source).get("netReceivedAmount"));
        assertEquals(new BigDecimal("40.00"), ledger(source).get("outstandingAmount"));
    }

    @Test
    void receiptCannotEnterApprovalWithoutProofAndCannotUseStaleRevision() {
        long source = confirmedReceivable("100.00");
        Map<String,Object> receipt = draft("receipt", source, "30.00");
        long id = number(receipt.get("id"));
        receipt.put("attachment", "[]");
        finance.save("receipt", id, receipt);
        assertThrows(ApiException.class, () -> submit(finance.detail(id), "receipt_confirm"));
        assertEquals("draft", finance.detail(id).get("status"));
        assertThrows(ApiException.class, () -> finance.save("receipt", id, receipt));
    }

    @Test
    void writeoffCannotEnterApprovalWithoutBusinessDate() {
        long source = confirmedReceivable("100.00");
        Map<String,Object> row = draft("writeoff", source, "10.00");
        long id = number(row.get("id"));
        row.remove("businessDate");
        finance.save("writeoff", id, row);
        assertThrows(ApiException.class, () -> submit(finance.detail(id), "adjust_confirm"));
        assertEquals("draft", finance.detail(id).get("status"));
    }

    @Test
    void refundApprovalDoesNotChangeCashBeforeActualPayment() {
        long source = confirmedReceivable("100.00");
        Map<String,Object> receipt = draft("receipt", source, "50.00");
        approveDirect(receipt, "receipt_confirm");
        Map<String,Object> refund = draft("refund", source, "20.00");
        refund.put("originalRecordId", receipt.get("id"));
        finance.save("refund", number(refund.get("id")), refund);
        approveDirect(refund, "refund_submit");
        assertEquals(new BigDecimal("50.00"), ledger(source).get("netReceivedAmount"));
        Map<String,Object> otherRefund = draft("refund", source, "31.00");
        otherRefund.put("originalRecordId", receipt.get("id"));
        finance.save("refund", number(otherRefund.get("id")), otherRefund);
        assertThrows(ApiException.class, () -> approveDirect(otherRefund, "refund_submit"));
        assertThrows(ApiException.class, () -> finance.operate(number(receipt.get("id")), "void",
                Map.of("revision", finance.detail(number(receipt.get("id"))).get("revision"), "reason", "不能删除原始到账")));
    }

    @Test
    void updatingCollectionKeepsOneTaskAndRecordsTheHistory() {
        long source = confirmedReceivable("100.00");
        Map<String,Object> body = new LinkedHashMap<>(Map.of("receivableId", source, "ownerUserId", 4,
                "nextDate", java.time.LocalDate.now().plusDays(7).toString(), "content", "首次催收"));
        finance.collect(body);
        Map<String,Object> task = finance.collections(Map.of()).get(0);
        body.put("collectionId", task.get("id")); body.put("content", "确认下周到账");
        finance.collect(body);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oa_finance_collection WHERE receivable_id=?", Integer.class, source));
        assertEquals(1, finance.collections(Map.of("keyword", "确认下周到账")).size());
        body.put("close", true); body.put("reason", "已另行确认");
        finance.collect(body);
        assertEquals("closed", finance.collections(Map.of()).get(0).get("status"));
    }

    @Test
    void financialRowsRemainScopedToCustomerOwner() {
        long source = confirmedReceivable("100.00");
        Map<String,Object> invoice = draft("invoice", source, "10.00");
        AuthContext.set(new AuthContext.CurrentUser(999999L, "unrelated-user"));
        assertTrue(((java.util.List<?>) finance.ledger(Map.of()).get("rows")).isEmpty());
        assertThrows(ApiException.class, () -> finance.detail(number(invoice.get("id"))));
    }

    @Test
    void contractRefundKeepsContractCustomerAndFinanceBalancesAligned() {
        String terms = "[{\"key\":\"fee-main\",\"title\":\"服务费\",\"amount\":100,\"dueDate\":\"2026-12-01\"}]";
        String snapshot = "{\"amount\":100,\"endDate\":\"2027-12-31\",\"feeTerms\":" + terms + "}";
        jdbc.update("INSERT INTO oa_crm_contract(customer_id,code,name,service_type,amount,start_date,end_date,status,effective_version,terms_json) VALUES(?,?,?,'猎头服务',100,'2026-01-01','2027-12-31','active',1,?)",
                customerId, "FIN-" + UUID.randomUUID(), "回款一致性合同", terms);
        long contract = jdbc.queryForObject("SELECT MAX(id) FROM oa_crm_contract", Long.class);
        jdbc.update("INSERT INTO oa_crm_contract_version(contract_id,version_no,snapshot_json,status,submit_user_id,approval_user_id) VALUES(?,1,?,'signed',1,1)", contract, snapshot);
        Map<String,Object> receipt = finance.save("receipt", 0, Map.of("contractId", contract, "termKey", "fee-main",
                "amount", new BigDecimal("50.00"), "businessDate", "2026-11-01", "attachment", proof(), "createToken", UUID.randomUUID().toString()));
        approveDirect(receipt, "receipt_confirm");
        long original = number(finance.detail(number(receipt.get("id"))).get("receiptId"));
        Map<String,Object> refund = finance.save("refund", 0, Map.of("contractId", contract, "termKey", "fee-main",
                "originalReceiptId", original, "amount", new BigDecimal("20.00"), "businessDate", "2026-11-02",
                "reason", "退回重复款项", "createToken", UUID.randomUUID().toString()));
        approveDirect(refund, "refund_submit");
        finance.operate(number(refund.get("id")), "pay", Map.of("revision", finance.detail(number(refund.get("id"))).get("revision"), "businessDate", "2026-11-02", "attachment", proof()));
        assertEquals(new BigDecimal("30.00"), contracts.detail(contract).get("receivedAmount"));
        assertEquals(new BigDecimal("70.00"), contracts.detail(contract).get("outstandingAmount"));
        assertEquals(new BigDecimal("30.00"), jdbc.queryForObject("SELECT received_amount FROM oa_merchant WHERE id=?", BigDecimal.class, customerId));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM oa_crm_contract_receipt WHERE contract_id=?", Integer.class, contract));
        Map<String,Object> remaining = finance.save("receipt", 0, Map.of("contractId", contract, "termKey", "fee-main",
                "amount", new BigDecimal("70.00"), "businessDate", "2026-11-02", "attachment", proof(), "createToken", UUID.randomUUID().toString()));
        approveDirect(remaining, "receipt_confirm");
        jdbc.update("UPDATE oa_crm_contract SET status='completed' WHERE id=?", contract);
        Map<String,Object> duplicate = finance.save("receipt", 0, Map.of("contractId", contract, "termKey", "fee-main",
                "amount", new BigDecimal("5.00"), "businessDate", "2026-11-02", "attachment", proof(), "createToken", UUID.randomUUID().toString()));
        assertTrue(text(duplicate.get("submissionBlockedReason")).contains("已收齐"));
        assertTrue(assertThrows(ApiException.class, () -> approveDirect(duplicate, "receipt_confirm")).getMessage().contains("已收齐"));
    }

    @Test
    void refundReopensArchivedContractsAndPreservesTheirTerminationState() {
        for(boolean terminated:java.util.List.of(false,true)) {
            String terms="[{\"key\":\"fee-main\",\"title\":\"服务费\",\"amount\":100,\"dueDate\":\"2026-12-01\"}]";
            String snapshot="{\"amount\":100,\"endDate\":\"2027-12-31\",\"feeTerms\":"+terms+"}";
            jdbc.update("INSERT INTO oa_crm_contract(customer_id,code,name,service_type,amount,start_date,end_date,status,effective_version,terms_json,deleted) VALUES(?,?,?,'猎头服务',100,'2026-01-01','2027-12-31','completed',1,?,1)",customerId,"ARCH-"+UUID.randomUUID(),"已删除结案合同",terms);
            long contract=jdbc.queryForObject("SELECT MAX(id) FROM oa_crm_contract",Long.class);
            jdbc.update("INSERT INTO oa_crm_contract_version(contract_id,version_no,snapshot_json,status,submit_user_id,approval_user_id) VALUES(?,1,?,'signed',1,1)",contract,snapshot);
            jdbc.update("INSERT INTO oa_crm_contract_receipt(contract_id,term_key,amount,receipt_date) VALUES(?,'fee-main',100,'2026-11-01')",contract);
            if(terminated)jdbc.update("INSERT INTO oa_crm_contract_history(customer_id,contract_id,action,reason) VALUES(?,?,'terminate','提前结束')",customerId,contract);
            long original=jdbc.queryForObject("SELECT MAX(id) FROM oa_crm_contract_receipt WHERE contract_id=?",Long.class,contract);
            Map<String,Object> refund=finance.save("refund",0,Map.of("contractId",contract,"termKey","fee-main","originalReceiptId",original,"amount",20,"reason","退回多收款","createToken",UUID.randomUUID().toString()));
            approveDirect(refund,"refund_submit");
            finance.operate(number(refund.get("id")),"pay",Map.of("revision",finance.detail(number(refund.get("id"))).get("revision"),"businessDate","2026-11-02","attachment",proof()));
            assertEquals(0,jdbc.queryForObject("SELECT deleted FROM oa_crm_contract WHERE id=?",Integer.class,contract));
            assertEquals(terminated?"terminated":"active",contracts.detail(contract).get("status"));
            assertEquals(new BigDecimal("80.00"),contracts.detail(contract).get("receivedAmount"));
            assertEquals(new BigDecimal("20.00"),contracts.detail(contract).get("outstandingAmount"));
        }
    }

    private long confirmedReceivable(String amount) {
        Map<String,Object> row = finance.saveReceivable(0, Map.of("customerId", customerId, "title", "应收测试",
                "amount", new BigDecimal(amount), "dueDate", "2026-12-01"));
        long id = number(row.get("id"));
        finance.confirmReceivable(id, Map.of("revision", row.get("revision")), true);
        return id;
    }

    private long submit(Map<String,Object> row, String action) {
        return requests.create(CustomerFinanceService.MODULE, action, "crm.finance." + action, Map.of(), "财务确认",
                Map.of("recordId", row.get("id"), "revision", row.get("revision")), 1L, "13610316814");
    }

    private Map<String,Object> draft(String type, long receivable, String amount) {
        return new LinkedHashMap<>(finance.save(type, 0, new LinkedHashMap<>(Map.of(
                "customerId", customerId, "receivableId", receivable, "amount", new BigDecimal(amount),
                "businessDate", "2026-11-01", "createToken", UUID.randomUUID().toString(),
                "attachment", proof(), "category", "招聘渠道", "invoiceTitle", "客户公司", "reason", "业务测试依据"))));
    }

    private void approveDirect(Map<String,Object> row, String action) {
        long id = number(row.get("id"));
        Map<String,Object> current = finance.detail(id);
        String handler = "crm.finance." + action;
        WorkflowActionContext context = new WorkflowActionContext(0, CustomerFinanceService.MODULE, action, handler,
                Map.of(), new LinkedHashMap<>(Map.of("recordId", id, "revision", current.get("revision"))),
                1L, "13610316814");
        finance.validateWorkflow(context);
        finance.execute(context);
    }

    private Map<String,Object> ledger(long id) {
        return ((java.util.List<Map<String,Object>>)finance.ledger(Map.of()).get("rows")).stream()
                .filter(row -> number(row.get("receivableId")) == id).findFirst().orElseThrow();
    }
    private long number(Object value) { return ((Number)value).longValue(); }
    private String text(Object value) { return value == null ? "" : value.toString(); }
    private String proof() { return "[{\"name\":\"凭证.pdf\",\"url\":\"/profile/finance/proof.pdf\",\"fileName\":\"/profile/finance/proof.pdf\"}]"; }
}
