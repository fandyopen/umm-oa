package com.seal.oa.hr;

import static com.seal.oa.hr.HrData.*;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.YearMonth;
import java.util.*;

@Service
public class HrAttendanceService {
    static final List<String> MONTH_FIELDS =
            List.of(
                    "requiredDays",
                    "actualDays",
                    "personalLeaveHours",
                    "sickLeaveHours",
                    "paidLeaveHours",
                    "absentDays",
                    "overtimeHours",
                    "lateCount",
                    "earlyCount",
                    "missingCount");
    static final List<String> DAY_FIELDS =
            List.of(
                    "scheduledHours",
                    "actualHours",
                    "personalLeaveHours",
                    "sickLeaveHours",
                    "paidLeaveHours",
                    "absentHours",
                    "overtimeHours",
                    "lateCount",
                    "earlyCount",
                    "missingCount");
    final HrData d;
    final HrEmployeeService employees;

    public HrAttendanceService(HrData d, HrEmployeeService employees) {
        this.d = d;
        this.employees = employees;
    }

    public Map<String, Object> preview(Map<String, Object> b) {
        d.require(ATTENDANCE, "import");
        String period = period(b.get("period")), kind = required(b.get("kind"), "数据类型", 16);
        if (!Set.of("monthly", "daily", "leave").contains(kind))
            throw new ApiException(400, "考勤类型无效");
        List<Map<String, Object>> result = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        int line = 1;
        for (var input : d.records(b.get("rows"))) {
            line++;
            try {
                long user = employees.match(input);
                var employee = d.employee(user);
                var r = new LinkedHashMap<String, Object>();
                r.put("userId", user);
                r.put("employeeNo", employee.get("employeeNo"));
                r.put("employeeName", employee.get("nickName"));
                r.put("deptName", employee.get("deptName"));
                if (!text(input.get("period")).isEmpty()
                        && !period.equals(period(input.get("period"))))
                    throw new ApiException(400, "数据月份与选定月份不一致");
                r.put("period", period);
                if ("monthly".equals(kind))
                    for (String field : MONTH_FIELDS)
                        r.put(field, nonnegative(input.get(field), field));
                if ("daily".equals(kind)) {
                    var day = date(input.get("workDate"));
                    if (!period.equals(YearMonth.from(day).toString()))
                        throw new ApiException(400, "考勤日期不在本期");
                    r.put("workDate", day.toString());
                    for (String field : DAY_FIELDS)
                        r.put(field, nonnegative(input.get(field), field));
                    for (String field : List.of("checkIn", "checkOut", "result"))
                        r.put(field, limited(input.get(field), 200));
                    if (nonnegative(r.get("scheduledHours"), "排班工时").compareTo(new BigDecimal("24"))
                            > 0) throw new ApiException(400, "单日工时超过24小时");
                }
                if ("leave".equals(kind)) {
                    r.put("leaveId", required(input.get("leaveId"), "审批实例编号", 128));
                    r.put("leaveType", required(input.get("leaveType"), "请假类型", 64));
                    r.put("startDate", date(input.get("startDate")).toString());
                    r.put("endDate", date(input.get("endDate")).toString());
                    if (date(r.get("endDate")).isBefore(date(r.get("startDate")))
                            || date(r.get("startDate"))
                                    .isAfter(YearMonth.parse(period).atEndOfMonth())
                            || date(r.get("endDate")).isBefore(YearMonth.parse(period).atDay(1)))
                        throw new ApiException(400, "请假日期范围无效或不属于本期");
                    r.put("leaveHours", nonnegative(input.get("leaveHours"), "请假小时数"));
                    r.put("approvalStatus", required(input.get("approvalStatus"), "钉钉审批状态", 32));
                }
                if (!"leave".equals(kind))
                    for (String count : List.of("lateCount", "earlyCount", "missingCount"))
                        if (number(r.get(count), count).stripTrailingZeros().scale() > 0)
                            throw new ApiException(400, "迟到、早退和缺卡次数必须是整数");
                String key =
                        user
                                + "/"
                                + ("daily".equals(kind)
                                        ? r.get("workDate")
                                        : "leave".equals(kind) ? r.get("leaveId") : "monthly");
                if (!unique.add(key)) throw new ApiException(400, "批次内存在重复员工、日期或请假实例");
                result.add(r);
            } catch (ApiException ex) {
                errors.add("第" + line + "行：" + ex.getMessage());
            }
        }
        return Map.of("rows", result, "errors", errors, "valid", errors.isEmpty());
    }

    @Transactional
    public Map<String, Object> save(Map<String, Object> b) {
        var checked = preview(b);
        if (!Boolean.TRUE.equals(checked.get("valid")))
            throw new ApiException(
                    400,
                    String.join(
                            "；",
                            ((List<String>) checked.get("errors")).stream().limit(10).toList()));
        String period = period(b.get("period")), kind = text(b.get("kind"));
        String canonical = d.json(checked.get("rows"));
        String hash;
        try {
            hash =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    canonical.getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        var existing =
                d.rows(
                        "SELECT * FROM oa_hr_attendance_batch WHERE period=? AND kind=? AND"
                                + " source_hash=?",
                        period,
                        kind,
                        hash);
        if (!existing.isEmpty()) return detail(id(existing.get(0).get("id")), false);
        long file = saveFile(null, null, b);
        long batch =
                d.insert(
                        "INSERT INTO"
                            + " oa_hr_attendance_batch(period,kind,file_id,source_hash,create_by)"
                            + " VALUES(?,?,?,?,?)",
                        period,
                        kind,
                        file,
                        hash,
                        AuthContext.userId());
        for (var r : (List<Map<String, Object>>) checked.get("rows"))
            d.insert(
                    "INSERT INTO oa_hr_attendance_row(batch_id,user_id,work_date,data_json)"
                            + " VALUES(?,?,?,?)",
                    batch,
                    r.get("userId"),
                    r.get("workDate"),
                    d.json(r));
        d.history(
                "attendance",
                batch,
                null,
                "导入钉钉考勤",
                Map.of(
                        "period",
                        period,
                        "kind",
                        kind,
                        "rows",
                        ((List<?>) checked.get("rows")).size()));
        return detail(batch, false);
    }

    long saveFile(Long user, Long payroll, Map<String, Object> b) {
        String name = required(b.get("fileName"), "文件名", 255),
                content = required(b.get("fileContent"), "原始文件", 14_000_000);
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(content);
        } catch (Exception ex) {
            throw new ApiException(400, "文件内容无效");
        }
        if (bytes.length == 0 || bytes.length > 10 * 1024 * 1024)
            throw new ApiException(400, "文件大小须在10MB以内");
        return d.insert(
                "INSERT INTO"
                        + " oa_hr_attachment(user_id,payroll_id,file_name,content_base64,create_by)"
                        + " VALUES(?,?,?,?,?)",
                user,
                payroll,
                name,
                content,
                AuthContext.userId());
    }

    public List<Map<String, Object>> batches(Map<String, String> q) {
        d.require(ATTENDANCE, "access");
        return d
                .rows(
                        "SELECT"
                            + " b.id,b.period,b.kind,b.status,b.revision,b.file_id,f.file_name,b.create_time"
                            + " FROM oa_hr_attendance_batch b JOIN oa_hr_attachment f ON"
                            + " f.id=b.file_id ORDER BY b.id DESC")
                .stream()
                .filter(
                        b ->
                                text(q.get("period")).isEmpty()
                                        || q.get("period").equals(b.get("period")))
                .filter(b -> text(q.get("kind")).isEmpty() || q.get("kind").equals(b.get("kind")))
                .filter(b -> visibleRows(id(b.get("id")), false).size() > 0)
                .toList();
    }

    List<Map<String, Object>> visibleRows(long batch, boolean self) {
        return d
                .rows(
                        "SELECT r.user_id,r.data_json,u.dept_id FROM oa_hr_attendance_row r JOIN"
                                + " sys_user u ON u.user_id=r.user_id WHERE r.batch_id=? ORDER BY"
                                + " r.user_id,r.work_date,r.id",
                        batch)
                .stream()
                .filter(
                        r ->
                                self
                                        ? id(r.get("userId")) == AuthContext.userId()
                                        : d.scope.canAccess(
                                                AuthContext.userId(),
                                                r.get("deptId"),
                                                r.get("userId")))
                .map(r -> d.object(r.get("dataJson")))
                .toList();
    }

    public Map<String, Object> detail(long batch, boolean self) {
        d.require(self ? SELF : ATTENDANCE, "access");
        var row =
                new LinkedHashMap<>(
                        d.one(
                                "SELECT id,period,kind,status,revision,file_id,create_time FROM"
                                        + " oa_hr_attendance_batch WHERE id=?",
                                batch));
        if (self && !Set.of("confirmed", "superseded").contains(text(row.get("status"))))
            throw new ApiException(403, "考勤尚未确认");
        var records = visibleRows(batch, self);
        if (records.isEmpty()) throw new ApiException(403, "没有可查看的员工数据");
        row.put("rows", records);
        if (self) row.remove("fileId");
        return row;
    }

    @Transactional
    public void confirm(long batch, Map<String, Object> b) {
        d.require(ATTENDANCE, "confirm");
        var current = d.one("SELECT * FROM oa_hr_attendance_batch WHERE id=? FOR UPDATE", batch);
        d.revision(current, b);
        d.expect(current, "draft");
        var users =
                d.jdbc.queryForList(
                        "SELECT user_id FROM oa_hr_attendance_row WHERE batch_id=?",
                        Long.class,
                        batch);
        users.forEach(d::employee);
        // Serialize confirmation of overlapping monthly versions.
        d.jdbc.queryForList("SELECT user_id FROM oa_hr_employee ORDER BY user_id FOR UPDATE");
        for (var old :
                d.rows(
                        "SELECT id FROM oa_hr_attendance_batch WHERE period=? AND kind=? AND"
                                + " status='confirmed' AND id<>?",
                        current.get("period"),
                        current.get("kind"),
                        batch)) {
            var oldUsers =
                    d.jdbc.queryForList(
                            "SELECT user_id FROM oa_hr_attendance_row WHERE batch_id=?",
                            Long.class,
                            old.get("id"));
            if (Collections.disjoint(users, oldUsers)) continue;
            if (!new HashSet<>(users).containsAll(oldUsers))
                throw new ApiException(409, "重新导入须覆盖被替换批次的全部员工，避免遗漏");
            d.jdbc.update(
                    "UPDATE oa_hr_attendance_batch SET status='superseded',revision=revision+1"
                            + " WHERE id=?",
                    old.get("id"));
        }
        d.jdbc.update(
                "UPDATE oa_hr_attendance_batch SET"
                        + " status='confirmed',revision=revision+1,confirmed_by=? WHERE id=?",
                AuthContext.userId(),
                batch);
        d.history("attendance", batch, null, "确认考勤", b);
    }

    public List<Map<String, Object>> self() {
        d.require(SELF, "access");
        return d.rows(
                "SELECT DISTINCT b.id,b.period,b.kind,b.status,b.revision FROM"
                    + " oa_hr_attendance_batch b JOIN oa_hr_attendance_row r ON r.batch_id=b.id"
                    + " WHERE r.user_id=? AND b.status IN ('confirmed','superseded') ORDER BY b.id"
                    + " DESC",
                AuthContext.userId());
    }

    public Map<String, Object> file(long file) {
        var f = d.one("SELECT * FROM oa_hr_attachment WHERE id=?", file);
        long user = optionalId(f.get("userId")), payroll = optionalId(f.get("payrollId"));
        if (user > 0) {
            d.require(EMPLOYEES, "access");
            d.employee(user);
        } else if (payroll > 0) {
            if (!d.allowed(PAYROLL, "export") && !d.allowed(FINANCE, "pay"))
                throw new ApiException(403, "没有工资文件权限");
            var users =
                    d.jdbc.queryForList(
                            "SELECT user_id FROM oa_hr_payroll_item WHERE payroll_id=?",
                            Long.class,
                            payroll);
            users.forEach(d::employee);
        } else {
            d.require(ATTENDANCE, "export");
            var batch = d.one("SELECT id FROM oa_hr_attendance_batch WHERE file_id=?", file);
            d.jdbc.queryForList(
                            "SELECT user_id FROM oa_hr_attendance_row WHERE batch_id=?",
                            Long.class,
                            batch.get("id"))
                    .forEach(d::employee);
        }
        return Map.of("fileName", f.get("fileName"), "fileContent", f.get("contentBase64"));
    }
}
