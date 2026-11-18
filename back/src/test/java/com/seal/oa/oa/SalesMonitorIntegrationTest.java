package com.seal.oa.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.auth.JwtService;
import com.seal.oa.common.ApiException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:sales_monitor;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class SalesMonitorIntegrationTest {
    @Autowired SalesMonitorService monitor;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    final long employee = 99781, colleague = 99782, outsider = 99783, role = 99781, dept = 99781, otherDept = 99782;
    final LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).withNano(0);
    long customer;

    @BeforeEach void setup() {
        jdbc.update("INSERT INTO sys_dept(dept_id,parent_id,dept_name) VALUES (?,326,'销售看板测试部'),(?,326,'其他销售部')", dept, otherDept);
        jdbc.update("INSERT INTO sys_role(role_id,role_name,role_key,data_scope) VALUES (?,'销售看板统计','sales_monitor_test','3')", role);
        for (long id : List.of(employee, colleague, outsider)) {
            jdbc.update("INSERT INTO sys_user(user_id,tenant_id,dept_id,user_name,nick_name,password,status,del_flag) SELECT ?,1,?,?,?,password,'0','0' FROM sys_user WHERE user_id=4", id, id == outsider ? otherDept : dept, "salesmonitor" + id, "销售" + id);
        }
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,?)", employee, role);
        jdbc.update("INSERT INTO sys_role_module(role_id,module_id) VALUES (?,341112)", role);
        customer = customer(employee, now.minusDays(20));
        AuthContext.set(new AuthContext.CurrentUser(employee, "salesmonitor" + employee));
    }
    @AfterEach void clear() { AuthContext.clear(); }

    @Test void moduleGrantAllowsReadOnlyStatisticsWithoutGrantingOtherBusinessPages() throws Exception {
        AuthContext.clear(); String token = "Bearer " + jwt.createToken(employee, "salesmonitor" + employee);
        mvc.perform(get("/project/crm/sales-performance/monitor").header("Authorization", token)).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.summary.customers").value(1));
        mvc.perform(get("/project/crm/sales-performance/monitor/details").param("metric", "customers").header("Authorization", token)).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/project/merchant/list").header("Authorization", token)).andExpect(jsonPath("$.code").value(403));
        jdbc.update("DELETE FROM sys_role_module WHERE role_id=?", role);
        mvc.perform(get("/project/crm/sales-performance/monitor").header("Authorization", token)).andExpect(jsonPath("$.code").value(403));
        mvc.perform(get("/project/crm/sales-performance/monitor/details").param("metric", "customers").header("Authorization", token)).andExpect(jsonPath("$.code").value(403));
    }

    @Test void scopeAndFiltersProtectTotalsOptionsPeopleAndDetails() {
        long other = customer(outsider, now.minusDays(5)), sameDept = customer(colleague, now.minusDays(5));
        JsonNode data = overview(Map.of());
        assertEquals(2, data.path("summary").path("customers").asInt());
        assertEquals(2, data.path("options").path("users").size());
        assertEquals(1, data.path("options").path("departments").size());
        assertFalse(data.path("options").path("customers").toString().contains("客户" + other + "\""));
        assertEquals(0, overview(Map.of("userId", "" + outsider)).path("summary").path("customers").asInt());
        assertEquals(0, detail("customers", Map.of("customerId", "" + other)).path("total").asInt());
        assertEquals(1, detail("customers", Map.of("customerId", "" + sameDept)).path("total").asInt());
        assertEquals(1, overview(Map.of("deptId", "" + dept, "userId", "" + colleague)).path("summary").path("customers").asInt());
        jdbc.update("UPDATE sys_role SET data_scope='5' WHERE role_id=?", role);
        assertEquals(1, overview(Map.of()).path("summary").path("customers").asInt());
        assertEquals(0, detail("customers", Map.of("userId", "" + colleague)).path("total").asInt());
    }

    @Test void salesOrganizationDefinesMonitoredPeopleRegardlessOfViewingGrantsOrBusinessOwnership() throws Exception {
        long externalDept = 99785, externalUser = 99785, zeroUser = 99786, inactiveUser = 99787, nestedDept = 99788;
        jdbc.update("INSERT INTO sys_dept(dept_id,parent_id,dept_name) VALUES (?,0,'综合部测试'),(?,?,'销售二级组')", externalDept, nestedDept, dept);
        for (long id : List.of(externalUser, zeroUser, inactiveUser)) {
            jdbc.update("INSERT INTO sys_user(user_id,tenant_id,dept_id,user_name,nick_name,password,status,del_flag) VALUES (?,1,?,?,?,'x',?,'0')", id, id == externalUser ? externalDept : nestedDept, "salesmonitor" + id, "测试人员" + id, id == inactiveUser ? "1" : "0");
        }
        // A non-sales manager has the dashboard grant and owns real business, yet must not enter sales statistics.
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES (?,?)", externalUser, role);
        long externalCustomer = customer(externalUser, now.minusDays(5));
        followup(externalCustomer, externalUser, now.minusDays(1), null, "非销售部门跟进");
        opportunity(externalUser, externalCustomer, "需求确认", List.of(Map.of("state", "需求确认", "recordTime", now.minusDays(1).toString())));
        financeForCustomer(externalCustomer, "receipt", 999, "confirmed");
        long c = contract(externalCustomer, 999, 1, 1); version(c, 1, "{\"amount\":999}", now.minusDays(1).toLocalDate());
        jdbc.update("UPDATE sys_role SET data_scope='1' WHERE role_id=?", role);
        var data = overview(period());
        var ids = new HashSet<Long>(); for (var p : data.path("people")) ids.add(p.path("userId").asLong());
        assertTrue(ids.contains(zeroUser), "unassigned sales members with no module grant remain visible");
        assertTrue(ids.contains(colleague), "zero-workload sales members remain visible");
        assertFalse(ids.contains(externalUser)); assertFalse(ids.contains(inactiveUser));
        for (var u : data.path("options").path("users")) assertNotEquals(externalUser, u.path("id").asLong());
        for (var d : data.path("options").path("departments")) assertNotEquals(externalDept, d.path("id").asLong());
        for (String metric : SalesMonitorService.METRICS) {
            var q = new HashMap<>(period()); q.put("userId", "" + externalUser);
            assertEquals(0, detail(metric, q).path("total").asInt(), metric);
            assertEquals(0, overview(q).path("summary").path(metric).asInt(), metric);
        }
        // Viewing permission does not expand membership, even for an external manager with global data scope.
        AuthContext.set(new AuthContext.CurrentUser(externalUser, "salesmonitor" + externalUser));
        assertEquals(1, overview(period()).path("summary").path("customers").asInt());
        assertEquals(0, detail("customers", Map.of("customerId", "" + externalCustomer)).path("total").asInt());
        jdbc.update("UPDATE sys_role SET data_scope='5' WHERE role_id=?", role);
        assertEquals(0, overview(period()).path("people").size());
        assertEquals(0, overview(period()).path("options").path("users").size());
    }

    @Test void departmentRenameDoesNotLoseItsPeopleAndMovingOutRemovesAllTheirFacts() {
        jdbc.update("UPDATE sys_dept SET dept_name='改名后的拓展团队' WHERE dept_id=326");
        assertEquals(1, overview(period()).path("summary").path("customers").asInt());
        jdbc.update("UPDATE sys_user SET dept_id=325 WHERE user_id=?", employee);
        assertEquals(0, overview(period()).path("summary").path("customers").asInt());
        assertEquals(0, detail("customers", Map.of("customerId", "" + customer)).path("total").asInt());
    }

    @Test void effectiveFollowupsUseAssignmentBoundaryAndActualOperatorIgnoringFutureOrOrdinaryEdits() {
        followup(customer, employee, now.minusDays(4), null, "真实跟进");
        followup(customer, employee, now.plusDays(1), null, "未来记录");
        jdbc.update("UPDATE oa_merchant SET last_followup_time=?,update_time=? WHERE id=?", now, now, customer);
        JsonNode data = overview(period());
        assertEquals(1, data.path("summary").path("stagnant").asInt());
        assertEquals(1, data.path("summary").path("contacted").asInt());
        assertEquals(1, data.path("summary").path("followups").asInt());
        followup(customer, 1, now.minusHours(1), now.plusDays(1), "管理员代录");
        data = overview(period());
        assertEquals(0, data.path("summary").path("stagnant").asInt());
        assertEquals(0, data.path("summary").path("overdue").asInt());
        assertEquals(2, data.path("summary").path("followups").asInt());
        assertEquals(employee, detail("followups", period()).path("rows").get(0).path("userId").asLong());
        assertEquals("管理员代录", detail("followups", period()).path("rows").get(0).path("metricContent").asText());
        jdbc.update("UPDATE oa_merchant SET assigned_at=? WHERE id=?", now.minusMinutes(10), customer);
        data = overview(period());
        assertEquals(0, data.path("summary").path("followups").asInt());
        assertEquals(0, data.path("summary").path("overdue").asInt());
        assertEquals(0, data.path("summary").path("stagnant").asInt());
    }

    @Test void nextPlanAndSavedRulesDriveOverdueButNotFixedThreeDayStagnation() {
        followup(customer, employee, now.minusDays(8), now.plusDays(1), "安排计划");
        assertEquals(0, overview(period()).path("summary").path("overdue").asInt());
        assertEquals(1, overview(period()).path("summary").path("stagnant").asInt());
        jdbc.update("UPDATE oa_customer_followup SET next_followup_time=NULL WHERE merchant_id=?", customer);
        assertEquals(1, overview(period()).path("summary").path("overdue").asInt());
        jdbc.update("INSERT INTO sys_config(config_name,config_key,config_value) VALUES ('监控规则','crm.sales.rules.1','{\"firstFollowupDays\":3,\"followupIntervalDays\":10,\"stagnantOpportunityDays\":20}')");
        assertEquals(0, overview(period()).path("summary").path("overdue").asInt());
        assertEquals(1, detail("stagnant", Map.of("startDate", "2000-01-01", "endDate", "2000-01-31")).path("total").asInt());
    }

    @Test void threeDayThresholdUsesSeventyTwoHoursRatherThanCalendarDayChanges() {
        jdbc.update("UPDATE oa_merchant SET assigned_at=? WHERE id=?", now.minusHours(71), customer);
        assertEquals(0, overview(period()).path("summary").path("stagnant").asInt());
        jdbc.update("UPDATE oa_merchant SET assigned_at=? WHERE id=?", now.minusHours(72), customer);
        assertEquals(1, overview(period()).path("summary").path("stagnant").asInt());
        assertEquals(1, detail("stagnant", period()).path("total").asInt());
    }

    @Test void stageRankingsCountFirstHistoryPerStageAndEditingDoesNotResetStagnation() throws Exception {
        var history = List.of(Map.of("state", "需求确认", "recordTime", now.minusDays(16).toString(), "remark", "首次需求"), Map.of("state", "需求确认", "recordTime", now.minusDays(15).toString()), Map.of("state", "方案报价", "recordTime", now.plusDays(1).toString()));
        opportunity(employee, customer, "需求确认", history);
        var data = overview(period());
        assertEquals(1, data.path("summary").path("demand").asInt());
        assertEquals(0, data.path("summary").path("quotation").asInt());
        assertEquals(1, data.path("summary").path("stagnantOpportunities").asInt());
        JsonNode row = detail("demand", period()).path("rows").get(0);
        assertTrue(row.path("metricContent").asText().contains("首次需求"));
        assertEquals("历史未记录", row.path("operatorName").asText());
        assertEquals(0, detail("demand", Map.of("startDate", now.minusDays(15).toLocalDate().toString(), "endDate", now.toLocalDate().toString())).path("total").asInt());
        assertEquals(1, detail("stagnantOpportunities", period()).path("total").asInt());
        opportunity(employee, customer, "赢单", List.of());
        assertEquals(0, overview(period()).path("summary").path("won").asInt());
    }

    @Test void signedFactsUseFirstSignedSnapshotNotDraftOrLaterAmendmentAndMoneyDrilldownMatches() {
        long contract = contract(customer, 9999, 2, 1);
        version(contract, 1, "{\"amount\":1999}", now.minusDays(4).toLocalDate());
        version(contract, 2, "{\"amount\":5999}", now.minusDays(1).toLocalDate());
        contract(customer, 20000, 1, 0);
        var data = overview(period());
        assertEquals(1, data.path("summary").path("signedContracts").asInt());
        assertEquals(1, data.path("summary").path("pendingContracts").asInt());
        assertEquals(1999, data.path("summary").path("signedAmount").asInt());
        assertEquals(1999, detail("signedAmount", period()).path("rows").get(0).path("amount").asInt());
        assertEquals(0, detail("signedContracts", Map.of("startDate", now.minusDays(2).toLocalDate().toString(), "endDate", now.toLocalDate().toString())).path("total").asInt());
        long unknown = contract(customer, 20000, 2, 1); version(unknown, 1, "{}", now.minusDays(1).toLocalDate());
        data = overview(period());
        assertEquals(2, data.path("summary").path("signedContracts").asInt());
        assertEquals(1999, data.path("summary").path("signedAmount").asInt());
        assertTrue(detail("signedContracts", period()).path("rows").toString().contains("历史快照缺少金额"));
    }

    @Test void netReceiptsIncludeConfirmedRefundsAndReversalsAndKeepNegativeValues() {
        finance("receipt", 100, "confirmed"); finance("refund", 120, "confirmed"); finance("reversal", 10, "confirmed"); finance("receipt", 999, "draft");
        var data = overview(period());
        assertEquals(-30, data.path("summary").path("receivedAmount").asInt());
        var rows = detail("receivedAmount", period());
        assertEquals(3, rows.path("total").asInt());
        int sum = 0; for (var row : rows.path("rows")) sum += row.path("amount").asInt();
        assertEquals(-30, sum);
        assertEquals(1, data.path("summary").path("stagnant").asInt());
    }

    @Test void tenantIsolationEmptyResultsValidationAndPaging() {
        jdbc.update("INSERT INTO oa_merchant(tenant_id,name,owner_user_id,public_pool) VALUES (2,'其他企业客户',?,'0')", employee);
        assertEquals(1, overview(period()).path("summary").path("customers").asInt());
        customer(employee, now.minusDays(1)); customer(employee, now.minusDays(1));
        var page = detail("customers", Map.of("pageNum", "2", "pageSize", "2"));
        assertEquals(3, page.path("total").asInt()); assertEquals(1, page.path("rows").size());
        assertEquals(0, detail("customers", Map.of("userId", "999999")).path("total").asInt());
        assertThrows(ApiException.class, () -> monitor.details(Map.of("metric", "bad")));
        assertThrows(ApiException.class, () -> monitor.details(Map.of("metric", "customers", "pageSize", "1000")));
        assertThrows(ApiException.class, () -> monitor.overview(Map.of("startDate", "2000-01-01")));
        assertThrows(ApiException.class, () -> monitor.overview(Map.of("startDate", "bad")));
        assertThrows(ApiException.class, () -> monitor.overview(Map.of("deptId", "bad")));
    }

    long customer(long owner, LocalDateTime assigned) {
        jdbc.update("INSERT INTO oa_merchant(name,owner_user_id,public_pool,assigned_at,create_time) VALUES (?,?, '0',?,?)", "客户" + owner + UUID.randomUUID(), owner, assigned, assigned);
        return jdbc.queryForObject("SELECT MAX(id) FROM oa_merchant", Long.class);
    }
    void followup(long c, long actor, LocalDateTime at, LocalDateTime next, String content) {
        jdbc.update("INSERT INTO oa_customer_followup(merchant_id,followup_time,next_followup_time,content,owner_user_id) VALUES (?,?,?,?,?)", c, at, next, content, actor);
    }
    void opportunity(long owner, long c, String stage, List<Map<String,String>> history) throws Exception {
        String payload = mapper.writeValueAsString(Map.of("id", 888881, "ownerUserId", owner, "customerId", c, "customerName", "测试客户", "name", "测试商机", "stage", stage, "sealBusinessStages", history));
        jdbc.update("INSERT INTO biz_record(tenant_id,module,business_key,payload_json,create_time,update_time) VALUES (1,'/project/business/list',?,?,?,?)", UUID.randomUUID().toString(), payload, now.minusDays(20), now);
    }
    long contract(long c, int amount, int version, int effective) {
        jdbc.update("INSERT INTO oa_crm_contract(customer_id,code,name,service_type,amount,start_date,end_date,status,version_no,effective_version,terms_json) VALUES (?,?,'测试合同','recruit',?,?,?,'draft',?,?,'[]')", c, UUID.randomUUID().toString(), amount, now.toLocalDate(), now.plusMonths(1).toLocalDate(), version, effective);
        return jdbc.queryForObject("SELECT MAX(id) FROM oa_crm_contract", Long.class);
    }
    void version(long c, int version, String snapshot, LocalDate signed) {
        jdbc.update("INSERT INTO oa_crm_contract_version(contract_id,version_no,snapshot_json,status,submit_user_id,approval_user_id,signed_date) VALUES (?,?,?,'active',1,1,?)", c, version, snapshot, signed);
    }
    void finance(String type, int amount, String status) {
        financeForCustomer(customer, type, amount, status);
    }
    void financeForCustomer(long c, String type, int amount, String status) { jdbc.update("INSERT INTO oa_finance_record(record_type,customer_id,amount,business_date,status,create_token) VALUES (?,?,?,?,?,?)", type, c, amount, now.toLocalDate(), status, UUID.randomUUID().toString()); }
    Map<String,String> period() { return Map.of("startDate", now.minusDays(30).toLocalDate().toString(), "endDate", now.toLocalDate().toString()); }
    JsonNode overview(Map<String,String> q) { return mapper.valueToTree(monitor.overview(q)); }
    JsonNode detail(String metric, Map<String,String> query) { var q = new HashMap<>(query); q.put("metric", metric); return mapper.valueToTree(monitor.details(q)); }
}
