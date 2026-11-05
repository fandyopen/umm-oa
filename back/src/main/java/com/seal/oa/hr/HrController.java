package com.seal.oa.hr;

import static com.seal.oa.hr.HrData.*;

import com.seal.oa.common.ApiResult;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/project/hr")
public class HrController {
    private final HrData d;
    private final HrEmployeeService employees;
    private final HrAttendanceService attendance;
    private final HrPayrollService payroll;
    private final HrReportService reports;

    public HrController(
            HrData d,
            HrEmployeeService employees,
            HrAttendanceService attendance,
            HrPayrollService payroll,
            HrReportService reports) {
        this.d = d;
        this.employees = employees;
        this.attendance = attendance;
        this.payroll = payroll;
        this.reports = reports;
    }

    @GetMapping("/employees")
    public Map<String, Object> employees(@RequestParam Map<String, String> q) {
        return ApiResult.ok(employees.list(q));
    }

    @GetMapping("/reports/employees")
    public Map<String, Object> employeeReport(@RequestParam Map<String, String> q) {
        return ApiResult.ok(reports.employees(q));
    }

    @GetMapping("/reports/changes")
    public Map<String, Object> changeReport() {
        return ApiResult.ok(reports.changes());
    }

    @GetMapping("/reports/attendance/{id}")
    public Map<String, Object> attendanceReport(@PathVariable long id) {
        return ApiResult.ok(reports.attendance(id));
    }

    @GetMapping("/reports/payroll/{id}")
    public Map<String, Object> payrollReport(
            @PathVariable long id, @RequestParam(defaultValue = "false") boolean finance) {
        return ApiResult.ok(reports.payroll(id, finance));
    }

    @GetMapping("/reports/payroll")
    public Map<String, Object> payrollSummary(
            @RequestParam Map<String, String> q,
            @RequestParam(defaultValue = "false") boolean finance) {
        return ApiResult.ok(reports.summary(q, finance));
    }

    @GetMapping("/employees/{user}")
    public Map<String, Object> employee(@PathVariable long user) {
        return ApiResult.ok(employees.detail(user));
    }

    @PutMapping("/employees/{user}")
    public Map<String, Object> save(@PathVariable long user, @RequestBody Map<String, Object> b) {
        return ApiResult.ok(employees.save(user, b));
    }

    @PostMapping("/employees/import")
    public Map<String, Object> importEmployees(@RequestBody Map<String, Object> b) {
        employees.importProfiles(b);
        return ApiResult.ok();
    }

    @PostMapping("/employees/{user}/salary")
    public Map<String, Object> salary(@PathVariable long user, @RequestBody Map<String, Object> b) {
        return ApiResult.ok(employees.salary(user, b));
    }

    @GetMapping("/salaries/{id}")
    public Map<String, Object> salaryDetail(@PathVariable long id) {
        if (!d.allowed(EMPLOYEES, "salary_view") && !d.allowed(EMPLOYEES, "salary_manage"))
            d.require(EMPLOYEES, "approve");
        var row =
                d.one(
                        "SELECT s.*,u.nick_name,e.employee_no FROM oa_hr_salary s JOIN sys_user u"
                            + " ON u.user_id=s.user_id JOIN oa_hr_employee e ON e.user_id=s.user_id"
                            + " WHERE s.id=?",
                        id);
        d.employee(HrData.id(row.get("userId")));
        return ApiResult.ok(row);
    }

    @GetMapping("/changes/{id}")
    public Map<String, Object> changeDetail(@PathVariable long id) {
        d.require(CHANGES, "access");
        var row =
                d.one(
                        "SELECT e.*,u.nick_name FROM oa_hr_event e JOIN sys_user u ON"
                                + " u.user_id=e.user_id WHERE e.id=?",
                        id);
        d.employee(HrData.id(row.get("userId")));
        return ApiResult.ok(row);
    }

    @PutMapping("/employees/{user}/accounts")
    public Map<String, Object> employeeAccounts(
            @PathVariable long user, @RequestBody Map<String, Object> b) {
        return ApiResult.ok(employees.accounts(user, b));
    }

    @PostMapping("/salaries/import")
    public Map<String, Object> importSalaries(@RequestBody Map<String, Object> b) {
        employees.importSalaries(b);
        return ApiResult.ok();
    }

    @PostMapping("/employees/{user}/attachments")
    public Map<String, Object> attachment(
            @PathVariable long user, @RequestBody Map<String, Object> b) {
        d.require(EMPLOYEES, "edit");
        d.employee(user);
        return ApiResult.ok(Map.of("id", attendance.saveFile(user, null, b)));
    }

    @GetMapping("/files/{id}")
    public Map<String, Object> file(@PathVariable long id) {
        return ApiResult.ok(attendance.file(id));
    }

    @GetMapping("/changes")
    public Map<String, Object> changes() {
        return ApiResult.ok(employees.changes());
    }

    @PostMapping("/employees/{user}/changes")
    public Map<String, Object> change(@PathVariable long user, @RequestBody Map<String, Object> b) {
        return ApiResult.ok(employees.change(user, b));
    }

    @PostMapping("/changes/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable long id, @RequestBody Map<String, Object> b) {
        employees.cancel(id, b);
        return ApiResult.ok();
    }

    @GetMapping("/employees/{user}/handover")
    public Map<String, Object> handovers(@PathVariable long user) {
        return ApiResult.ok(employees.handovers(user));
    }

    @PostMapping("/handover/{id}")
    public Map<String, Object> handover(@PathVariable long id, @RequestBody Map<String, Object> b) {
        employees.handover(id, b);
        return ApiResult.ok();
    }

    @GetMapping("/options")
    public Map<String, Object> options() {
        if (!d.allowed(EMPLOYEES, "access")
                && !d.allowed(CHANGES, "access")
                && !d.allowed(ATTENDANCE, "access")
                && !d.allowed(PAYROLL, "access")
                && !d.allowed(FINANCE, "access")) d.require(EMPLOYEES, "access");
        return ApiResult.ok(
                Map.of(
                        "departments",
                        d.rows(
                                "SELECT dept_id,dept_name FROM sys_dept WHERE status='0' AND"
                                        + " del_flag='0'"),
                        "users",
                        d
                                .rows(
                                        "SELECT"
                                            + " u.user_id,u.nick_name,u.dept_id,u.del_flag,u.status,e.employee_no,e.revision,e.employment_status"
                                            + " FROM sys_user u JOIN oa_hr_employee e ON"
                                            + " e.user_id=u.user_id WHERE u.del_flag IN ('0','2')")
                                .stream()
                                .filter(
                                        u ->
                                                d.scope.canAccess(
                                                        com.seal.oa.auth.AuthContext.userId(),
                                                        u.get("deptId"),
                                                        u.get("userId")))
                                .toList()));
    }

    @GetMapping("/attendance")
    public Map<String, Object> attendance(@RequestParam Map<String, String> q) {
        return ApiResult.ok(attendance.batches(q));
    }

    @GetMapping("/attendance/{id}")
    public Map<String, Object> attendance(@PathVariable long id) {
        return ApiResult.ok(attendance.detail(id, false));
    }

    @PostMapping("/attendance/preview")
    public Map<String, Object> preview(@RequestBody Map<String, Object> b) {
        return ApiResult.ok(attendance.preview(b));
    }

    @PostMapping("/attendance")
    public Map<String, Object> importAttendance(@RequestBody Map<String, Object> b) {
        return ApiResult.ok(attendance.save(b));
    }

    @PostMapping("/attendance/{id}/confirm")
    public Map<String, Object> confirm(@PathVariable long id, @RequestBody Map<String, Object> b) {
        attendance.confirm(id, b);
        return ApiResult.ok();
    }

    @GetMapping("/payroll")
    public Map<String, Object> payroll(@RequestParam Map<String, String> q) {
        return ApiResult.ok(payroll.list(q, false));
    }

    @GetMapping("/payroll/{id}")
    public Map<String, Object> payroll(@PathVariable long id) {
        return ApiResult.ok(payroll.detail(id, false));
    }

    @PostMapping("/payroll")
    public Map<String, Object> generate(@RequestBody Map<String, Object> b) {
        return ApiResult.ok(payroll.generate(b));
    }

    @GetMapping("/payroll/attendance-options")
    public Map<String, Object> payrollAttendance(@RequestParam String period) {
        return ApiResult.ok(payroll.attendanceOptions(period));
    }

    @PostMapping("/payroll/supplement")
    public Map<String, Object> supplement(@RequestBody Map<String, Object> b) {
        return ApiResult.ok(payroll.supplement(b));
    }

    @PostMapping("/payroll/{id}/recalculate")
    public Map<String, Object> recalculate(
            @PathVariable long id, @RequestBody Map<String, Object> b) {
        return ApiResult.ok(payroll.recalculate(id, b));
    }

    @PostMapping("/payroll/{id}/preview")
    public Map<String, Object> previewPayroll(
            @PathVariable long id, @RequestBody Map<String, Object> b) {
        return ApiResult.ok(payroll.previewImport(id, b));
    }

    @PostMapping("/payroll/{id}/import")
    public Map<String, Object> importPayroll(
            @PathVariable long id, @RequestBody Map<String, Object> b) {
        payroll.importRows(id, b);
        return ApiResult.ok();
    }

    @PostMapping("/payroll/{id}/verify")
    public Map<String, Object> verify(@PathVariable long id, @RequestBody Map<String, Object> b) {
        payroll.verify(id, b);
        return ApiResult.ok();
    }

    @PostMapping("/payroll/{id}/accounts")
    public Map<String, Object> accounts(@PathVariable long id, @RequestBody Map<String, Object> b) {
        payroll.refreshAccounts(id, b);
        return ApiResult.ok();
    }

    @PostMapping("/payroll/{id}/reopen")
    public Map<String, Object> reopen(@PathVariable long id, @RequestBody Map<String, Object> b) {
        payroll.reopen(id, b);
        return ApiResult.ok();
    }

    @PostMapping("/payroll/{id}/publish")
    public Map<String, Object> publish(@PathVariable long id, @RequestBody Map<String, Object> b) {
        payroll.publish(id, b);
        return ApiResult.ok();
    }

    @PostMapping("/payroll/items/{id}/pay")
    public Map<String, Object> pay(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return ApiResult.ok(payroll.pay(id, b));
    }

    @GetMapping("/self/attendance")
    public Map<String, Object> selfAttendance() {
        return ApiResult.ok(attendance.self());
    }

    @GetMapping("/self/attendance/{id}")
    public Map<String, Object> selfAttendance(@PathVariable long id) {
        return ApiResult.ok(attendance.detail(id, true));
    }

    @GetMapping("/self/payslips")
    public Map<String, Object> payslips() {
        return ApiResult.ok(payroll.self());
    }

    @PostMapping("/self/payslips/{id}/read")
    public Map<String, Object> read(@PathVariable long id) {
        payroll.acknowledge(id);
        return ApiResult.ok();
    }

    @GetMapping("/finance/payroll")
    public Map<String, Object> finance(@RequestParam Map<String, String> q) {
        return ApiResult.ok(payroll.list(q, true));
    }

    @GetMapping("/finance/payroll/{id}")
    public Map<String, Object> financeDetail(@PathVariable long id) {
        return ApiResult.ok(payroll.detail(id, true));
    }
}
