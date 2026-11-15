package com.seal.oa.hr;

import static com.seal.oa.hr.HrData.*;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.YearMonth;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
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
        return validate(b);
    }

    private Map<String, Object> validate(Map<String, Object> b) {
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
        d.require(ATTENDANCE, "import");
        return persist(b, "导入覆盖考勤", false);
    }

    @Transactional
    public Map<String, Object> update(long batch, Map<String, Object> b) {
        d.require(ATTENDANCE, "edit");
        var body = new LinkedHashMap<>(b);
        var base = d.one("SELECT period,kind FROM oa_hr_attendance_batch WHERE id=?", batch);
        body.put("period", base.get("period"));
        body.put("kind", base.get("kind"));
        body.put("baseBatchId", batch);
        return persist(body, "修改考勤", true);
    }

    @Transactional
    public Map<String, Object> generate(Map<String, Object> b) {
        d.require(ATTENDANCE, "generate");
        String period = period(b.get("period")), kind = text(b.getOrDefault("kind", "monthly"));
        if (!Set.of("monthly", "daily").contains(kind)) throw new ApiException(400, "正常考勤仅支持月度汇总或每日考勤");
        BigDecimal hours = nonnegative(b.getOrDefault("hoursPerDay", 8), "每日工时");
        if (hours.signum() == 0 || hours.compareTo(new BigDecimal("24")) > 0)
            throw new ApiException(400, "每日工时须大于0且不超过24小时");
        Set<Long> weekdays = new LinkedHashSet<>();
        Object days = b.getOrDefault("workdays", List.of(1, 2, 3, 4, 5));
        if (!(days instanceof List<?> values) || values.isEmpty()) throw new ApiException(400, "请选择每周工作日");
        for (Object value : (List<?>) days) {
            long day = id(value);
            if (day < 1 || day > 7) throw new ApiException(400, "工作日须在周一至周日之间");
            weekdays.add(day);
        }
        var month = YearMonth.parse(period);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var user : d.rows("""
                SELECT u.user_id,u.dept_id FROM sys_user u
                JOIN oa_hr_employee e ON e.user_id=u.user_id AND e.employment_status='active'
                WHERE u.del_flag='0' AND u.status='0' AND %s
                ORDER BY u.user_id
                """.formatted(d.memberCondition("u")))) {
            long uid = id(user.get("userId"));
            if (!d.scope.canAccess(AuthContext.userId(), user.get("deptId"), uid)) continue;
            var employee = d.employee(uid);
            var periods = d.rows("SELECT entry_date,exit_date FROM oa_hr_employment_period WHERE user_id=?", uid);
            if (month.atDay(1).datesUntil(month.plusMonths(1).atDay(1)).noneMatch(day -> employedOn(day, employee, periods))) continue;
            int required = 0, actual = 0;
            for (int n = 1; n <= month.lengthOfMonth(); n++) {
                var date = month.atDay(n);
                boolean working = weekdays.contains((long) date.getDayOfWeek().getValue());
                boolean employed = employedOn(date, employee, periods);
                if (working) required++;
                if (working && employed) actual++;
                if ("daily".equals(kind)) {
                    var row = normalRow(employee, DAY_FIELDS);
                    row.put("workDate", date.toString());
                    row.put("scheduledHours", working ? hours : BigDecimal.ZERO);
                    row.put("actualHours", working && employed ? hours : BigDecimal.ZERO);
                    row.put("result", !employed ? "非任职期间" : working ? "正常" : "休息");
                    rows.add(row);
                }
            }
            if ("monthly".equals(kind)) {
                var row = normalRow(employee, MONTH_FIELDS);
                row.put("requiredDays", required);
                row.put("actualDays", actual);
                rows.add(row);
            }
        }
        if (rows.isEmpty()) throw new ApiException(400, "本月没有可生成考勤的在职成员");
        var body = new LinkedHashMap<String, Object>();
        body.put("period", period);
        body.put("kind", kind);
        body.put("rows", rows);
        return persist(body, "生成正常考勤", true);
    }

    private Map<String, Object> normalRow(Map<String, Object> employee, List<String> fields) {
        var row = new LinkedHashMap<String, Object>();
        row.put("employeeNo", employee.get("employeeNo"));
        row.put("employeeName", employee.get("nickName"));
        for (String field : fields) row.put(field, 0);
        return row;
    }

    private boolean employedOn(LocalDate day, Map<String, Object> employee, List<Map<String, Object>> periods) {
        if (!periods.isEmpty()) return periods.stream().anyMatch(p ->
                !day.isBefore(date(p.get("entryDate"))) && (p.get("exitDate") == null || !day.isAfter(date(p.get("exitDate")))));
        LocalDate entry = nullableDate(employee.get("entryDate")), exit = nullableDate(employee.get("exitDate"));
        return (entry == null || !day.isBefore(entry)) && (exit == null || !day.isAfter(exit));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> persist(Map<String, Object> b, String action, boolean internalFile) {
        var checked = validate(b);
        if (!Boolean.TRUE.equals(checked.get("valid")))
            throw new ApiException(400, String.join("；", ((List<String>) checked.get("errors")).stream().limit(10).toList()));
        String period = period(b.get("period")), kind = text(b.get("kind"));
        long baseId = optionalId(b.get("baseBatchId"));
        Map<String, Object> base = null;
        List<Map<String, Object>> records = (List<Map<String, Object>>) checked.get("rows");
        if (baseId > 0) {
            base = d.one("SELECT * FROM oa_hr_attendance_batch WHERE id=? FOR UPDATE", baseId);
            d.revision(base, b);
            d.expect(base, "draft", "confirmed");
            if (!period.equals(base.get("period")) || !kind.equals(base.get("kind")))
                throw new ApiException(400, "覆盖批次的月份或类型不一致");
            var merged = new LinkedHashMap<String, Map<String, Object>>();
            for (var raw : d.rows("SELECT r.user_id,r.data_json FROM oa_hr_attendance_row r JOIN sys_user u ON u.user_id=r.user_id WHERE r.batch_id=? AND " + d.memberCondition("u") + " ORDER BY r.user_id,r.work_date,r.id", baseId)) {
                d.employee(id(raw.get("userId")));
                var row = d.object(raw.get("dataJson"));
                merged.put(rowKey(row, kind), row);
            }
            for (var row : records) {
                String key = rowKey(row, kind);
                if (internalFile && !merged.containsKey(key)) throw new ApiException(400, "只能修改当前批次已有员工的考勤记录");
                merged.put(key, row);
            }
            records = new ArrayList<>(merged.values());
        }
        if (records.size() > 5000) throw new ApiException(400, "每批最多5000条考勤记录，请缩小范围");
        String canonical = d.json(records);
        String hash, fingerprint = (base == null ? "" : baseId + ":" + base.get("revision") + ":") + canonical;
        while (true) {
            try {
                hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fingerprint.getBytes(StandardCharsets.UTF_8)));
            } catch (Exception ex) { throw new IllegalStateException(ex); }
            var existing = d.rows("SELECT id,status,revision FROM oa_hr_attendance_batch WHERE period=? AND kind=? AND source_hash=?", period, kind, hash);
            if (existing.isEmpty()) break;
            if (!"superseded".equals(existing.get(0).get("status"))) return detail(id(existing.get(0).get("id")), false);
            fingerprint += ":after:" + existing.get(0).get("id") + ":" + existing.get(0).get("revision");
        }
        var source = new LinkedHashMap<>(b);
        if (internalFile) {
            source.put("fileName", action + "-" + period + "-" + kind + ".json");
            source.put("fileContent", Base64.getEncoder().encodeToString(canonical.getBytes(StandardCharsets.UTF_8)));
        }
        long file = saveFile(null, null, source);
        long batch;
        if (base != null && "draft".equals(base.get("status"))) {
            batch = baseId;
            d.jdbc.update("UPDATE oa_hr_attendance_batch SET file_id=?,source_hash=?,revision=revision+1 WHERE id=?", file, hash, batch);
            d.jdbc.update("DELETE FROM oa_hr_attendance_row WHERE batch_id=?", batch);
        } else {
            batch = d.insert("INSERT INTO oa_hr_attendance_batch(period,kind,file_id,source_hash,create_by) VALUES(?,?,?,?,?)", period, kind, file, hash, AuthContext.userId());
        }
        for (var row : records) d.insert("INSERT INTO oa_hr_attendance_row(batch_id,user_id,work_date,data_json) VALUES(?,?,?,?)", batch, row.get("userId"), row.get("workDate"), d.json(row));
        d.history("attendance", batch, null, action, Map.of("period", period, "kind", kind, "rows", records.size(), "baseBatchId", baseId));
        return detail(batch, false);
    }

    private String rowKey(Map<String, Object> row, String kind) {
        return id(row.get("userId")) + "/" + ("daily".equals(kind) ? row.get("workDate") : "leave".equals(kind) ? row.get("leaveId") : "monthly");
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
                                + " sys_user u ON u.user_id=r.user_id WHERE r.batch_id=? AND " + d.memberCondition("u") + " ORDER BY"
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

    List<Long> memberUserIds(long batch) {
        return d.jdbc.queryForList("SELECT r.user_id FROM oa_hr_attendance_row r JOIN sys_user u ON u.user_id=r.user_id WHERE r.batch_id=? AND " + d.memberCondition("u"), Long.class, batch);
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
        var users = memberUserIds(batch);
        if (users.isEmpty()) throw new ApiException(400, "当前批次没有可确认的员工考勤");
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
            var oldUsers = memberUserIds(id(old.get("id")));
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
        if (AuthContext.userId() == 1L || d.system.isSuperAdminIdentity(AuthContext.userId())) return List.of();
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
            memberUserIds(id(batch.get("id"))).forEach(d::employee);
        }
        return Map.of("fileName", f.get("fileName"), "fileContent", f.get("contentBase64"));
    }
}
