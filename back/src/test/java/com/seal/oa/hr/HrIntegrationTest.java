package com.seal.oa.hr;

import static com.seal.oa.hr.HrData.*;

import static org.junit.jupiter.api.Assertions.*;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.system.HrMemberService;
import com.seal.oa.system.ModuleActionService;
import com.seal.oa.workflow.WorkflowActionService;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@SpringBootTest(
        properties =
                "spring.datasource.url=jdbc:h2:mem:hr_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@Transactional
class HrIntegrationTest {
    @Autowired HrEmployeeService employees;
    @Autowired HrAttendanceService attendance;
    @Autowired HrPayrollService payroll;
    @Autowired HrMemberService members;
    @Autowired WorkflowActionService workflows;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.seal.oa.workflow.WorkflowActionRequestService requests;
    @Autowired HrReportService reports;
    @Autowired HrWorkflowHandler hrWorkflow;
    @Autowired HrController controller;
    @Autowired ModuleActionService moduleActions;
    @Autowired HrPayrollRuleService payrollRules;
    long user, dept;

    @Test
    void attendanceActionMigrationRestoresMissingActionsBeforeGrantingPermissions() {
        jdbc.update("DELETE FROM sys_role_module_action WHERE module_id=341004 AND action_code IN ('generate','edit')");
        jdbc.update("DELETE FROM sys_module_action WHERE module_id=341004 AND action_code IN ('generate','edit')");
        jdbc.update("DELETE FROM sys_permission_migration WHERE migration_key='hr_attendance_generate_edit_actions_v2'");

        moduleActions.bootstrap();
        moduleActions.bootstrap();

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_module_action WHERE module_id=341004 AND action_code='generate'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_module_action WHERE module_id=341004 AND action_code='edit'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_module_action WHERE role_id=19 AND module_id=341004 AND action_code='generate'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_module_action WHERE role_id=19 AND module_id=341004 AND action_code='edit'", Integer.class));
    }

    @BeforeEach
    void prepare() {
        AuthContext.set(new AuthContext.CurrentUser(1L, "13610316814"));
        dept =
                jdbc.queryForObject(
                        "SELECT dept_id FROM sys_dept WHERE status='0' AND del_flag='0' ORDER BY"
                                + " dept_id LIMIT 1",
                        Long.class);
        String phone = "HR" + System.nanoTime();
        user =
                members.invite(
                        Map.of(
                                "userName",
                                phone,
                                "phonenumber",
                                phone,
                                "nickName",
                                "人事闭环测试",
                                "deptId",
                                dept),
                        "test");
        employees.ensure(user);
        var e = employees.detail(user);
        e.putAll(
                Map.of(
                        "entryDate",
                        "2026-01-01",
                        "accountName",
                        "测试收款人",
                        "bankName",
                        "测试银行",
                        "bankAccount",
                        "TEST-ACCOUNT"));
        employees.save(user, e);
        saveRule("2026-01", rules());
    }

    @AfterEach
    void clear() {
        AuthContext.clear();
    }

    Map<String, Object> rules() {
        return Map.of(
                "hoursPerDay",
                8,
                "personalPaidRate",
                0,
                "sickPaidRate",
                0.5,
                "absencePaidRate",
                0,
                "overtimeMultiplier",
                1.5,
                "latePenalty",
                0,
                "earlyPenalty",
                0);
    }

    Map<String, Object> saveRule(String month, Map<String, Object> parameters) {
        int base = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0) FROM oa_hr_payroll_rule WHERE tenant_id=1", Integer.class);
        return payrollRules.save(Map.of("name", "公司规则", "effectiveMonth", month, "rules", parameters,
                "reason", "公司确认的测试规则", "baseVersion", base));
    }

    @Test
    void payrollRulesFollowEffectiveMonthAndLatestVersionWithoutOverwritingHistory() {
        var original = payrollRules.effective("2026-10");
        var nextRules = new LinkedHashMap<>(rules());
        nextRules.put("latePenalty", 30);
        var future = saveRule("2026-12", nextRules);
        assertEquals(original.get("id"), payrollRules.effective("2026-11").get("id"));
        assertEquals(future.get("id"), payrollRules.effective("2027-01").get("id"));
        nextRules.put("latePenalty", 40);
        var correction = saveRule("2026-12", nextRules);
        assertEquals(correction.get("id"), payrollRules.effective("2026-12").get("id"));
        var backdated = saveRule("2026-10", rules());
        assertEquals(backdated.get("id"), payrollRules.effective("2026-11").get("id"));
        assertEquals(correction.get("id"), payrollRules.effective("2026-12").get("id"));
        var saved = ((List<Map<String,Object>>) payrollRules.list("2026-12").get("versions")).stream()
                .filter(v -> id(v.get("id")) == id(future.get("id"))).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("30").compareTo(new BigDecimal(text(((Map<?,?>) saved.get("rules")).get("latePenalty")))));
        assertNull(payrollRules.effective("2025-12"));
        assertEquals(4, ((List<?>) payrollRules.list("2026-12").get("versions")).size());
    }

    @Test
    void newPayrollAutomaticallyUsesCompanyRulesAndRejectsMissingOrStaleSelection() {
        salary("2026-01-01", "2200");
        long source = confirmed();
        var selected = payrollRules.effective("2026-10");
        var updated = saveRule("2026-10", rules());
        assertThrows(ApiException.class, () -> payroll.generate(Map.of("period", "2026-10", "attendanceId", source, "ruleId", selected.get("id"))));
        var forged = new LinkedHashMap<>(rules());
        forged.put("sickPaidRate", 1);
        assertThrows(ApiException.class, () -> payroll.generate(Map.of("period", "2026-10", "attendanceId", source, "rules", forged)));
        jdbc.update("DELETE FROM oa_hr_payroll_rule");
        assertThrows(ApiException.class, () -> payroll.generate(Map.of("period", "2026-10", "attendanceId", source, "rules", rules())));
        updated = saveRule("2026-01", rules());
        var result = payroll.generate(Map.of("period", "2026-10", "attendanceId", source));
        var snapshot = (Map<String, Object>) result.get("rules");
        assertEquals(id(updated.get("id")), id(((Map<String, Object>) snapshot.get("_rule")).get("id")));
    }

    @Test
    void ruleChangesKeepPayrollSnapshotsUntilExplicitRecalculationAndApprovalStaysFrozen() {
        var wage = draft();
        long batch = id(wage.get("id"));
        var originalRules = wage.get("rules");
        var next = new LinkedHashMap<>(rules());
        next.put("personalPaidRate", 1);
        var version = saveRule("2026-10", next);
        assertEquals(originalRules, payroll.detail(batch, false).get("rules"));
        assertEquals(true, payroll.detail(batch, false).get("rulesChanged"));
        var unchanged = payroll.recalculate(batch, Map.of("revision", wage.get("revision"), "attendanceId", wage.get("attendanceId"), "reason", "仅重算考勤"));
        assertEquals(originalRules, unchanged.get("rules"));
        assertEquals(new BigDecimal("2100.00"), ((List<Map<String,Object>>) unchanged.get("rows")).get(0).get("baseAmount"));
        var changed = payroll.recalculate(batch, Map.of("revision", unchanged.get("revision"), "attendanceId", wage.get("attendanceId"), "reason", "明确采用新版规则", "ruleId", version.get("id")));
        assertEquals(new BigDecimal("2200.00"), ((List<Map<String,Object>>) changed.get("rows")).get(0).get("baseAmount"));
        assertEquals(false, changed.get("rulesChanged"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM oa_hr_history WHERE entity_type='payroll' AND entity_id=? AND action='重算前工资快照' AND detail_json LIKE '%rules%'", Integer.class, batch));
        var approved = approve(changed);
        saveRule("2026-10", rules());
        assertEquals(approved.get("rules"), payroll.detail(batch, false).get("rules"));
        assertThrows(ApiException.class, () -> payroll.recalculate(batch, Map.of("revision", approved.get("revision"), "attendanceId", wage.get("attendanceId"), "reason", "不能改已批准工资", "ruleId", version.get("id"))));
    }

    @Test
    void payrollRuleValidationAndOptimisticVersionPreventIncompleteOrLostUpdates() {
        var body = new LinkedHashMap<String,Object>(Map.of("name", "规则", "effectiveMonth", "2026-11", "rules", rules(), "reason", "修改依据", "baseVersion", 0));
        assertThrows(ApiException.class, () -> payrollRules.save(body));
        body.put("baseVersion", 1);
        for (String key : HrPayrollRuleService.FIELDS) {
            var missing = new LinkedHashMap<>(rules());
            missing.remove(key);
            body.put("rules", missing);
            assertThrows(ApiException.class, () -> payrollRules.save(body), key);
        }
        for (var invalid : List.of(Map.of("hoursPerDay", 0), Map.of("hoursPerDay", 25), Map.of("sickPaidRate", 1.1), Map.of("latePenalty", -1))) {
            var input = new LinkedHashMap<>(rules());
            input.putAll(invalid);
            body.put("rules", input);
            assertThrows(ApiException.class, () -> payrollRules.save(body));
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oa_hr_payroll_rule", Integer.class));
    }

    @Test
    void legacyPayrollCanKeepItsRulesAndCompanyRulesDoNotLeakBetweenTenants() {
        var wage = draft();
        long batch = id(wage.get("id"));
        jdbc.update("UPDATE oa_hr_payroll SET rules_json=? WHERE id=?",
                "{\"hoursPerDay\":8,\"personalPaidRate\":0,\"sickPaidRate\":0.5,\"absencePaidRate\":0,\"overtimeMultiplier\":1.5,\"latePenalty\":0,\"earlyPenalty\":0}", batch);
        var legacy = payroll.recalculate(batch, Map.of("revision", wage.get("revision"), "attendanceId", wage.get("attendanceId"), "reason", "沿用历史规则"));
        assertFalse(((Map<?,?>) legacy.get("rules")).containsKey("_rule"));
        assertEquals(new BigDecimal("2100.00"), ((List<Map<String,Object>>) legacy.get("rows")).get(0).get("baseAmount"));
        jdbc.update("INSERT INTO sys_tenant(tenant_id,tenant_name,status) VALUES(989,'独立公司','0')");
        jdbc.update("UPDATE sys_user SET tenant_id=989 WHERE user_id=1");
        assertNull(payrollRules.effective("2026-10"));
        assertTrue(((List<?>) payrollRules.list("2026-10").get("versions")).isEmpty());
        var rule = payrollRules.save(Map.of("name", "独立公司规则", "effectiveMonth", "2026-01", "rules", rules(), "reason", "独立公司核准", "baseVersion", 0));
        assertEquals(989L, id(rule.get("tenantId")));
        assertEquals(1L, id(rule.get("versionNo")));
        jdbc.update("UPDATE sys_user SET tenant_id=1 WHERE user_id=1");
        assertEquals(1, ((List<?>) payrollRules.list("2026-10").get("versions")).size());
    }

    @Test
    void generatingPermissionCannotChangeCompanyRulesAndMigrationPreservesLaterRevocation() {
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", user);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,2)", user);
        jdbc.update("DELETE FROM sys_role_module_action WHERE role_id=2 AND module_id=341005");
        jdbc.update("INSERT INTO sys_role_module(role_id,module_id) SELECT 2,341005 WHERE NOT EXISTS(SELECT 1 FROM sys_role_module WHERE role_id=2 AND module_id=341005)");
        jdbc.update("INSERT INTO sys_role_module_action(role_id,module_id,action_code) VALUES(2,341005,'generate')");
        AuthContext.set(new AuthContext.CurrentUser(user, "test"));
        assertNotNull(payrollRules.list("2026-10").get("applicableRule"));
        assertThrows(ApiException.class, () -> saveRule("2026-11", rules()));
        AuthContext.set(new AuthContext.CurrentUser(1L, "13610316814"));
        jdbc.update("DELETE FROM sys_role_module_action WHERE module_id=341005 AND action_code='rule_manage'");
        jdbc.update("DELETE FROM sys_module_action WHERE module_id=341005 AND action_code='rule_manage'");
        jdbc.update("DELETE FROM sys_permission_migration WHERE migration_key='hr_payroll_rule_actions_v1'");
        moduleActions.bootstrap();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_module_action WHERE role_id=19 AND module_id=341005 AND action_code='rule_manage'", Integer.class));
        jdbc.update("DELETE FROM sys_role_module_action WHERE role_id=19 AND module_id=341005 AND action_code='rule_manage'");
        moduleActions.bootstrap();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_module_action WHERE role_id=19 AND module_id=341005 AND action_code='rule_manage'", Integer.class));
    }

    Map<String, Object> monthly() {
        var r = new LinkedHashMap<String, Object>();
        r.put("employeeNo", "EMP" + user);
        for (String f : HrAttendanceService.MONTH_FIELDS) r.put(f, 0);
        r.put("requiredDays", 22);
        r.put("actualDays", 21);
        r.put("personalLeaveHours", 8);
        return r;
    }

    Map<String, Object> source(Map<String, Object> row) {
        return Map.of(
                "period",
                "2026-10",
                "kind",
                "monthly",
                "fileName",
                "钉钉导出.xlsx",
                "fileContent",
                Base64.getEncoder().encodeToString("test source".getBytes()),
                "rows",
                List.of(row));
    }

    void salary(String date, String amount) {
        var row =
                employees.salary(
                        user,
                        Map.of(
                                "effectiveDate",
                                date,
                                "payType",
                                "monthly",
                                "baseAmount",
                                amount,
                                "allowance",
                                220,
                                "reason",
                                "测试定薪"));
        workflows.execute(
                Map.of(
                        "moduleId",
                        EMPLOYEES,
                        "actionCode",
                        "salary_submit",
                        "payload",
                        Map.of("salaryId", row.get("id"), "revision", row.get("revision"))));
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> attendanceRow(Map<String, Object> batch, long uid) {
        return ((List<Map<String, Object>>) batch.get("rows")).stream().filter(r -> id(r.get("userId")) == uid).findFirst().orElseThrow();
    }

    @Test
    void normalAttendanceUsesMemberIdentityWeekdaysAndGeneratedSource() {
        var request = Map.<String, Object>of("period", "2026-10", "kind", "monthly");
        var batch = attendance.generate(request);
        var row = attendanceRow(batch, user);
        assertEquals("EMP" + user, row.get("employeeNo"));
        assertEquals(0, number(row.get("requiredDays"), "required").compareTo(new BigDecimal("22")));
        assertEquals(0, number(row.get("actualDays"), "actual").compareTo(new BigDecimal("22")));
        assertEquals(0, number(row.get("missingCount"), "missing").signum());
        assertEquals(batch.get("id"), attendance.generate(request).get("id"));
        assertTrue(text(attendance.file(id(batch.get("fileId"))).get("fileName")).endsWith(".json"));
        var daily = attendance.generate(Map.of("period", "2026-10", "kind", "daily"));
        var weekend = ((List<Map<String, Object>>) daily.get("rows")).stream()
                .filter(r -> id(r.get("userId")) == user && "2026-10-03".equals(r.get("workDate"))).findFirst().orElseThrow();
        assertEquals(0, number(weekend.get("scheduledHours"), "scheduled").signum());
        assertEquals("休息", weekend.get("result"));
        jdbc.update("UPDATE sys_user SET status='1' WHERE user_id=?", user);
        var changed = attendance.generate(request);
        assertFalse(((List<Map<String, Object>>) changed.get("rows")).stream().anyMatch(r -> id(r.get("userId")) == user));
        assertThrows(ApiException.class, () -> attendance.generate(Map.of("period", "2026-10", "workdays", List.of())));
        assertThrows(ApiException.class, () -> attendance.generate(Map.of("period", "2026-10", "kind", "leave")));
    }

    @Test
    void fixedSuperAdministratorIsNeverMaterializedOrListedAsAnEmployee() {
        jdbc.update("DELETE FROM oa_hr_employee WHERE user_id=1");

        var batch = attendance.generate(Map.of("period", "2026-10", "kind", "monthly"));

        assertTrue(((List<Map<String, Object>>) batch.get("rows")).stream().noneMatch(row -> id(row.get("userId")) == 1L));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM oa_hr_employee WHERE user_id=1", Integer.class));
        assertTrue(employees.list(Map.of()).stream().noneMatch(row -> id(row.get("userId")) == 1L));
    }

    @Test
    void normalAttendanceKeepsFullMonthDivisorAndClipsActualWorkToEmployment() {
        jdbc.update("UPDATE oa_hr_employee SET entry_date='2026-10-15' WHERE user_id=?", user);
        jdbc.update("DELETE FROM oa_hr_employment_period WHERE user_id=?", user);
        jdbc.update("INSERT INTO oa_hr_employment_period(user_id,entry_date) VALUES(?,'2026-10-15')", user);
        var batch = attendance.generate(Map.of("period", "2026-10", "kind", "monthly"));
        var row = attendanceRow(batch, user);
        assertEquals(0, number(row.get("requiredDays"), "required").compareTo(new BigDecimal("22")));
        assertEquals(0, number(row.get("actualDays"), "actual").compareTo(new BigDecimal("12")));
        var daily = attendance.generate(Map.of("period", "2026-10", "kind", "daily"));
        var rows = ((List<Map<String, Object>>) daily.get("rows")).stream().filter(r -> id(r.get("userId")) == user).toList();
        assertEquals(31, rows.size());
        assertEquals(0, rows.stream().map(r -> number(r.get("scheduledHours"), "scheduled")).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(new BigDecimal("176")));
        assertEquals(0, rows.stream().map(r -> number(r.get("actualHours"), "actual")).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(new BigDecimal("96")));
    }

    @Test
    void draftEditsAndPartialImportsPreserveOtherEmployeesAndRejectStaleWrites() {
        var batch = attendance.generate(Map.of("period", "2026-10", "kind", "monthly"));
        long batchId = id(batch.get("id"));
        var row = new LinkedHashMap<>(attendanceRow(batch, user));
        row.put("actualDays", 21);
        row.put("personalLeaveHours", 8);
        var edited = attendance.update(batchId, Map.of("revision", batch.get("revision"), "rows", List.of(row)));
        assertEquals(batchId, id(edited.get("id")));
        assertEquals(((List<?>) batch.get("rows")).size(), ((List<?>) edited.get("rows")).size());
        assertEquals(409, assertThrows(ApiException.class, () -> attendance.update(batchId, Map.of("revision", batch.get("revision"), "rows", List.of(row)))).code());
        var input = new LinkedHashMap<>(source(monthly()));
        input.put("baseBatchId", batchId);
        input.put("revision", edited.get("revision"));
        var imported = attendance.save(input);
        assertEquals(batchId, id(imported.get("id")));
        assertEquals(((List<?>) batch.get("rows")).size(), ((List<?>) imported.get("rows")).size());
        assertEquals(0, number(attendanceRow(imported, user).get("actualDays"), "actual").compareTo(new BigDecimal("21")));
        long other = ((List<Map<String, Object>>) batch.get("rows")).stream().map(r -> id(r.get("userId"))).filter(uid -> uid != user).findFirst().orElseThrow();
        assertEquals(0, number(attendanceRow(imported, other).get("actualDays"), "actual").compareTo(number(attendanceRow(batch, other).get("actualDays"), "original")));
    }

    @Test
    void confirmedAttendanceChangesCreateDraftAndInvalidatePayrollOnlyAfterConfirmation() {
        var wage = draft();
        long original = id(wage.get("attendanceId"));
        var before = attendance.detail(original, false);
        var row = new LinkedHashMap<>(attendanceRow(before, user));
        row.put("actualDays", 20);
        row.put("personalLeaveHours", 16);
        var edited = attendance.update(original, Map.of("revision", before.get("revision"), "rows", List.of(row)));
        assertNotEquals(original, id(edited.get("id")));
        assertEquals("draft", edited.get("status"));
        assertEquals("confirmed", attendance.detail(original, false).get("status"));
        assertEquals(false, payroll.detail(id(wage.get("id")), false).get("attendanceChanged"));
        attendance.confirm(id(edited.get("id")), Map.of("revision", edited.get("revision")));
        assertEquals("superseded", attendance.detail(original, false).get("status"));
        assertEquals(true, payroll.detail(id(wage.get("id")), false).get("attendanceChanged"));
        assertEquals(0, number(attendanceRow(attendance.detail(original, false), user).get("actualDays"), "actual").compareTo(new BigDecimal("21")));
        assertEquals(edited.get("rows"), reports.attendance(id(edited.get("id"))).get("rows"));
        assertThrows(ApiException.class, () -> attendance.update(original, Map.of("revision", attendance.detail(original, false).get("revision"), "rows", List.of(row))));
    }

    @Test
    void attendanceGenerationEditAndExportRequireTheirOwnPermissions() {
        var batch = attendance.save(source(monthly()));
        AuthContext.set(new AuthContext.CurrentUser(user, "test-member"));
        assertEquals(403, assertThrows(ApiException.class, () -> attendance.generate(Map.of("period", "2026-10"))).code());
        assertEquals(403, assertThrows(ApiException.class, () -> attendance.update(id(batch.get("id")), Map.of())).code());
        assertEquals(403, assertThrows(ApiException.class, () -> reports.attendance(id(batch.get("id")))).code());
    }

    @Test
    @SuppressWarnings("unchecked")
    void scopedOperatorsGenerateOnlyTheirEmployeesAndCannotOverwriteMixedBatches() {
        var mixed = attendance.generate(Map.of("period", "2026-10", "kind", "monthly"));
        jdbc.update("INSERT INTO sys_role(role_id,role_name,role_key,data_scope) VALUES(99331,'考勤测试','attendance_scope_test','5')");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,99331)", user);
        jdbc.update("INSERT INTO sys_role_module(role_id,module_id) VALUES(99331,341004)");
        for (String code : List.of("generate", "edit", "import", "export"))
            jdbc.update("INSERT INTO sys_role_module_action(role_id,module_id,action_code) VALUES(99331,341004,?)", code);
        AuthContext.set(new AuthContext.CurrentUser(user, "scope-member"));
        var mine = attendance.generate(Map.of("period", "2026-10", "kind", "monthly"));
        assertEquals(1, ((List<?>) mine.get("rows")).size());
        assertEquals(user, id(attendanceRow(mine, user).get("userId")));
        assertEquals(1, ((List<?>) reports.attendance(id(mixed.get("id"))).get("rows")).size());
        assertEquals(403, assertThrows(ApiException.class, () -> attendance.update(id(mixed.get("id")), Map.of("revision", mixed.get("revision"), "rows", List.of(attendanceRow(mixed, user))))).code());
        assertEquals(id(mixed.get("revision")), jdbc.queryForObject("SELECT revision FROM oa_hr_attendance_batch WHERE id=?", Long.class, mixed.get("id")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dailyImportOverwritesOnlyMatchingEmployeeAndDate() {
        var batch = attendance.generate(Map.of("period", "2026-10", "kind", "daily"));
        var rows = (List<Map<String, Object>>) batch.get("rows");
        var row = new LinkedHashMap<>(rows.stream().filter(r -> id(r.get("userId")) == user && "2026-10-01".equals(r.get("workDate"))).findFirst().orElseThrow());
        row.put("actualHours", 4);
        row.put("personalLeaveHours", 4);
        var input = new LinkedHashMap<>(source(row));
        input.put("kind", "daily");
        input.put("baseBatchId", batch.get("id"));
        input.put("revision", batch.get("revision"));
        var imported = attendance.save(input);
        var changed = (List<Map<String, Object>>) imported.get("rows");
        assertEquals(rows.size(), changed.size());
        var october1 = changed.stream().filter(r -> id(r.get("userId")) == user && "2026-10-01".equals(r.get("workDate"))).findFirst().orElseThrow();
        var october2 = changed.stream().filter(r -> id(r.get("userId")) == user && "2026-10-02".equals(r.get("workDate"))).findFirst().orElseThrow();
        assertEquals(0, number(october1.get("actualHours"), "actual").compareTo(new BigDecimal("4")));
        assertEquals(0, number(october2.get("actualHours"), "actual").compareTo(new BigDecimal("8")));
    }

    long confirmed() {
        var a = attendance.save(source(monthly()));
        long id = id(a.get("id"));
        attendance.confirm(id, Map.of("revision", a.get("revision")));
        return id;
    }

    @Test
    void legacyAttendanceWithSystemAdministratorDoesNotBlockEmployeePayroll() {
        salary("2026-01-01", "2200");
        var batch = attendance.save(source(monthly()));
        long source = id(batch.get("id"));
        // Simulate a batch created before administrator identities were excluded.
        jdbc.update("INSERT INTO oa_hr_attendance_row(batch_id,user_id,data_json) VALUES(?,1,?)", source,
                "{\"userId\":1,\"employeeNo\":\"EMP1\",\"employeeName\":\"欧志锋\"}");
        attendance.confirm(source, Map.of("revision", batch.get("revision")));
        assertEquals(1, attendance.visibleRows(source, false).size());
        assertTrue(payroll.attendanceOptions("2026-10").stream().anyMatch(row -> id(row.get("id")) == source));
        var wage = payroll.generate(Map.of("period", "2026-10", "attendanceId", source, "rules", rules()));
        assertEquals(1, ((List<?>) wage.get("rows")).size());
        assertEquals(user, id(((Map<?, ?>) ((List<?>) wage.get("rows")).get(0)).get("userId")));
        assertThrows(ApiException.class, () -> employees.detail(1L));
    }

    Map<String, Object> draft() {
        salary("2026-01-01", "2200");
        return payroll.generate(
                Map.of("period", "2026-10", "attendanceId", confirmed(), "rules", rules()));
    }

    Map<String, Object> enterZeroTax(Map<String, Object> b) {
        long batch = id(b.get("id"));
        for (var row : (List<Map<String, Object>>) b.get("rows"))
            if (optionalId(row.get("taxEntered")) != 1)
                b = payroll.saveTax(batch, id(row.get("id")), Map.of("revision", b.get("revision"), "tax", 0, "reason", "测试明确核对无需扣税"));
        return b;
    }

    @Test
    void workplacesDefaultToGuangzhouAndValidateProvinceCityWhileBlankImportPreservesValues() {
        var e = employees.detail(user);
        assertEquals("广东省", e.get("workProvince"));
        assertEquals("广州市", e.get("workCity"));
        e.put("workProvince", "浙江省");
        e.put("workCity", "广州市");
        assertThrows(ApiException.class, () -> employees.save(user, e));
        e.put("workCity", "杭州市");
        var saved = employees.save(user, e);
        employees.importProfiles(Map.of("rows", List.of(Map.of("employeeNo", saved.get("employeeNo"), "revision", saved.get("revision"), "workProvince", "", "workCity", ""))));
        assertEquals("杭州市", employees.detail(user).get("workCity"));
        assertTrue(employees.list(Map.of()).stream().anyMatch(r -> id(r.get("userId")) == user && "浙江省".equals(r.get("workProvince"))));
        assertEquals(34, ((List<?>)controller.workLocations().get("data")).size());
        assertThrows(ApiException.class, () -> employees.detail(1));
    }

    @Test
    void defaultZeroTaxCanBeVerifiedAndSubmittedWithoutIndividualTaxEntry() {
        var b = draft(); long batch = id(b.get("id"));
        assertEquals(0L, optionalId(((List<Map<String,Object>>)b.get("rows")).get(0).get("taxEntered")));
        assertEquals(new BigDecimal("0.00"), ((List<Map<String,Object>>)b.get("rows")).get(0).get("tax"));
        payroll.verify(batch, Map.of("revision", b.get("revision"), "reason", "本期项目已核对"));
        var checked = payroll.detail(batch, false);
        var ctx = new com.seal.oa.workflow.WorkflowActionContext(0,PAYROLL,"submit","hr.payroll.submit",Map.of(),Map.of("payrollId",batch,"revision",checked.get("revision")),1L,"admin");
        assertDoesNotThrow(() -> payroll.validateWorkflow(ctx));
        assertEquals(new BigDecimal("2310.00"), checked.get("netAmount"));
    }

    @Test
    void manualTaxUpdatesNetTotalAndAuditAndRejectsStaleOrInvalidAmounts() {
        var b = draft(); long batch=id(b.get("id"));
        long item=id(((List<Map<String,Object>>)b.get("rows")).get(0).get("id"));
        for (Object tax : List.of(-1, "0.001", 9999))
            assertThrows(ApiException.class, () -> payroll.saveTax(batch,item,Map.of("revision",b.get("revision"),"tax",tax,"reason","非法金额测试")));
        assertThrows(ApiException.class, () -> payroll.saveTax(batch,item,Map.of("revision",b.get("revision"),"tax",0,"reason","长".repeat(1001))));
        var saved=payroll.saveTax(batch,item,Map.of("revision",b.get("revision"),"tax","123.45","reason","按本期实际申报"));
        assertEquals(new BigDecimal("2186.55"),saved.get("netAmount"));
        assertEquals(new BigDecimal("2186.55"),payroll.list(Map.of(),false).stream().filter(r->id(r.get("id"))==batch).findFirst().orElseThrow().get("netAmount"));
        var row=((List<Map<String,Object>>)saved.get("rows")).get(0);
        assertEquals(1L,id(row.get("taxOperatorId")));
        assertNotNull(row.get("taxUpdateTime"));
        assertEquals("按本期实际申报",row.get("taxReason"));
        var history=((List<Map<String,Object>>)saved.get("history")).get(0);
        assertEquals("填写或修改个税",history.get("action"));
        assertEquals("按本期实际申报",((Map<?,?>)history.get("detail")).get("reason"));
        assertThrows(ApiException.class, ()->payroll.saveTax(batch,item,Map.of("revision",b.get("revision"),"tax",0,"reason","过期版本")));
        assertThrows(ApiException.class, ()->payroll.saveTax(batch,item+999999,Map.of("revision",saved.get("revision"),"tax",0,"reason","非本批次明细")));
    }

    @Test
    void taxReasonIsOptionalForZeroAndPositiveAmounts() {
        var b = draft(); long batch = id(b.get("id"));
        long item = id(((List<Map<String,Object>>) b.get("rows")).get(0).get("id"));
        var zero = payroll.saveTax(batch, item, Map.of("revision", b.get("revision"), "tax", 0));
        var zeroRow = ((List<Map<String,Object>>) zero.get("rows")).get(0);
        assertEquals("", zeroRow.get("taxReason"));
        assertEquals(1L, optionalId(zeroRow.get("taxEntered")));
        var positive = payroll.saveTax(batch, item, Map.of("revision", zero.get("revision"), "tax", 50, "reason", "  "));
        var row = ((List<Map<String,Object>>) positive.get("rows")).get(0);
        assertEquals("", row.get("taxReason"));
        assertEquals(new BigDecimal("2260.00"), positive.get("netAmount"));
        var history = ((List<Map<String,Object>>) positive.get("history")).get(0);
        assertEquals("", ((Map<?,?>) history.get("detail")).get("reason"));
    }

    @Test
    void taxChangesInvalidateVerificationAndApprovalFreezesTaxWhileRejectionAllowsEditing() {
        var b=enterZeroTax(draft());long batch=id(b.get("id"));
        long item=id(((List<Map<String,Object>>)b.get("rows")).get(0).get("id"));
        payroll.verify(batch,Map.of("revision",b.get("revision"),"reason","确认"));
        var checked=payroll.detail(batch,false);
        var changed=payroll.saveTax(batch,item,Map.of("revision",checked.get("revision"),"tax",10,"reason","修正税额"));
        assertEquals(0L,optionalId(((List<Map<String,Object>>)changed.get("rows")).get(0).get("verified")));
        for(String state:List.of("approving","approved")) {
            jdbc.update("UPDATE oa_hr_payroll SET status=? WHERE id=?",state,batch);
            assertThrows(ApiException.class,()->payroll.saveTax(batch,item,Map.of("revision",changed.get("revision"),"tax",0,"reason","已锁定")));
        }
        jdbc.update("UPDATE oa_hr_payroll SET status='rejected' WHERE id=?",batch);
        assertEquals(new BigDecimal("2310.00"),payroll.saveTax(batch,item,Map.of("revision",changed.get("revision"),"tax",0,"reason","退回修正")).get("netAmount"));
    }

    @Test
    void recalculationRequiresDiscardConfirmationEvenForExplicitZeroAndResetsTaxState() {
        var b=enterZeroTax(draft());long batch=id(b.get("id"));
        var body=new LinkedHashMap<String,Object>(Map.of("revision",b.get("revision"),"attendanceId",b.get("attendanceId"),"reason","重新核算测试"));
        assertThrows(ApiException.class,()->payroll.recalculate(batch,body));
        body.put("discardAdjustments",true);
        var fresh=payroll.recalculate(batch,body);
        assertEquals(0L,optionalId(((List<Map<String,Object>>)fresh.get("rows")).get(0).get("taxEntered")));
        assertTrue(((List<Map<String,Object>>)fresh.get("history")).stream().anyMatch(h->"重算前工资快照".equals(h.get("action"))));
    }

    @Test
    void importBlankTaxDefaultsToZeroAndKeepsVerificationAndAudit() {
        var b=draft();long batch=id(b.get("id"));
        var row=new LinkedHashMap<>(((List<Map<String,Object>>)b.get("rows")).get(0));
        row.putAll(Map.of("employeeNo","EMP"+user,"batchId",batch,"period",b.get("period"),"revision",b.get("revision"),"reason","按申报核对"));
        row.put("tax", "");
        var body=Map.<String,Object>of("revision",b.get("revision"),"rows",List.of(row));
        var preview = payroll.previewImport(batch,body);
        assertEquals(new BigDecimal("0.00"),((List<Map<String,Object>>)preview.get("rows")).get(0).get("tax"));
        payroll.importRows(batch,body);
        var saved=((List<Map<String,Object>>)payroll.detail(batch,false).get("rows")).get(0);
        assertEquals(1L,optionalId(saved.get("taxEntered")));
        assertEquals(1L,optionalId(saved.get("verified")));
        assertEquals("按申报核对",saved.get("taxReason"));
    }

    @Test
    void clearingManualTaxUsesZeroAndUpdatesNetWithoutARequiredFieldError() {
        var b = draft(); long batch = id(b.get("id"));
        long item = id(((List<Map<String,Object>>)b.get("rows")).get(0).get("id"));
        var entered = payroll.saveTax(batch,item,Map.of("revision",b.get("revision"),"tax",100));
        var cleared = payroll.saveTax(batch,item,Map.of("revision",entered.get("revision"),"tax",""));
        assertEquals(new BigDecimal("2310.00"),cleared.get("netAmount"));
        var saved = ((List<Map<String,Object>>)cleared.get("rows")).get(0);
        assertEquals(new BigDecimal("0.00"),saved.get("tax"));
        assertEquals(0L,optionalId(saved.get("verified")));
    }

    Map<String, Object> approve(Map<String, Object> b) {
        long id = id(b.get("id"));
        b = enterZeroTax(b);
        payroll.verify(id, Map.of("revision", b.get("revision"), "reason", "已逐项核对个税和个人扣款，测试项目为零"));
        b = payroll.detail(id, false);
        workflows.execute(
                Map.of(
                        "moduleId",
                        PAYROLL,
                        "actionCode",
                        "submit",
                        "payload",
                        Map.of("payrollId", id, "revision", b.get("revision"))));
        return payroll.detail(id, false);
    }

    @Test
    void salaryVersionAndAttendanceProduceReviewedPayroll() {
        var b = draft();
        var row = ((List<Map<String, Object>>) b.get("rows")).get(0);
        assertEquals(new BigDecimal("2100.00"), row.get("baseAmount"));
        assertEquals(new BigDecimal("210.00"), row.get("allowance"));
        assertEquals(new BigDecimal("2310.00"), row.get("netAmount"));
        assertThrows(
                ApiException.class,
                () ->
                        workflows.execute(
                                Map.of(
                                        "moduleId",
                                        PAYROLL,
                                        "actionCode",
                                        "submit",
                                        "payload",
                                        Map.of(
                                                "payrollId",
                                                b.get("id"),
                                                "revision",
                                                b.get("revision")))));
        assertEquals("approved", approve(b).get("status"));
    }

    @Test
    void salaryReportUsesEmployeeFilters() {
        salary("2026-01-01", "2200");
        var rows = reports.salaries(Map.of("keyword", "EMP" + user, "status", "active"));
        assertFalse(rows.isEmpty());
        assertTrue(rows.stream().allMatch(row -> ("EMP" + user).equals(row.get("employeeNo"))));
    }

    @Test
    void salaryImportSkipsUnchangedRowsAndAddsNewVersions() {
        salary("2026-01-01", "2200");
        var unchanged =
                Map.<String, Object>of(
                        "employeeNo", "EMP" + user,
                        "employeeName", "人事闭环测试",
                        "effectiveDate", "2026-01-01",
                        "payType", "monthly",
                        "baseAmount", 2200,
                        "allowance", 220,
                        "reason", "测试定薪");
        var newVersion = new LinkedHashMap<>(unchanged);
        newVersion.put("effectiveDate", "2026-02-01");
        newVersion.put("baseAmount", 3300);
        newVersion.put("reason", "测试调薪");

        employees.importSalaries(Map.of("rows", List.of(unchanged, newVersion)));

        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM oa_hr_salary WHERE user_id=?", Integer.class, user));
        assertEquals(
                new BigDecimal("3300.00"),
                jdbc.queryForObject(
                        "SELECT base_amount FROM oa_hr_salary WHERE user_id=? AND effective_date='2026-02-01'",
                        BigDecimal.class,
                        user));
    }

    @Test
    void duplicateSourceIsIdempotentAndMissingValuesAreRejected() {
        var input = source(monthly());
        var one = attendance.save(input);
        assertEquals(one.get("id"), attendance.save(input).get("id"));
        var broken = monthly();
        broken.remove("actualDays");
        assertEquals(false, attendance.preview(source(broken)).get("valid"));
        assertThrows(ApiException.class, () -> attendance.save(source(broken)));
    }

    @Test
    void payrollRoundTripRejectsOldVersionsAndKeepsOtherRows() {
        var b = draft();
        long batch = id(b.get("id"));
        var item = new LinkedHashMap<>(((List<Map<String, Object>>) b.get("rows")).get(0));
        item.putAll(
                Map.of(
                        "employeeNo",
                        "EMP" + user,
                        "batchId",
                        batch,
                        "period",
                        "2026-10",
                        "revision",
                        b.get("revision"),
                        "bonus",
                        100,
                        "reason",
                        "本期奖金核对"));
        var body = Map.<String, Object>of("revision", b.get("revision"), "rows", List.of(item));
        assertEquals(1, ((List<?>) payroll.previewImport(batch, body).get("rows")).size());
        payroll.importRows(batch, body);
        assertThrows(ApiException.class, () -> payroll.importRows(batch, body));
        assertEquals(
                new BigDecimal("2410.00"),
                ((List<Map<String, Object>>) payroll.detail(batch, false).get("rows"))
                        .get(0)
                        .get("netAmount"));
    }

    @Test
    void changedAttendanceBlocksSubmissionWithoutChangingFrozenAmounts() {
        var b = draft();
        var changed = monthly();
        changed.put("actualDays", 22);
        changed.put("personalLeaveHours", 0);
        var a = attendance.save(source(changed));
        attendance.confirm(id(a.get("id")), Map.of("revision", a.get("revision")));
        long batch = id(b.get("id"));
        assertEquals(true, payroll.detail(batch, false).get("attendanceChanged"));
        assertThrows(ApiException.class, () -> approve(b));
        assertEquals(
                new BigDecimal("2310.00"),
                ((List<Map<String, Object>>) payroll.detail(batch, false).get("rows"))
                        .get(0)
                        .get("netAmount"));
    }

    @Test
    void paymentNeedsEvidenceIsIdempotentAndCannotExceedRemaining() {
        var b = approve(draft());
        long batch = id(b.get("id")),
                item = id(((List<Map<String, Object>>) b.get("rows")).get(0).get("id"));
        var pay =
                new LinkedHashMap<String, Object>(
                        Map.of(
                                "requestToken",
                                UUID.randomUUID().toString(),
                                "amount",
                                100,
                                "status",
                                "paid",
                                "paymentDate",
                                "2026-11-01",
                                "referenceNo",
                                UUID.randomUUID().toString()));
        assertThrows(ApiException.class, () -> payroll.pay(item, pay));
        pay.put("fileName", "银行凭证.pdf");
        pay.put("fileContent", Base64.getEncoder().encodeToString("proof".getBytes()));
        var first = payroll.pay(item, pay);
        assertEquals(first.get("id"), payroll.pay(item, pay).get("id"));
        assertEquals(new BigDecimal("100.00"), payroll.paid(item));
        assertThrows(
                ApiException.class,
                () -> payroll.reopen(batch, Map.of("revision", b.get("revision"), "reason", "修改")));
        pay.put("requestToken", UUID.randomUUID().toString());
        pay.put("amount", 9999);
        assertThrows(ApiException.class, () -> payroll.pay(item, pay));
    }

    @Test
    void batchPaymentValidatesTheWholeSetThenRecordsEachEmployeeTransaction() {
        var approved = approve(draft());
        long batch = id(approved.get("id"));
        var row = ((List<Map<String,Object>>)approved.get("rows")).get(0);
        long item = id(row.get("id"));
        String reference = "BATCH-" + UUID.randomUUID();
        var paymentRow = Map.<String,Object>of("itemId",item,"amount",row.get("netAmount"),"referenceNo",reference,"requestToken",UUID.randomUUID().toString());
        var body = new LinkedHashMap<String,Object>();
        body.put("revision",approved.get("revision"));body.put("paymentDate","2026-11-01");body.put("status","paid");
        body.put("fileName","批量付款凭证.pdf");body.put("fileContent",Base64.getEncoder().encodeToString("evidence".getBytes()));
        body.put("rows",List.of(paymentRow));
        var invalid = new LinkedHashMap<String,Object>(body);
        invalid.put("rows",List.of(Map.of("itemId",item,"amount",999999,"referenceNo",reference,"requestToken",UUID.randomUUID().toString())));
        assertThrows(ApiException.class,()->payroll.payBatch(batch,invalid));
        assertEquals(BigDecimal.ZERO,payroll.paid(item));
        var result=payroll.payBatch(batch,body);
        assertEquals(1,result.get("count"));
        assertEquals(row.get("netAmount"),payroll.paid(item));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM oa_hr_payment WHERE item_id=? AND reference_no=?",Integer.class,item,reference));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM oa_hr_attachment WHERE payroll_id=? AND file_name='批量付款凭证.pdf'",Integer.class,batch));
    }

    @Test
    void publishingAlreadyPublishedPayslipsIsIdempotentAndDoesNotDuplicateHistoryOrTodos() {
        var approved=approve(draft());long batch=id(approved.get("id"));
        payroll.publish(batch,Map.of("revision",approved.get("revision")));
        var published=payroll.detail(batch,false);
        assertEquals(true,published.get("published"));
        long history=jdbc.queryForObject("SELECT COUNT(*) FROM oa_hr_history WHERE entity_type='payroll' AND entity_id=? AND action='发布工资条'",Long.class,batch);
        long todos=jdbc.queryForObject("SELECT COUNT(*) FROM oa_todo WHERE business_type='hrPayslip' AND business_id IN (SELECT id FROM oa_hr_payroll_item WHERE payroll_id=?)",Long.class,batch);
        payroll.publish(batch,Map.of("revision",-1));
        assertEquals(history,jdbc.queryForObject("SELECT COUNT(*) FROM oa_hr_history WHERE entity_type='payroll' AND entity_id=? AND action='发布工资条'",Long.class,batch));
        assertEquals(todos,jdbc.queryForObject("SELECT COUNT(*) FROM oa_todo WHERE business_type='hrPayslip' AND business_id IN (SELECT id FROM oa_hr_payroll_item WHERE payroll_id=?)",Long.class,batch));
    }

    @Test
    void employeeArchiveKeywordSearchMatchesPhoneNameAndEmployeeNumberCaseInsensitively() {
        var employee=employees.detail(user);
        var updated=new LinkedHashMap<String,Object>(employee);updated.put("employeeNo","hr-search-"+user);
        employees.save(user,updated);
        jdbc.update("UPDATE sys_user SET phonenumber=? WHERE user_id=?","13900001234",user);
        for(String keyword:List.of("hr-search-"+user,"1390000",text(employees.detail(user).get("nickName")).toLowerCase(Locale.ROOT)))
            assertTrue(employees.list(Map.of("keyword",keyword)).stream().anyMatch(r->id(r.get("userId"))==user),keyword);
    }

    @Test
    void salaryAndBankInformationDoNotLeakToOrdinaryMember() {
        salary("2026-01-01", "2200");
        AuthContext.set(new AuthContext.CurrentUser(user, "test"));
        assertThrows(ApiException.class, () -> employees.detail(user));
        assertThrows(ApiException.class, () -> payroll.list(Map.of(), false));
    }

    @Test
    void departureDisablesAccountAndPreservesPayrollAndHistory() {
        var b = draft();
        var profile = employees.detail(user);
        var event =
                employees.change(
                        user,
                        Map.of(
                                "eventType",
                                "dismiss",
                                "effectiveDate",
                                "2026-11-01",
                                "reason",
                                "办理离职",
                                "revision",
                                profile.get("revision")));
        workflows.execute(
                Map.of(
                        "moduleId",
                        CHANGES,
                        "actionCode",
                        "submit",
                        "payload",
                        Map.of("eventId", event.get("id"), "revision", event.get("revision"))));
        assertEquals(
                "1",
                jdbc.queryForObject(
                        "SELECT status FROM sys_user WHERE user_id=?", String.class, user));
        assertEquals("departed", employees.detail(user).get("employmentStatus"));
        assertEquals(1, ((List<?>) payroll.detail(id(b.get("id")), false).get("rows")).size());
        assertEquals(1, employees.events(user, false).size());
    }

    @Test
    void futureSalaryDoesNotChangeOldMonth() {
        salary("2026-01-01", "2200");
        salary("2026-11-01", "4400");
        var b =
                payroll.generate(
                        Map.of("period", "2026-10", "attendanceId", confirmed(), "rules", rules()));
        assertEquals(
                new BigDecimal("2100.00"),
                ((List<Map<String, Object>>) b.get("rows")).get(0).get("baseAmount"));
    }

    @Test
    void midMonthEmploymentStillRequiresDailyEvidence() {
        var profile = employees.detail(user);
        profile.put("entryDate", "2026-10-16");
        employees.save(user, profile);
        salary("2026-01-01", "2200");
        assertThrows(
                ApiException.class,
                () ->
                        payroll.generate(
                                Map.of(
                                        "period",
                                        "2026-10",
                                        "attendanceId",
                                        confirmed(),
                                        "rules",
                                        rules())));
    }

    @Test
    void salaryRejectionCanBeEditedAndResubmitted() {
        var standard =
                employees.salary(
                        user,
                        Map.of(
                                "effectiveDate",
                                "2026-01-01",
                                "payType",
                                "monthly",
                                "baseAmount",
                                2200,
                                "allowance",
                                220,
                                "reason",
                                "测试"));
        long request =
                requests.create(
                        EMPLOYEES,
                        "salary_submit",
                        "hr.salary.submit",
                        Map.of(),
                        "测试审批",
                        Map.of(
                                "salaryId",
                                standard.get("id"),
                                "revision",
                                standard.get("revision")),
                        1L,
                        "13610316814");
        requests.updateWorkflowState("salary-test", request, "审批未通过", "task", "退回");
        var rejected =
                jdbc.queryForMap(
                        "SELECT id,revision,status FROM oa_hr_salary WHERE id=?",
                        standard.get("id"));
        assertEquals("rejected", rejected.get("status"));
        var edited =
                employees.salary(
                        user,
                        Map.of(
                                "id",
                                standard.get("id"),
                                "revision",
                                rejected.get("revision"),
                                "effectiveDate",
                                "2026-01-01",
                                "payType",
                                "monthly",
                                "baseAmount",
                                2300,
                                "allowance",
                                220,
                                "reason",
                                "补充定薪依据"));
        workflows.execute(
                Map.of(
                        "moduleId",
                        EMPLOYEES,
                        "actionCode",
                        "salary_submit",
                        "payload",
                        Map.of("salaryId", edited.get("id"), "revision", edited.get("revision"))));
        assertEquals(
                new BigDecimal("2300.00"),
                jdbc.queryForObject(
                        "SELECT base_amount FROM oa_hr_salary WHERE id=?",
                        BigDecimal.class,
                        edited.get("id")));
        assertThrows(
                ApiException.class,
                () ->
                        employees.salary(
                                user,
                                Map.of(
                                        "id",
                                        edited.get("id"),
                                        "revision",
                                        edited.get("revision"),
                                        "effectiveDate",
                                        "2026-01-01",
                                        "payType",
                                        "monthly",
                                        "baseAmount",
                                        2400,
                                        "allowance",
                                        220,
                                        "reason",
                                        "不能覆盖已生效标准")));
    }

    @Test
    void payrollApprovalAndRejectionUseExistingWorkflowCallbacks() {
        var b = enterZeroTax(draft());
        long batch = id(b.get("id"));
        payroll.verify(batch, Map.of("revision", b.get("revision"), "reason", "测试核对"));
        b = payroll.detail(batch, false);
        long request =
                requests.create(
                        PAYROLL,
                        "submit",
                        "hr.payroll.submit",
                        Map.of(),
                        "工资审批",
                        Map.of("payrollId", batch, "revision", b.get("revision")),
                        1L,
                        "13610316814");
        assertEquals("approving", payroll.detail(batch, false).get("status"));
        var context =
                new com.seal.oa.workflow.WorkflowActionContext(
                        request,
                        PAYROLL,
                        "submit",
                        "hr.payroll.submit",
                        Map.of(),
                        Map.of("payrollId", batch),
                        null,
                        "13610316814");
        assertThrows(
                ApiException.class,
                () -> hrWorkflow.validateCompletion(context, "test", "task", true, "同意", 1L));
        requests.updateWorkflowState("payroll-test", request, "审批未通过", "task", "退回");
        assertEquals("rejected", payroll.detail(batch, false).get("status"));
        var rejected = payroll.detail(batch, false);
        long retry =
                requests.create(
                        PAYROLL,
                        "submit",
                        "hr.payroll.submit",
                        Map.of(),
                        "工资重新审批",
                        Map.of("payrollId", batch, "revision", rejected.get("revision")),
                        1L,
                        "13610316814");
        requests.updateWorkflowState("payroll-retry", retry, "审批通过", "task", "完成");
        requests.updateWorkflowState("payroll-retry", retry, "审批通过", "task", "完成");
        assertEquals("approved", payroll.detail(batch, false).get("status"));
    }

    @Test
    void salaryChangesInvalidateDraftAndRecalculationKeepsAuditSnapshot() {
        var b = draft();
        long batch = id(b.get("id"));
        salary("2026-10-01", "4400");
        assertEquals(true, payroll.detail(batch, false).get("sourcesChanged"));
        assertThrows(ApiException.class, () -> approve(b));
        var current = payroll.detail(batch, false);
        var revised =
                payroll.recalculate(
                        batch,
                        Map.of(
                                "revision",
                                current.get("revision"),
                                "attendanceId",
                                b.get("attendanceId"),
                                "reason",
                                "按已批准调薪重算",
                                "discardAdjustments",
                                true));
        assertEquals(false, revised.get("sourcesChanged"));
        assertEquals(
                new BigDecimal("4200.00"),
                ((List<Map<String, Object>>) revised.get("rows")).get(0).get("baseAmount"));
        assertTrue(
                jdbc.queryForObject(
                                "SELECT COUNT(*) FROM oa_hr_history WHERE entity_type='payroll' AND"
                                        + " entity_id=? AND action='重算前工资快照'",
                                Long.class,
                                batch)
                        > 0);
    }

    @Test
    void originalMemberActionsCreateProfilesHistoryAndDisableDepartedAccounts() {
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM oa_hr_employee WHERE user_id=?",
                        Integer.class,
                        user));
        members.transfer(dept, List.of(user));
        members.dismiss(List.of(user));
        assertEquals("departed", employees.detail(user).get("employmentStatus"));
        assertEquals(
                "1",
                jdbc.queryForObject(
                        "SELECT status FROM sys_user WHERE user_id=?", String.class, user));
        assertEquals(2, employees.events(user, false).size());
        assertFalse(String.valueOf(employees.changes()).contains("TEST-ACCOUNT"));
    }

    @Test
    void offboardingRequiresPendingApprovalsToBeTransferredOrCompleted() {
        String taskId = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO oa_process_task(id,instance_id,assignee_id,status)"
                    + " VALUES(?,?,?,'todo')",
                taskId,
                "HR-TEST-" + taskId,
                user);
        ApiException failure =
                assertThrows(ApiException.class, () -> members.dismiss(List.of(user)));
        assertTrue(failure.getMessage().contains("待审批任务"));
        assertEquals("active", employees.detail(user).get("employmentStatus"));
        assertEquals(
                "0",
                jdbc.queryForObject(
                        "SELECT status FROM sys_user WHERE user_id=?", String.class, user));
        jdbc.update("UPDATE oa_process_task SET status='done' WHERE id=?", taskId);
        members.dismiss(List.of(user));
        assertEquals("departed", employees.detail(user).get("employmentStatus"));
    }

    @Test
    void failedPaymentCanBeRetriedAndMoneyPrecisionIsStrict() {
        var b = approve(draft());
        long item = id(((List<Map<String, Object>>) b.get("rows")).get(0).get("id"));
        var pay =
                new LinkedHashMap<String, Object>(
                        Map.of(
                                "requestToken",
                                UUID.randomUUID().toString(),
                                "amount",
                                100,
                                "status",
                                "failed",
                                "paymentDate",
                                "2026-11-01",
                                "referenceNo",
                                UUID.randomUUID().toString(),
                                "reason",
                                "银行退回"));
        payroll.pay(item, pay);
        assertEquals(new BigDecimal("0").signum(), payroll.paid(item).signum());
        pay.putAll(
                Map.of(
                        "requestToken",
                        UUID.randomUUID().toString(),
                        "amount",
                        "0.001",
                        "status",
                        "paid",
                        "fileName",
                        "回执.pdf",
                        "fileContent",
                        Base64.getEncoder().encodeToString("proof".getBytes())));
        assertThrows(ApiException.class, () -> payroll.pay(item, pay));
        pay.put("amount", 100);
        payroll.pay(item, pay);
        assertEquals(new BigDecimal("100.00"), payroll.paid(item));
    }

    @Test
    void opposingSuccessfulPaymentsCannotReopenEvenWhenBatchPaidSumIsZero() {
        String phone = "HR" + System.nanoTime();
        long other =
                members.invite(
                        Map.of("userName", phone, "nickName", "扣回人员", "deptId", dept), "test");
        var e = employees.detail(other);
        e.putAll(Map.of("accountName", "测试", "bankName", "测试", "bankAccount", "TEST"));
        employees.save(other, e);
        var b =
                approve(
                        payroll.supplement(
                                Map.of(
                                        "period",
                                        "2026-10",
                                        "reason",
                                        "差额测试",
                                        "rows",
                                        List.of(
                                                Map.of(
                                                        "employeeNo",
                                                        "EMP" + user,
                                                        "adjustment",
                                                        100),
                                                Map.of(
                                                        "employeeNo",
                                                        "EMP" + other,
                                                        "adjustment",
                                                        -100)))));
        for (var item : (List<Map<String, Object>>) b.get("rows"))
            payroll.pay(
                    id(item.get("id")),
                    Map.of(
                            "requestToken",
                            UUID.randomUUID().toString(),
                            "amount",
                            item.get("netAmount"),
                            "status",
                            "paid",
                            "paymentDate",
                            "2026-11-01",
                            "referenceNo",
                            UUID.randomUUID().toString(),
                            "fileName",
                            "回执.pdf",
                            "fileContent",
                            Base64.getEncoder().encodeToString("proof".getBytes())));
        assertEquals(0, payroll.paidForBatch(id(b.get("id"))).signum());
        assertThrows(
                ApiException.class,
                () ->
                        payroll.reopen(
                                id(b.get("id")),
                                Map.of("revision", b.get("revision"), "reason", "已实际支付不能撤销")));
    }

    @Test
    void rehireKeepsPriorEmploymentPeriodForHistoricalPayroll() {
        salary("2026-01-01", "2200");
        var old = employees.detail(user);
        var departure =
                employees.change(
                        user,
                        Map.of(
                                "eventType",
                                "dismiss",
                                "effectiveDate",
                                "2026-11-01",
                                "reason",
                                "离职",
                                "revision",
                                old.get("revision")));
        workflows.execute(
                Map.of(
                        "moduleId",
                        CHANGES,
                        "actionCode",
                        "submit",
                        "payload",
                        Map.of(
                                "eventId",
                                departure.get("id"),
                                "revision",
                                departure.get("revision"))));
        var profile = employees.detail(user);
        var event =
                employees.change(
                        user,
                        Map.of(
                                "eventType",
                                "rehire",
                                "effectiveDate",
                                "2026-11-03",
                                "reason",
                                "重新入职",
                                "revision",
                                profile.get("revision")));
        workflows.execute(
                Map.of(
                        "moduleId",
                        CHANGES,
                        "actionCode",
                        "submit",
                        "payload",
                        Map.of("eventId", event.get("id"), "revision", event.get("revision"))));
        var b =
                payroll.generate(
                        Map.of("period", "2026-10", "attendanceId", confirmed(), "rules", rules()));
        assertEquals(
                new BigDecimal("2100.00"),
                ((List<Map<String, Object>>) b.get("rows")).get(0).get("baseAmount"));
        assertEquals(2, ((List<?>) employees.detail(user).get("employmentPeriods")).size());
    }

    @Test
    void midMonthSalaryUsesLatestConfirmedStandardForWholeMonth() {
        salary("2026-01-01", "2200");
        salary("2026-10-16", "4400");
        var rows = new ArrayList<Map<String, Object>>();
        for (int day = 1; day <= 31; day++) {
            var date = java.time.LocalDate.of(2026, 10, day);
            boolean work = date.getDayOfWeek().getValue() <= 5;
            var row = new LinkedHashMap<String, Object>();
            row.put("employeeNo", "EMP" + user);
            row.put("workDate", date.toString());
            for (String key : HrAttendanceService.DAY_FIELDS) row.put(key, 0);
            row.put("scheduledHours", work ? 8 : 0);
            row.put("actualHours", work && day != 1 ? 8 : 0);
            row.put("personalLeaveHours", day == 1 ? 8 : 0);
            rows.add(row);
        }
        var source = new LinkedHashMap<>(source(monthly()));
        source.put("kind", "daily");
        source.put("rows", rows);
        var daily = attendance.save(source);
        attendance.confirm(id(daily.get("id")), Map.of("revision", daily.get("revision")));
        var b =
                payroll.generate(
                        Map.of("period", "2026-10", "attendanceId", confirmed(), "rules", rules()));
        var row = ((List<Map<String, Object>>) b.get("rows")).get(0);
        assertEquals(new BigDecimal("4200.00"), row.get("baseAmount"));
        assertEquals(false, b.get("sourcesChanged"));
        var snapshot = (Map<String, Object>) row.get("snapshot");
        assertEquals(1, ((List<?>) snapshot.get("segments")).size());
    }

    @Test
    void latestSalaryWithinMonthNeedsNoMonthStartStandardAndIgnoresFutureAndDraft() {
        salary("2026-10-04", "5000");
        salary("2026-10-05", "9999");
        salary("2026-11-01", "20000");
        employees.salary(user, Map.of("effectiveDate", "2026-10-30", "payType", "monthly",
                "baseAmount", 30000, "allowance", 0, "reason", "未提交的草稿"));
        var batch = payroll.generate(Map.of("period", "2026-10", "attendanceId", confirmed()));
        var row = ((List<Map<String, Object>>) batch.get("rows")).get(0);
        assertEquals(new BigDecimal("9544.50"), row.get("baseAmount"));
        var snapshot = (Map<String, Object>) row.get("snapshot");
        assertEquals("monthly_latest_v1", snapshot.get("salaryBasis"));
        var segment = ((List<Map<String, Object>>) snapshot.get("segments")).get(0);
        assertEquals(jdbc.queryForObject("SELECT id FROM oa_hr_salary WHERE user_id=? AND effective_date='2026-10-05'",
                Long.class, user), id(segment.get("salaryId")));
    }

    @Test
    void payrollRejectsWhenOnlyFutureSalaryIsConfirmed() {
        salary("2026-11-01", "9999");
        var source = confirmed();
        var error = assertThrows(ApiException.class,
                () -> payroll.generate(Map.of("period", "2026-10", "attendanceId", source)));
        assertTrue(error.getMessage().contains("截至2026-10-31没有已确认的薪资标准"));
    }

    Map<String, Object> salaryRow() {
        return ((List<Map<String, Object>>) employees.detail(user).get("salaries")).get(0);
    }

    Map<String, Object> correctSalary(String amount) {
        var form = new LinkedHashMap<>(salaryRow());
        form.put("baseAmount", amount);
        form.put("reason", "更正金额并保留审批记录");
        return employees.salary(user, form);
    }

    long submitSalaryCorrection(Map<String, Object> standard) {
        return requests.create(EMPLOYEES, "salary_submit", "hr.salary.submit", Map.of(), "薪资更正",
                Map.of("salaryId", standard.get("id"), "revision", standard.get("revision")),
                1L, "13610316814");
    }

    @Test
    void salaryCorrectionRetainsApprovedStandardUntilApprovalThenRequiresRecalculation() {
        var batch = draft();
        var old = salaryRow();
        var correction = correctSalary("4400");
        assertEquals("draft", correction.get("status"));
        assertEquals(new BigDecimal("2200.00"),
                currency(((Map<String, Object>) salaryRow().get("confirmedSalary")).get("baseAmount"), "原标准"));
        assertEquals(false, payroll.detail(id(batch.get("id")), false).get("sourcesChanged"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oa_hr_salary_effective WHERE salary_id=?", Integer.class, old.get("id")));
        long request = submitSalaryCorrection(correction);
        assertEquals("approving", salaryRow().get("status"));
        assertEquals(false, payroll.detail(id(batch.get("id")), false).get("sourcesChanged"));
        assertEquals(409, assertThrows(ApiException.class, () -> correctSalary("4500")).code());
        requests.updateWorkflowState("salary-correction-pass", request, "审批通过", "task", "完成");
        requests.updateWorkflowState("salary-correction-pass", request, "审批通过", "task", "完成");
        assertEquals("confirmed", salaryRow().get("status"));
        assertFalse(salaryRow().containsKey("confirmedSalary"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM oa_hr_salary_effective WHERE salary_id=?", Integer.class, old.get("id")));
        var unchanged = payroll.detail(id(batch.get("id")), false);
        assertEquals(true, unchanged.get("sourcesChanged"));
        assertEquals(new BigDecimal("2100.00"), ((List<Map<String, Object>>) unchanged.get("rows")).get(0).get("baseAmount"));
        var recalculated = payroll.recalculate(id(batch.get("id")), Map.of("revision", unchanged.get("revision"),
                "attendanceId", unchanged.get("attendanceId"), "reason", "采用获批更正标准", "discardAdjustments", true));
        assertEquals(new BigDecimal("4200.00"), ((List<Map<String, Object>>) recalculated.get("rows")).get(0).get("baseAmount"));
        var history = ((List<Map<String, Object>>) employees.detail(user).get("salaryHistory")).stream()
                .filter(h -> "更正已确认薪资".equals(h.get("action"))).findFirst().orElseThrow();
        var detail = (Map<String, Object>) history.get("detail");
        assertEquals(new BigDecimal("2200.00"), currency(((Map<?, ?>) detail.get("before")).get("baseAmount"), "原值"));
        assertEquals(new BigDecimal("4400.00"), currency(((Map<?, ?>) detail.get("after")).get("baseAmount"), "新值"));
    }

    @Test
    void rejectedSalaryCorrectionStillUsesOriginalAndCanBeResubmitted() {
        salary("2026-01-01", "2200");
        var correction = correctSalary("4400");
        long request = submitSalaryCorrection(correction);
        requests.updateWorkflowState("salary-correction-reject", request, "审批未通过", "task", "退回");
        assertEquals("rejected", salaryRow().get("status"));
        var batch = payroll.generate(Map.of("period", "2026-10", "attendanceId", confirmed()));
        assertEquals(new BigDecimal("2100.00"), ((List<Map<String, Object>>) batch.get("rows")).get(0).get("baseAmount"));
        var edited = correctSalary("4600");
        long retry = submitSalaryCorrection(edited);
        requests.updateWorkflowState("salary-correction-retry", retry, "审批通过", "task", "完成");
        assertEquals("confirmed", salaryRow().get("status"));
        assertEquals(new BigDecimal("4600.00"), salaryRow().get("baseAmount"));
    }

    @Test
    void confirmedSalaryDateAndStaleEditsAreRejectedWithoutLosingOriginal() {
        salary("2026-01-01", "2200");
        var form = new LinkedHashMap<>(salaryRow());
        form.put("effectiveDate", "2026-01-02");
        assertEquals(409, assertThrows(ApiException.class, () -> employees.salary(user, form)).code());
        form.put("effectiveDate", "2026-01-01");
        correctSalary("4400");
        assertEquals(409, assertThrows(ApiException.class, () -> employees.salary(user, form)).code());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oa_hr_salary_effective WHERE salary_id=?", Integer.class, form.get("id")));
    }

    @Test
    void employmentTypesUseOneDictionaryAndStoreCodesWithoutChangingSalary() {
        var options = (Map<String, Object>) controller.options().get("data");
        assertEquals(HrEmploymentType.options(), options.get("employmentTypes"));
        for (var option : HrEmploymentType.options()) {
            var profile = employees.detail(user);
            profile.put("employmentType", option.get("label"));
            var saved = employees.save(user, profile);
            assertEquals(option.get("value"), saved.get("employmentType"));
            assertEquals(option.get("label"), saved.get("employmentTypeName"));
            assertEquals(false, saved.get("employmentTypeLegacy"));
            assertEquals(option.get("value"), jdbc.queryForObject(
                    "SELECT employment_type FROM oa_hr_employee WHERE user_id=?",
                    String.class, user));
            assertTrue(((List<?>) saved.get("salaries")).isEmpty());
        }
        var invalid = employees.detail(user);
        invalid.put("employmentType", "随意新增类型");
        assertEquals(400, assertThrows(ApiException.class, () -> employees.save(user, invalid)).code());
        assertEquals("labor", employees.detail(user).get("employmentType"));
        invalid.put("employmentType", null);
        assertEquals("", employees.save(user, invalid).get("employmentType"));
    }

    @Test
    void legacyEmploymentTypeIsPreservedUntilExplicitlyClassified() {
        jdbc.update("UPDATE oa_hr_employee SET employment_type=? WHERE user_id=?", "历史全职员工", user);
        var profile = employees.detail(user);
        assertEquals("历史全职员工", profile.get("employmentType"));
        assertEquals(true, profile.get("employmentTypeLegacy"));
        profile.put("emergencyContact", "更新联系人");
        assertEquals("历史全职员工", employees.save(user, profile).get("employmentType"));
        var report = reports.employees(Map.of()).stream()
                .filter(row -> id(row.get("userId")) == user).findFirst().orElseThrow();
        assertEquals("历史全职员工", report.get("employmentTypeName"));
        profile = employees.detail(user);
        profile.put("employmentType", "direct");
        assertEquals(false, employees.save(user, profile).get("employmentTypeLegacy"));
    }

    @Test
    void profileImportAcceptsLabelsAndCodesAndRejectsUnknownTypes() {
        employees.importProfiles(Map.of("rows", List.of(
                profileImportRow(Map.of("employmentType", "劳务派遣")))));
        assertEquals("dispatch", employees.detail(user).get("employmentType"));
        employees.importProfiles(Map.of("rows", List.of(
                profileImportRow(Map.of("employmentType", "internship")))));
        assertEquals("internship", employees.detail(user).get("employmentType"));
        employees.importProfiles(Map.of("rows", List.of(
                profileImportRow(Map.of("emergencyContact", "只更新联系人")))));
        assertEquals("internship", employees.detail(user).get("employmentType"));
        var report = reports.employees(Map.of()).stream()
                .filter(row -> id(row.get("userId")) == user).findFirst().orElseThrow();
        assertEquals("实习", report.get("employmentTypeName"));
        assertThrows(ApiException.class, () -> employees.importProfiles(Map.of("rows", List.of(
                profileImportRow(Map.of("employmentType", "试用"))))));
        assertEquals("internship", employees.detail(user).get("employmentType"));
        AuthContext.set(new AuthContext.CurrentUser(user, "test"));
        assertThrows(ApiException.class, () -> controller.options());
    }

    Map<String, Object> profileImportRow(Map<String, Object> changes) {
        var row = new LinkedHashMap<String, Object>();
        row.put("employeeNo", "EMP" + user);
        row.put("revision", employees.detail(user).get("revision"));
        row.putAll(changes);
        return row;
    }

    @Test
    void profileImportPreservesBlankFieldsAndRejectsStaleOrMissingVersions() {
        var original = employees.detail(user);
        var oldRow = profileImportRow(Map.of("emergencyContact", "旧表联系人"));
        var row = profileImportRow(Map.of("emergencyContact", "新联系人", "employmentType", "  ", "employeeName", ""));
        row.put("entryDate", null);
        employees.importProfiles(Map.of("rows", List.of(row)));
        var saved = employees.detail(user);
        assertEquals("新联系人", saved.get("emergencyContact"));
        assertEquals(original.get("entryDate"), saved.get("entryDate"));
        assertEquals(original.get("employmentType"), saved.get("employmentType"));
        assertEquals(id(original.get("revision")) + 1, id(saved.get("revision")));
        assertEquals(409, assertThrows(ApiException.class,
                () -> employees.importProfiles(Map.of("rows", List.of(oldRow)))).code());
        var missingVersion = profileImportRow(Map.of("emergencyContact", "无版本联系人"));
        missingVersion.remove("revision");
        assertEquals(409, assertThrows(ApiException.class,
                () -> employees.importProfiles(Map.of("rows", List.of(missingVersion)))).code());
        assertEquals("新联系人", employees.detail(user).get("emergencyContact"));
    }

    @Test
    void profileImportDoesNotCreateUnknownEmployees() {
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM oa_hr_employee", Long.class);
        var row = Map.<String, Object>of("employeeNo", "UNKNOWN-" + user, "revision", 0);
        assertEquals(400, assertThrows(ApiException.class,
                () -> employees.importProfiles(Map.of("rows", List.of(row)))).code());
        assertEquals(count, jdbc.queryForObject("SELECT COUNT(*) FROM oa_hr_employee", Long.class));
    }

    @Test
    void profileEmailImportUpdatesMemberEmailAndPreservesBlanksAndRejectsInvalidValues() {
        employees.importProfiles(Map.of("rows", List.of(profileImportRow(Map.of("email", "  employee@example.com  ")))));
        assertEquals("employee@example.com", employees.detail(user).get("email"));
        assertEquals("employee@example.com", jdbc.queryForObject("SELECT email FROM sys_user WHERE user_id=?", String.class, user));
        var exported = reports.employees(Map.of()).stream().filter(r -> id(r.get("userId")) == user).findFirst().orElseThrow();
        assertEquals("employee@example.com", exported.get("email"));
        employees.importProfiles(Map.of("rows", List.of(profileImportRow(Map.of("email", "  ")))));
        assertEquals("employee@example.com", employees.detail(user).get("email"));
        var old = profileImportRow(Map.of("email", "old@example.com"));
        for (String invalid : List.of("invalid-email", "name@", "a".repeat(120) + "@example.com")) {
            var revision = employees.detail(user).get("revision");
            assertEquals(400, assertThrows(ApiException.class, () -> employees.importProfiles(Map.of("rows", List.of(profileImportRow(Map.of("email", invalid)))))).code());
            assertEquals(revision, employees.detail(user).get("revision"));
            assertEquals("employee@example.com", employees.detail(user).get("email"));
        }
        var direct = employees.detail(user);
        direct.put("email", "updated@example.com");
        employees.save(user, direct);
        assertEquals(409, assertThrows(ApiException.class, () -> employees.importProfiles(Map.of("rows", List.of(old)))).code());
        direct = employees.detail(user);
        direct.put("email", "");
        assertEquals("", employees.save(user, direct).get("email"));
    }

    @Test
    void reportEndpointsIncludeAccountsOnlyWithSensitiveFieldPermission() {
        var rows = reports.employees(Map.of());
        var row = rows.stream().filter(r -> id(r.get("userId")) == user).findFirst().orElseThrow();
        assertEquals("TEST-ACCOUNT", row.get("bankAccount"));
        assertFalse(row.containsKey("salaries"));
        AuthContext.set(new AuthContext.CurrentUser(user, "test"));
        assertThrows(ApiException.class, () -> reports.employees(Map.of()));
        assertThrows(ApiException.class, () -> reports.changes());
    }

    @Test
    void profileAccountExportAndImportRespectReadWritePermissionsAndPreserveBlankValues() {
        employees.importProfiles(Map.of("rows", List.of(profileImportRow(Map.of("accountName", "实际收款人", "bankName", "实际银行", "bankAccount", "001234567890123456789")))));
        var exported = reports.employees(Map.of()).stream().filter(r -> id(r.get("userId")) == user).findFirst().orElseThrow();
        assertEquals("001234567890123456789", exported.get("bankAccount"));
        employees.importProfiles(Map.of("rows", List.of(profileImportRow(Map.of("accountName", "", "bankName", "", "bankAccount", "")))));
        assertEquals("001234567890123456789", employees.detail(user).get("bankAccount"));
        jdbc.update("INSERT INTO sys_role(role_id,role_name,role_key,data_scope) VALUES(99401,'档案账户权限测试','profile_accounts_test','1')");
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", user);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,99401)", user);
        jdbc.update("INSERT INTO sys_role_module(role_id,module_id) VALUES(99401,341002)");
        for (String code : List.of("edit", "export"))
            jdbc.update("INSERT INTO sys_role_module_action(role_id,module_id,action_code) VALUES(99401,341002,?)", code);
        AuthContext.set(new AuthContext.CurrentUser(user, "test"));
        var hidden = reports.employees(Map.of()).stream().filter(r -> id(r.get("userId")) == user).findFirst().orElseThrow();
        for (String field : List.of("accountName", "bankName", "bankAccount")) assertFalse(hidden.containsKey(field));
        assertEquals(403, assertThrows(ApiException.class, () -> employees.importProfiles(Map.of("rows", List.of(profileImportRow(Map.of("bankAccount", "002222")))))).code());
        jdbc.update("INSERT INTO sys_role_module_action(role_id,module_id,action_code) VALUES(99401,341002,'salary_view')");
        var visible = reports.employees(Map.of()).stream().filter(r -> id(r.get("userId")) == user).findFirst().orElseThrow();
        assertEquals("实际银行", visible.get("bankName"));
        employees.importProfiles(Map.of("rows", List.of(profileImportRow(Map.of("bankAccount", visible.get("bankAccount"), "emergencyContact", "只修改普通档案")))));
        assertEquals(403, assertThrows(ApiException.class, () -> employees.importProfiles(Map.of("rows", List.of(profileImportRow(Map.of("bankAccount", "002222")))))).code());
        jdbc.update("INSERT INTO sys_role_module_action(role_id,module_id,action_code) VALUES(99401,341002,'salary_manage')");
        employees.importProfiles(Map.of("rows", List.of(profileImportRow(Map.of("bankAccount", "002222")))));
        assertEquals("002222", employees.detail(user).get("bankAccount"));
    }

    @Test
    void accountSyncPreviewsDifferencesAndOnlyInvalidatesChangedEmployees() {
        var b = payroll.supplement(Map.of("period", "2026-10", "reason", "同步账户测试", "rows", List.of(
                Map.of("employeeNo", "EMP" + user, "adjustment", 1000), Map.of("employeeNo", "EMP4", "adjustment", 2000))));
        b = enterZeroTax(b); long batch = id(b.get("id"));
        payroll.verify(batch, Map.of("revision", b.get("revision"), "reason", "逐人核对"));
        var checked = payroll.detail(batch, false);
        long historyCount = jdbc.queryForObject("SELECT COUNT(*) FROM oa_hr_history WHERE entity_type='payroll' AND entity_id=?", Long.class, batch);
        assertEquals(0, payroll.refreshAccounts(batch, Map.of("revision", checked.get("revision"))).get("updatedCount"));
        assertEquals(checked.get("revision"), payroll.detail(batch, false).get("revision"));
        assertEquals(historyCount, jdbc.queryForObject("SELECT COUNT(*) FROM oa_hr_history WHERE entity_type='payroll' AND entity_id=?", Long.class, batch));
        var e = employees.detail(user);
        employees.accounts(user, Map.of("revision", e.get("revision"), "accountName", "新开户人", "bankName", "新银行", "bankAccount", "001234567890123456789"));
        var preview = payroll.detail(batch, false);
        assertEquals(1, ((List<?>)preview.get("accountChanges")).size());
        var ctx = new com.seal.oa.workflow.WorkflowActionContext(0,PAYROLL,"submit","hr.payroll.submit",Map.of(),Map.of("payrollId",batch,"revision",preview.get("revision")),1L,"admin");
        assertTrue(assertThrows(ApiException.class, () -> payroll.validateWorkflow(ctx)).getMessage().contains("收款账户"));
        assertEquals(409, assertThrows(ApiException.class, () -> payroll.refreshAccounts(batch, Map.of("revision", preview.get("revision")))).code());
        assertEquals(1, payroll.refreshAccounts(batch, Map.of("revision", preview.get("revision"), "changesToken", preview.get("accountChangesToken"))).get("updatedCount"));
        var synced = payroll.detail(batch, false);
        assertTrue(((List<?>)synced.get("accountChanges")).isEmpty());
        assertEquals(checked.get("netAmount"), synced.get("netAmount"));
        for (var item : (List<Map<String,Object>>) synced.get("rows")) {
            assertEquals(1L, optionalId(item.get("taxEntered")));
            if (id(item.get("userId")) == user) {
                assertEquals(0L, optionalId(item.get("verified")));
                assertEquals("001234567890123456789", ((Map<?,?>)item.get("snapshot")).get("bankAccount"));
            } else assertEquals(1L, optionalId(item.get("verified")));
        }
        assertEquals("同步最新收款账户", ((List<Map<String,Object>>)synced.get("history")).get(0).get("action"));
    }

    @Test
    void accountSyncRejectsStalePreviewAndFrozenPayrolls() {
        var b = draft(); long batch = id(b.get("id"));
        var e = employees.detail(user);
        employees.accounts(user, Map.of("revision", e.get("revision"), "accountName", "A", "bankName", "B", "bankAccount", "001"));
        var first = payroll.detail(batch, false);
        e = employees.detail(user);
        employees.accounts(user, Map.of("revision", e.get("revision"), "accountName", "A", "bankName", "B", "bankAccount", "002"));
        var body = Map.<String,Object>of("revision", first.get("revision"), "changesToken", first.get("accountChangesToken"));
        assertEquals(409, assertThrows(ApiException.class, () -> payroll.refreshAccounts(batch, body)).code());
        assertEquals("TEST-ACCOUNT", ((Map<?,?>)((List<Map<String,Object>>)payroll.detail(batch,false).get("rows")).get(0).get("snapshot")).get("bankAccount"));
        for (String status : List.of("approving", "approved")) {
            jdbc.update("UPDATE oa_hr_payroll SET status=? WHERE id=?", status, batch);
            assertThrows(ApiException.class, () -> payroll.refreshAccounts(batch, body));
            assertFalse(payroll.detail(batch, false).containsKey("accountChanges"));
        }
    }
}
