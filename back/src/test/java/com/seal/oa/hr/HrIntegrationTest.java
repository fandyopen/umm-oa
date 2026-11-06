package com.seal.oa.hr;

import static com.seal.oa.hr.HrData.*;

import static org.junit.jupiter.api.Assertions.*;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.system.HrMemberService;
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
    long user, dept;

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

    long confirmed() {
        var a = attendance.save(source(monthly()));
        long id = id(a.get("id"));
        attendance.confirm(id, Map.of("revision", a.get("revision")));
        return id;
    }

    Map<String, Object> draft() {
        salary("2026-01-01", "2200");
        return payroll.generate(
                Map.of("period", "2026-10", "attendanceId", confirmed(), "rules", rules()));
    }

    Map<String, Object> approve(Map<String, Object> b) {
        long id = id(b.get("id"));
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
    void midMonthChangesRequireDailyEvidence() {
        salary("2026-01-01", "2200");
        salary("2026-10-16", "4400");
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
        var b = draft();
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
    void midMonthSalaryUsesDailySegmentsAndConfirmedVersions() {
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
        assertEquals(new BigDecimal("3200.00"), row.get("baseAmount"));
        assertEquals(false, b.get("sourcesChanged"));
        var snapshot = (Map<String, Object>) row.get("snapshot");
        assertEquals(31, ((List<?>) snapshot.get("segments")).size());
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
    void reportEndpointsDoNotExposeBankAccountsAndArePermissionProtected() {
        var rows = reports.employees(Map.of());
        var row = rows.stream().filter(r -> id(r.get("userId")) == user).findFirst().orElseThrow();
        assertFalse(row.containsKey("bankAccount"));
        assertFalse(row.containsKey("salaries"));
        AuthContext.set(new AuthContext.CurrentUser(user, "test"));
        assertThrows(ApiException.class, () -> reports.employees(Map.of()));
        assertThrows(ApiException.class, () -> reports.changes());
    }
}
