package com.seal.oa.hr;

import static com.seal.oa.hr.HrData.*;

import org.springframework.stereotype.Service;

import java.util.*;

/** The same scopes and field permissions apply to the screen and downloaded reports. */
@Service
public class HrReportService {
    private final HrData d;
    private final HrEmployeeService employees;
    private final HrAttendanceService attendance;
    private final HrPayrollService payroll;

    public HrReportService(
            HrData d,
            HrEmployeeService employees,
            HrAttendanceService attendance,
            HrPayrollService payroll) {
        this.d = d;
        this.employees = employees;
        this.attendance = attendance;
        this.payroll = payroll;
    }

    public List<Map<String, Object>> employees(Map<String, String> filters) {
        d.require(EMPLOYEES, "export");
        return employees.list(filters).stream()
                .map(
                        row -> {
                            var e = new LinkedHashMap<>(employees.detail(id(row.get("userId"))));
                            for (String key :
                                    List.of(
                                            "salaries",
                                            "attachments",
                                            "events",
                                            "accountName",
                                            "bankName",
                                            "bankAccount")) e.remove(key);
                            e.put("employeeName", e.get("nickName"));
                            return (Map<String, Object>) e;
                        })
                .toList();
    }

    public List<Map<String, Object>> changes() {
        d.require(CHANGES, "export");
        return employees.changes();
    }

    public Map<String, Object> attendance(long id) {
        d.require(ATTENDANCE, "export");
        return attendance.detail(id, false);
    }

    public Map<String, Object> payroll(long id, boolean finance) {
        d.require(finance ? FINANCE : PAYROLL, "export");
        return payroll.detail(id, finance);
    }

    public List<Map<String, Object>> summary(Map<String, String> filters, boolean finance) {
        d.require(finance ? FINANCE : PAYROLL, "export");
        return payroll.list(filters, finance);
    }
}
