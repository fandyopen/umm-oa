package com.seal.oa.hr;

import static com.seal.oa.hr.HrData.*;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.workflow.WorkflowActionContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;

@Service
public class HrPayrollService {
    private final HrData d;
    private final HrAttendanceService attendance;
    private final HrEmployeeService employees;
    private final HrPayrollRuleService ruleService;
    private static final List<String> ADJUSTABLE =
            List.of("bonus", "commission", "adjustment", "deduction", "tax", "contribution");
    private static final List<String> ACCOUNT_FIELDS = List.of("accountName", "bankName", "bankAccount");

    List<Map<String, Object>> accountChanges(long payroll) {
        var changes = new ArrayList<Map<String, Object>>();
        for (var item : d.rows("SELECT id,user_id,snapshot_json FROM oa_hr_payroll_item WHERE payroll_id=? ORDER BY id", payroll)) {
            var employee = d.employee(id(item.get("userId")));
            var snapshot = d.object(item.get("snapshotJson"));
            var before = new LinkedHashMap<String, Object>();
            var after = new LinkedHashMap<String, Object>();
            for (String field : ACCOUNT_FIELDS) {
                before.put(field, text(snapshot.get(field)));
                after.put(field, text(employee.get(field)));
            }
            if (before.equals(after)) continue;
            var change = new LinkedHashMap<String, Object>();
            change.put("itemId", item.get("id"));
            change.put("userId", item.get("userId"));
            change.put("employeeNo", employee.get("employeeNo"));
            change.put("employeeName", employee.get("nickName"));
            change.put("employeeRevision", employee.get("revision"));
            change.put("before", before);
            change.put("after", after);
            changes.add(change);
        }
        return changes;
    }

    String accountChangesToken(List<Map<String, Object>> changes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(d.json(changes).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Transactional
    public Map<String, Object> refreshAccounts(long payroll, Map<String, Object> body) {
        d.require(PAYROLL, "edit");
        var b = batch(payroll, true);
        d.revision(b, body);
        d.expect(b, "draft", "rejected");
        access(payroll);
        // Keep the confirmed preview stable while employee account updates are committed.
        d.jdbc.queryForList("SELECT e.user_id FROM oa_hr_employee e JOIN oa_hr_payroll_item i"
                + " ON i.user_id=e.user_id WHERE i.payroll_id=? ORDER BY e.user_id FOR UPDATE", Long.class, payroll);
        var changes = accountChanges(payroll);
        if (changes.isEmpty()) return Map.of("updatedCount", 0);
        if (!accountChangesToken(changes).equals(text(body.get("changesToken"))))
            throw new ApiException(409, "收款账户预览缺失或已变化，请重新打开同步窗口确认");
        for (var change : changes) {
            var item = d.one("SELECT snapshot_json FROM oa_hr_payroll_item WHERE id=?", change.get("itemId"));
            var snapshot = d.object(item.get("snapshotJson"));
            snapshot.putAll(d.object(change.get("after")));
            d.jdbc.update(
                    "UPDATE oa_hr_payroll_item SET snapshot_json=?,verified=0 WHERE id=?",
                    d.json(snapshot),
                    change.get("itemId"));
        }
        d.jdbc.update("UPDATE oa_hr_payroll SET revision=revision+1 WHERE id=?", payroll);
        d.history("payroll", payroll, null, "同步最新收款账户", Map.of("changes", changes));
        return Map.of("updatedCount", changes.size());
    }

    public HrPayrollService(HrData d, HrAttendanceService attendance, HrEmployeeService employees, HrPayrollRuleService ruleService) {
        this.d = d;
        this.attendance = attendance;
        this.employees = employees;
        this.ruleService = ruleService;
    }

    @Transactional
    public long createCommissionPayroll(String period, long commissionBatch, Map<Long, BigDecimal> amounts) {
        d.require(PAYROLL, "generate");
        long batch = d.insert("INSERT INTO oa_hr_payroll(period,batch_type,rules_json,reason,create_by) VALUES(?,'commission','{}',?,?)",
                period(period), "提成核算 #" + commissionBatch, AuthContext.userId());
        for (var entry : amounts.entrySet()) {
            var employee = d.employee(entry.getKey());
            var snapshot = new LinkedHashMap<String, Object>();
            for (String field : List.of("employeeNo", "nickName", "deptName", "accountName", "bankName", "bankAccount"))
                snapshot.put(field, employee.get(field));
            snapshot.put("commissionBatchId", commissionBatch);
            BigDecimal amount = entry.getValue();
            d.insert("""
                    INSERT INTO oa_hr_payroll_item(payroll_id,user_id,snapshot_json,base_amount,allowance,commission,adjustment,gross_amount,net_amount,reason)
                    VALUES(?,?,?,0,0,?,?,?,?,?)
                    """,batch,entry.getKey(),d.json(snapshot),amount.max(BigDecimal.ZERO),amount.min(BigDecimal.ZERO),
                    amount,amount,"关联提成核算 #" + commissionBatch);
        }
        d.history("payroll",batch,null,"接收已批准提成",Map.of("commissionBatchId",commissionBatch));
        return batch;
    }

    Map<String, Object> batch(long id, boolean lock) {
        return d.one("SELECT * FROM oa_hr_payroll WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
    }

    void access(long id) {
        d.jdbc.queryForList(
                        "SELECT user_id FROM oa_hr_payroll_item WHERE payroll_id=?", Long.class, id)
                .forEach(d::employee);
    }

    void readAccess(boolean finance) {
        d.require(finance ? FINANCE : PAYROLL, "access");
    }

    public List<Map<String, Object>> attendanceOptions(String period) {
        d.require(PAYROLL, "generate");
        return d
                .rows(
                        "SELECT id,period,status FROM oa_hr_attendance_batch WHERE period=? AND"
                                + " kind='monthly' AND status='confirmed' ORDER BY id DESC",
                        period(period))
                .stream()
                .filter(
                        row -> {
                            try {
                                var members = attendance.memberUserIds(id(row.get("id")));
                                if (members.isEmpty()) return false;
                                members.forEach(d::employee);
                                return true;
                            } catch (ApiException error) {
                                return false;
                            }
                        })
                .toList();
    }

    public List<Map<String, Object>> list(Map<String, String> q, boolean finance) {
        readAccess(finance);
        var result = new ArrayList<Map<String, Object>>();
        for (var b :
                d.rows(
                        "SELECT p.*,COALESCE(SUM(i.net_amount),0) AS net_amount,COUNT(i.id) AS"
                            + " employee_count FROM oa_hr_payroll p LEFT JOIN oa_hr_payroll_item i"
                            + " ON i.payroll_id=p.id GROUP BY p.id ORDER BY p.period DESC,p.create_time DESC,p.id DESC")) {
            if (!text(q.get("period")).isEmpty() && !q.get("period").equals(b.get("period")))
                continue;
            if (finance && !"approved".equals(b.get("status"))) continue;
            try {
                access(id(b.get("id")));
            } catch (ApiException ex) {
                continue;
            }
            b.remove("rulesJson");
            b.put("paidAmount", paidForBatch(id(b.get("id"))));
            result.add(b);
        }
        return result;
    }

    public Map<String, Object> detail(long id, boolean finance) {
        readAccess(finance);
        access(id);
        var b = new LinkedHashMap<>(batch(id, false));
        if (finance && !"approved".equals(b.get("status")))
            throw new ApiException(403, "仅批准的工资可以进入财务发放");
        var items = items(id);
        b.put("rows", items);
        if (!finance && Set.of("draft", "rejected").contains(text(b.get("status"))) && d.allowed(PAYROLL, "edit")) {
            var changes = accountChanges(id);
            b.put("accountChanges", changes);
            b.put("accountChangesToken", accountChangesToken(changes));
        }
        b.put("rules", d.object(b.remove("rulesJson")));
        b.put("rulesChanged", "regular".equals(b.get("batchType")) && ruleService.changed(text(b.get("period")), d.object(b.get("rules"))));
        b.put(
                "history",
                d.rows(
                        "SELECT action,operator_id,create_time,detail_json FROM oa_hr_history WHERE"
                                + " entity_type='payroll' AND entity_id=? ORDER BY id DESC",
                        id).stream().map(h -> {
                            h.put("detail", d.object(h.remove("detailJson")));
                            return h;
                        }).toList());
        b.put("attendanceChanged", attendanceChanged(b));
        b.put("sourcesChanged", sourcesChanged(b, items));
        b.put(
                "netAmount",
                items.stream()
                        .map(r -> number(r.get("netAmount"), "实发"))
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        b.put("paidAmount", paidForBatch(id));
        return b;
    }

    List<Map<String, Object>> items(long batch) {
        return d
                .rows("SELECT * FROM oa_hr_payroll_item WHERE payroll_id=? ORDER BY id", batch)
                .stream()
                .map(
                        r -> {
                            r.put("snapshot", d.object(r.remove("snapshotJson")));
                            r.put("paidAmount", paid(id(r.get("id"))));
                            r.put("commissionSources", commissionSources(id(r.get("id"))));
                            r.put(
                                    "payments",
                                    d.rows(
                                            "SELECT"
                                                + " id,amount,payment_date,status,reference_no,attachment_id,reason,operator_id,create_time"
                                                + " FROM oa_hr_payment WHERE item_id=? ORDER BY id",
                                            r.get("id")));
                            return r;
                        })
                .toList();
    }

    BigDecimal paid(long item) {
        return d.jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM oa_hr_payment WHERE item_id=? AND"
                        + " status='paid'",
                BigDecimal.class,
                item);
    }

    BigDecimal paidForBatch(long batch) {
        return d.jdbc.queryForObject(
                "SELECT COALESCE(SUM(p.amount),0) FROM oa_hr_payment p JOIN oa_hr_payroll_item i ON"
                        + " i.id=p.item_id WHERE i.payroll_id=? AND p.status='paid'",
                BigDecimal.class,
                batch);
    }

    boolean attendanceChanged(Map<String, Object> batch) {
        return optionalId(batch.get("attendanceId")) > 0
                && !"confirmed"
                        .equals(
                                d.one(
                                                "SELECT status FROM oa_hr_attendance_batch WHERE"
                                                        + " id=?",
                                                batch.get("attendanceId"))
                                        .get("status"));
    }

    String sourceSignature(long user, String period) {
        return d.json(
                Map.of(
                        "salaryBasis", "monthly_latest_v1",
                        "salary",
                                d.effectiveSalaries(user).stream()
                                        .filter(s -> !date(s.get("effectiveDate")).isAfter(
                                                java.time.YearMonth.parse(period).atEndOfMonth()))
                                        .map(s -> Map.of("id", id(s.get("id")), "revision", id(s.get("revision"))))
                                        .toList(),
                        "daily",
                                d.rows(
                                        "SELECT DISTINCT b.id,b.revision FROM"
                                            + " oa_hr_attendance_batch b JOIN oa_hr_attendance_row"
                                            + " r ON r.batch_id=b.id WHERE b.kind='daily' AND"
                                            + " b.status='confirmed' AND b.period=? AND r.user_id=?"
                                            + " ORDER BY b.id",
                                        period,
                                        user),
                        "employment", employmentPeriods(user, period)));
    }

    List<Map<String, Object>> employmentPeriods(long user, String period) {
        var month = java.time.YearMonth.parse(period);
        return d.rows(
                "SELECT entry_date,exit_date FROM oa_hr_employment_period WHERE user_id=? AND"
                    + " entry_date<=? AND (exit_date IS NULL OR exit_date>=?) ORDER BY entry_date",
                user,
                month.atEndOfMonth(),
                month.atDay(1));
    }

    boolean sourcesChanged(Map<String, Object> batch, List<Map<String, Object>> rows) {
        if (!"regular".equals(batch.get("batchType"))) return false;
        return rows.stream()
                .anyMatch(
                        row ->
                                !sourceSignature(id(row.get("userId")), text(batch.get("period")))
                                        .equals(
                                                text(
                                                        d.object(row.get("snapshot"))
                                                                .get("sourceSignature"))));
    }

    @Transactional
    public Map<String, Object> generate(Map<String, Object> b) {
        d.require(PAYROLL, "generate");
        String period = period(b.get("period"));
        long source = id(b.get("attendanceId"));
        var a = d.one("SELECT * FROM oa_hr_attendance_batch WHERE id=? FOR UPDATE", source);
        d.expect(a, "confirmed");
        if (!period.equals(a.get("period")) || !"monthly".equals(a.get("kind")))
            throw new ApiException(400, "请选择本月已确认的月度考勤");
        var rules = ruleService.resolve(period, b);
        var rows = attendance.visibleRows(source, false);
        if (rows.size() != attendance.memberUserIds(source).size()) throw new ApiException(403, "工资批次包含超出权限的员工");
        if (rows.isEmpty()) throw new ApiException(400, "当前考勤没有可核算的员工");
        if (d.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM oa_hr_payroll WHERE regular_key=?",
                        Long.class,
                        period)
                > 0) throw new ApiException(409, "本月已有常规工资表，请重算原草稿或创建补发批次");
        long batch =
                d.insert(
                        "INSERT INTO"
                            + " oa_hr_payroll(period,batch_type,regular_key,attendance_id,rules_json,create_by)"
                            + " VALUES(?,'regular',?,?,?,?)",
                        period,
                        period,
                        source,
                        d.json(rules),
                        AuthContext.userId());
        fill(batch, period, rows, rules);
        d.history("payroll", batch, null, "生成工资草稿", Map.of("attendanceId", source, "rules", rules));
        return detail(batch, false);
    }

    void fill(
            long batch, String period, List<Map<String, Object>> rows, Map<String, Object> rules) {
        for (var a : rows) {
            long user = id(a.get("userId"));
            var e = d.employee(user);
            e.put("employmentPeriods", employmentPeriods(user, period));
            var salaries = d.effectiveSalaries(user);
            var daily =
                    d
                            .rows(
                                    "SELECT r.data_json FROM oa_hr_attendance_row r JOIN"
                                        + " oa_hr_attendance_batch b ON b.id=r.batch_id WHERE"
                                        + " b.period=? AND b.kind='daily' AND b.status='confirmed'"
                                        + " AND r.user_id=? ORDER BY r.work_date",
                                    period,
                                    user)
                            .stream()
                            .map(r -> d.object(r.get("dataJson")))
                            .toList();
            Map<String, Object> amounts;
            try {
                amounts = PayrollCalculator.calculate(period, e, a, salaries, daily, rules);
            } catch (ApiException ex) {
                throw new ApiException(
                        ex.code(),
                        text(e.get("employeeNo"))
                                + " "
                                + text(e.get("nickName"))
                                + "："
                                + ex.getMessage());
            }
            var snapshot = new LinkedHashMap<String, Object>();
            for (String key :
                    List.of(
                            "employeeNo",
                            "nickName",
                            "deptId",
                            "deptName",
                            "entryDate",
                            "exitDate",
                            "accountName",
                            "bankName",
                            "bankAccount")) snapshot.put(key, e.get(key));
            snapshot.put("attendance", a);
            snapshot.put("salaryVersions", salaries);
            snapshot.put("salaryBasis", "monthly_latest_v1");
            snapshot.put("segments", amounts.get("segments"));
            snapshot.put("sourceSignature", sourceSignature(user, period));
            snapshot.put("employmentPeriods", e.get("employmentPeriods"));
            BigDecimal gross =
                    number(amounts.get("baseAmount"), "基本工资")
                            .add(number(amounts.get("allowance"), "补贴"))
                            .add(number(amounts.get("overtime"), "加班工资"));
            BigDecimal net = gross.subtract(number(amounts.get("deduction"), "扣款"));
            if (net.signum() < 0) throw new ApiException(409, "核算实发为负数，请核对计薪规则");
            d.insert(
                    "INSERT INTO"
                        + " oa_hr_payroll_item(payroll_id,user_id,snapshot_json,base_amount,allowance,overtime,deduction,gross_amount,net_amount)"
                        + " VALUES(?,?,?,?,?,?,?,?,?)",
                    batch,
                    user,
                    d.json(snapshot),
                    amounts.get("baseAmount"),
                    amounts.get("allowance"),
                    amounts.get("overtime"),
                    amounts.get("deduction"),
                    money(gross),
                    money(net));
        }
    }

    @Transactional
    public Map<String, Object> recalculate(long batch, Map<String, Object> body) {
        d.require(PAYROLL, "generate");
        var b = batch(batch, true);
        d.revision(b, body);
        d.expect(b, "draft", "rejected");
        access(batch);
        if (!"regular".equals(b.get("batchType"))) throw new ApiException(409, "补发批次使用调整项维护");
        var previousItems = items(batch);
        if (previousItems.stream()
                        .anyMatch(
                                i ->
                                        !text(i.get("reason")).isEmpty()
                                                || optionalId(i.get("taxEntered")) != 0
                                                || optionalId(i.get("verified")) != 0
                                                || ADJUSTABLE.stream()
                                                        .anyMatch(
                                                                k ->
                                                                        optionalMoney(i.get(k))
                                                                                        .signum()
                                                                                != 0))
                && !Boolean.TRUE.equals(body.get("discardAdjustments")))
            throw new ApiException(409, "工资已有核对或调整，请导出留存并明确确认后重算");
        String recalcReason = required(body.get("reason"), "重算原因", 1000);
        long source = id(body.get("attendanceId"));
        var a = d.one("SELECT * FROM oa_hr_attendance_batch WHERE id=? FOR UPDATE", source);
        d.expect(a, "confirmed");
        if (!a.get("period").equals(b.get("period")) || !"monthly".equals(a.get("kind")))
            throw new ApiException(400, "考勤月份或类型不一致");
        var rows = attendance.visibleRows(source, false);
        if (rows.size() != attendance.memberUserIds(source).size()) throw new ApiException(403, "考勤包含越权员工");
        if (rows.isEmpty()) throw new ApiException(400, "当前考勤没有可核算的员工");
        var rules = ruleService.forRecalculation(text(b.get("period")), body, d.object(b.get("rulesJson")));
        d.history(
                "payroll",
                batch,
                null,
                "重算前工资快照",
                Map.of(
                        "revision",
                        b.get("revision"),
                        "rows",
                        previousItems,
                        "rules",
                        d.object(b.get("rulesJson")),
                        "reason",
                        recalcReason));
        d.jdbc.update("DELETE FROM oa_hr_payroll_item WHERE payroll_id=?", batch);
        fill(batch, text(b.get("period")), rows, rules);
        d.jdbc.update(
                "UPDATE oa_hr_payroll SET"
                    + " attendance_id=?,rules_json=?,status='draft',revision=revision+1 WHERE id=?",
                source,
                d.json(rules),
                batch);
        d.history("payroll", batch, null, "重新核算工资", body);
        return detail(batch, false);
    }

    @Transactional
    public Map<String, Object> supplement(Map<String, Object> b) {
        d.require(PAYROLL, "generate");
        String period = period(b.get("period")),
                reason = required(b.get("reason"), "补发或扣回原因", 1000);
        long batch =
                d.insert(
                        "INSERT INTO oa_hr_payroll(period,batch_type,rules_json,reason,create_by)"
                                + " VALUES(?,'supplement','{}',?,?)",
                        period,
                        reason,
                        AuthContext.userId());
        Set<Long> seen = new HashSet<>();
        for (var r : d.records(b.get("rows"))) {
            long user = employees.match(r);
            if (!seen.add(user)) throw new ApiException(400, "补发员工重复");
            var e = d.employee(user);
            var amount = currency(r.get("adjustment"), "补发或扣回金额");
            if (amount.signum() == 0) throw new ApiException(400, "差额不能为0");
            var snapshot = new LinkedHashMap<String, Object>();
            for (String key :
                    List.of(
                            "employeeNo",
                            "nickName",
                            "deptName",
                            "accountName",
                            "bankName",
                            "bankAccount")) snapshot.put(key, e.get(key));
            d.insert(
                    "INSERT INTO"
                        + " oa_hr_payroll_item(payroll_id,user_id,snapshot_json,base_amount,allowance,adjustment,gross_amount,net_amount,reason)"
                        + " VALUES(?,?,?,0,0,?,?,?,?)",
                    batch,
                    user,
                    d.json(snapshot),
                    money(amount),
                    money(amount),
                    money(amount),
                    reason);
        }
        d.history("payroll", batch, null, "创建差额批次", Map.of("reason", reason));
        return detail(batch, false);
    }

    public Map<String, Object> previewImport(long batch, Map<String, Object> body) {
        d.require(PAYROLL, "edit");
        var b = batch(batch, false);
        d.revision(b, body);
        d.expect(b, "draft", "rejected");
        access(batch);
        Set<Long> seen = new HashSet<>();
        List<Map<String, Object>> changes = new ArrayList<>();
        for (var input : d.records(body.get("rows"))) {
            if (id(input.get("batchId")) != batch
                    || !text(input.get("period")).equals(text(b.get("period")))
                    || id(input.get("revision")) != id(b.get("revision")))
                throw new ApiException(409, "文件批次、月份或版本不一致，请重新导出");
            long user = employees.match(input);
            if (!seen.add(user)) throw new ApiException(400, "文件包含重复员工");
            var old =
                    d.one(
                            "SELECT * FROM oa_hr_payroll_item WHERE payroll_id=? AND user_id=?",
                            batch,
                            user);
            var row = new LinkedHashMap<>(old);
            for (String key : ADJUSTABLE) row.put(key, currency(
                    "tax".equals(key) && text(input.get(key)).isEmpty() ? 0 : input.get(key), key));
            if ("commission".equals(b.get("batchType")))
                for (String key : List.of("commission", "adjustment", "bonus"))
                    if (number(row.get(key), key).compareTo(number(old.get(key), key)) != 0)
                        throw new ApiException(409, "关联提成金额已锁定，请从提成核算生成新的差额批次；个税等项目仍需核对");
            for (String key : List.of("bonus", "commission", "deduction", "tax", "contribution"))
                if (number(row.get(key), key).signum() < 0)
                    throw new ApiException(400, "奖金、提成及扣款项目须非负，扣回使用差额批次");
            for (String key : List.of("baseAmount", "allowance", "overtime"))
                if (input.containsKey(key)
                        && number(input.get(key), key).compareTo(number(old.get(key), key)) != 0)
                    throw new ApiException(400, "基础核算项不能回传覆盖，请通过本期调整或重新核算处理");
            String reason = required(input.get("reason"), "本期核对或调整说明", 1000);
            row.put("reason", reason);
            row.put("taxEntered", 1);
            totals(row);
            if (number(row.get("netAmount"), "实发").signum() < 0
                    && ("regular".equals(b.get("batchType")) || number(row.get("tax"), "个税").signum() != 0))
                throw new ApiException(400, "个税不能导致实发金额为负，扣回批次仅可填写0元个税");
            var diff = new LinkedHashMap<String, Object>();
            for (String key : ADJUSTABLE)
                if (number(old.get(key), key).compareTo(number(row.get(key), key)) != 0)
                    diff.put(key, Map.of("before", old.get(key), "after", row.get(key)));
            row.put("changes", diff);
            row.put("employeeNo", input.get("employeeNo"));
            row.put("employeeName", input.get("employeeName"));
            changes.add(row);
        }
        return Map.of("rows", changes, "revision", b.get("revision"));
    }

    @Transactional
    public void importRows(long batch, Map<String, Object> body) {
        var b = batch(batch, true);
        d.revision(b, body);
        var preview = previewImport(batch, body);
        for (var r : (List<Map<String, Object>>) preview.get("rows"))
            d.jdbc.update(
                    "UPDATE oa_hr_payroll_item SET"
                        + " bonus=?,commission=?,adjustment=?,deduction=?,tax=?,contribution=?,gross_amount=?,net_amount=?,reason=?,verified=1,tax_entered=1,tax_reason=?,tax_operator_id=?,tax_update_time=CURRENT_TIMESTAMP"
                        + " WHERE id=?",
                    r.get("bonus"),
                    r.get("commission"),
                    r.get("adjustment"),
                    r.get("deduction"),
                    r.get("tax"),
                    r.get("contribution"),
                    r.get("grossAmount"),
                    r.get("netAmount"),
                    r.get("reason"),
                    r.get("reason"),
                    AuthContext.userId(),
                    r.get("id"));
        d.jdbc.update("UPDATE oa_hr_payroll SET revision=revision+1 WHERE id=?", batch);
        d.history("payroll", batch, null, "回传工资调整", preview);
    }

    void totals(Map<String, Object> row) {
        BigDecimal gross = BigDecimal.ZERO;
        for (String key :
                List.of("baseAmount", "allowance", "overtime", "bonus", "commission", "adjustment"))
            gross = gross.add(optionalMoney(row.get(key)));
        BigDecimal net = gross;
        for (String key : List.of("deduction", "tax", "contribution"))
            net = net.subtract(optionalMoney(row.get(key)));
        row.put("grossAmount", money(gross));
        row.put("netAmount", money(net));
    }

    @Transactional
    public Map<String, Object> saveTax(long payroll, long item, Map<String, Object> body) {
        d.require(PAYROLL, "edit");
        var b = batch(payroll, true);
        d.revision(b, body);
        d.expect(b, "draft", "rejected");
        access(payroll);
        var row = d.one("SELECT * FROM oa_hr_payroll_item WHERE payroll_id=? AND id=? FOR UPDATE", payroll, item);
        BigDecimal tax = currency(text(body.get("tax")).isEmpty() ? 0 : body.get("tax"), "个税");
        if (tax.signum() < 0) throw new ApiException(400, "个税不能为负数");
        String reason = limited(body.get("reason"), 1000);
        var before = new LinkedHashMap<>(row);
        row.put("tax", tax);
        totals(row);
        BigDecimal net = number(row.get("netAmount"), "实发");
        if (net.signum() < 0 && ("regular".equals(b.get("batchType")) || tax.signum() != 0))
            throw new ApiException(400, "个税不能导致实发金额为负，扣回批次仅可填写0元个税");
        d.jdbc.update("""
                UPDATE oa_hr_payroll_item SET tax=?,tax_entered=1,tax_reason=?,tax_operator_id=?,
                tax_update_time=CURRENT_TIMESTAMP,net_amount=?,verified=0 WHERE id=?
                """, tax, reason, AuthContext.userId(), row.get("netAmount"), item);
        d.jdbc.update("UPDATE oa_hr_payroll SET revision=revision+1 WHERE id=?", payroll);
        d.history("payroll", payroll, id(row.get("userId")), "填写或修改个税",
                Map.of("itemId", item, "beforeTax", before.get("tax"),
                        "beforeEntered", before.get("taxEntered"), "tax", tax,
                        "beforeNetAmount", before.get("netAmount"), "netAmount", row.get("netAmount"), "reason", reason));
        return detail(payroll, false);
    }

    @Transactional
    public void verify(long batch, Map<String, Object> b) {
        d.require(PAYROLL, "edit");
        var row = batch(batch, true);
        d.revision(row, b);
        d.expect(row, "draft", "rejected");
        access(batch);
        String reason = required(b.get("reason"), "个税、个人扣款与调整项目核对说明", 1000);
        d.jdbc.update("UPDATE oa_hr_payroll_item SET verified=1 WHERE payroll_id=?", batch);
        d.jdbc.update("UPDATE oa_hr_payroll SET revision=revision+1 WHERE id=?", batch);
        d.history("payroll", batch, null, "完成逐项核对", Map.of("reason", reason));
    }

    public void validateWorkflow(WorkflowActionContext ctx) {
        if (ctx.moduleId() != PAYROLL || !"submit".equals(ctx.actionCode()))
            throw new ApiException(400, "工资审批动作无效");
        long batch = id(ctx.payload().get("payrollId"));
        var b = batch(batch, false);
        access(batch);
        if (ctx.requestId() == 0) {
            d.revision(b, ctx.payload());
            d.expect(b, "draft", "rejected");
            if (!accountChanges(batch).isEmpty())
                throw new ApiException(409, "员工收款账户已变更，请先同步最新收款账户并重新核对");
        } else if (optionalId(b.get("workflowRequestId")) != ctx.requestId()
                || !"approving".equals(b.get("status"))) throw new ApiException(409, "工资审批版本已变化");
        if (attendanceChanged(b)) throw new ApiException(409, "考勤已有新版本，请重新核算工资后提交");
        if (sourcesChanged(b, items(batch)))
            throw new ApiException(409, "薪资标准、任职日期或每日考勤已有变化，请重新核算工资");
        if (d.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM oa_hr_payroll_item WHERE payroll_id=? AND verified=0",
                        Long.class,
                        batch)
                > 0) throw new ApiException(409, "请先核对个税、个人扣款和本期调整项目");
        for (var item : items(batch)) {
            var s = (Map<String, Object>) item.get("snapshot");
            if (text(s.get("accountName")).isEmpty()
                    || text(s.get("bankName")).isEmpty()
                    || text(s.get("bankAccount")).isEmpty())
                throw new ApiException(409, "员工发薪账户不完整，请完善档案并更新工资快照");
        }
    }

    @Transactional
    public void submitted(WorkflowActionContext ctx) {
        validateWorkflow(
                new WorkflowActionContext(
                        0,
                        ctx.moduleId(),
                        ctx.actionCode(),
                        ctx.handlerKey(),
                        ctx.config(),
                        ctx.payload(),
                        ctx.operatorId(),
                        ctx.operatorName()));
        d.jdbc.update(
                "UPDATE oa_hr_payroll SET"
                    + " status='approving',workflow_request_id=?,revision=revision+1 WHERE id=?",
                ctx.requestId(),
                ctx.payload().get("payrollId"));
    }

    @Transactional
    public Object approved(WorkflowActionContext ctx) {
        validateWorkflow(ctx);
        long batch = id(ctx.payload().get("payrollId"));
        d.jdbc.update(
                "UPDATE oa_hr_payroll SET status='approved',revision=revision+1 WHERE id=?", batch);
        d.history("payroll", batch, null, "工资审批通过", Map.of("requestId", ctx.requestId()));
        var payers =
                d.jdbc.queryForList(
                        "SELECT DISTINCT u.user_id FROM sys_user u WHERE u.del_flag='0' AND"
                                + " u.status='0'",
                        Long.class);
        if (items(batch).stream()
                .allMatch(item -> number(item.get("netAmount"), "实发").signum() == 0))
            return Map.of("payrollId", batch, "status", "approved");
        for (Long user : payers)
            if (d.system.hasModuleAction(user, FINANCE, "pay")
                    || d.system.hasModuleAction(user, PAYROLL, "pay"))
                d.todo(
                        user,
                        "工资待发放：" + batch(batch, false).get("period"),
                        "hrPayroll",
                        batch,
                        "hr-payroll-" + batch);
        return Map.of("payrollId", batch, "status", "approved");
    }

    @Transactional
    public void rejected(WorkflowActionContext ctx, String state) {
        if (!Set.of("审批未通过", "rejected", "cancelled", "canceled", "withdrawn").contains(state))
            return;
        var b = batch(id(ctx.payload().get("payrollId")), true);
        if (optionalId(b.get("workflowRequestId")) != ctx.requestId()) return;
        d.jdbc.update(
                "UPDATE oa_hr_payroll SET status='rejected',revision=revision+1 WHERE id=?",
                b.get("id"));
        d.history("payroll", id(b.get("id")), null, "工资审批退回", Map.of("status", state));
    }

    @Transactional
    public void reopen(long batch, Map<String, Object> body) {
        d.require(PAYROLL, "edit");
        var b = batch(batch, true);
        d.revision(b, body);
        d.expect(b, "approved");
        access(batch);
        if (d.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM oa_hr_payment p JOIN oa_hr_payroll_item i ON"
                                + " i.id=p.item_id WHERE i.payroll_id=? AND p.status='paid'",
                        Long.class,
                        batch)
                > 0) throw new ApiException(409, "已有发放结果，请使用差额批次更正");
        d.jdbc.update(
                "UPDATE oa_hr_payroll SET"
                        + " status='draft',workflow_request_id=NULL,published=0,revision=revision+1"
                        + " WHERE id=?",
                batch);
        d.jdbc.update("UPDATE oa_hr_payroll_item SET verified=0 WHERE payroll_id=?", batch);
        d.closeTodos("hr-payroll-" + batch);
        d.history(
                "payroll",
                batch,
                null,
                "撤销批准并重新核对",
                Map.of("reason", required(body.get("reason"), "更正原因", 1000)));
    }

    @Transactional
    public void publish(long batch, Map<String, Object> body) {
        d.require(PAYROLL, "publish");
        var b = batch(batch, true);
        d.expect(b, "approved");
        access(batch);
        if (optionalId(b.get("published")) == 1) return;
        d.revision(b, body);
        d.jdbc.update("UPDATE oa_hr_payroll SET published=1,revision=revision+1 WHERE id=?", batch);
        d.history("payroll", batch, null, "发布工资条", Map.of());
        for (var item : items(batch))
            d.todo(
                    id(item.get("userId")),
                    "工资条已发布：" + b.get("period"),
                    "hrPayslip",
                    id(item.get("id")),
                    "hr-payslip-" + item.get("id"));
    }

    @Transactional
    public Map<String, Object> pay(long item, Map<String, Object> body) {
        return recordPayment(item, body, null);
    }

    @Transactional
    public Map<String, Object> payBatch(long payroll, Map<String, Object> body) {
        requirePaymentPermission();
        var batch = batch(payroll, true);
        d.expect(batch, "approved");
        d.revision(batch, body);
        access(payroll);
        var rows = d.records(body.get("rows"));
        if (rows.isEmpty() || rows.size() > 500) throw new ApiException(400, "每次请选择1至500名员工登记发放");
        Set<Long> seen = new HashSet<>();
        Set<String> tokens = new HashSet<>();
        // Validate membership before recording any payment, including idempotent retries.
        for (var row : rows) {
            long item = id(row.get("itemId"));
            if (!seen.add(item)) throw new ApiException(400, "同一员工工资明细不能重复选择");
            if (!tokens.add(required(row.get("requestToken"), "付款幂等标识", 64)))
                throw new ApiException(400, "每条发放记录必须使用不同的付款幂等标识");
            if (id(d.one("SELECT payroll_id FROM oa_hr_payroll_item WHERE id=?", item).get("payrollId")) != payroll)
                throw new ApiException(400, "所选员工不属于当前工资批次");
        }
        Long[] sharedAttachment = new Long[1];
        List<Map<String, Object>> payments = new ArrayList<>();
        for (var row : rows) {
            var payment = new LinkedHashMap<String, Object>(body);
            payment.remove("rows");
            payment.putAll(row);
            payments.add(recordPayment(id(row.get("itemId")), payment, sharedAttachment));
        }
        return Map.of("count", payments.size(), "payments", payments);
    }

    private void requirePaymentPermission() {
        if (!d.allowed(FINANCE, "pay") && !d.allowed(PAYROLL, "pay"))
            throw new ApiException(403, "没有工资发放权限");
    }

    private Map<String, Object> recordPayment(long item, Map<String, Object> body, Long[] sharedAttachment) {
        requirePaymentPermission();
        var initial = d.one("SELECT payroll_id FROM oa_hr_payroll_item WHERE id=?", item);
        long payroll = id(initial.get("payrollId"));
        var b = batch(payroll, true);
        d.expect(b, "approved");
        access(payroll);
        var row = d.one("SELECT * FROM oa_hr_payroll_item WHERE id=? FOR UPDATE", item);
        String token = required(body.get("requestToken"), "付款幂等标识", 64);
        var duplicated = d.rows("SELECT * FROM oa_hr_payment WHERE request_token=?", token);
        if (!duplicated.isEmpty()) {
            var existing = duplicated.get(0);
            if (id(existing.get("itemId")) != item
                    || number(existing.get("amount"), "金额")
                                    .compareTo(number(body.get("amount"), "金额"))
                            != 0
                    || !text(existing.get("status")).equals(text(body.get("status"))))
                throw new ApiException(409, "付款标识已用于其他结果");
            return existing;
        }
        BigDecimal amount = currency(body.get("amount"), "付款或扣回金额"),
                remaining = number(row.get("netAmount"), "实发").subtract(paid(item));
        if (amount.signum() == 0
                || amount.signum() != remaining.signum()
                || amount.abs().compareTo(remaining.abs()) > 0)
            throw new ApiException(409, "金额超过剩余应付或扣回金额");
        String status = required(body.get("status"), "付款结果", 16);
        if (!Set.of("paid", "failed").contains(status)) throw new ApiException(400, "付款结果无效");
        var day = date(body.get("paymentDate"));
        if (day.isAfter(LocalDate.now())) throw new ApiException(400, "实际付款日期不能是未来");
        String reference = required(body.get("referenceNo"), "银行流水号或失败单号", 128);
        if (d.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM oa_hr_payment WHERE reference_no=? AND status='paid'",
                        Long.class,
                        reference)
                > 0) throw new ApiException(409, "该付款流水已登记，不能重复发放");
        Long file = null;
        if ("paid".equals(status)) {
            if (sharedAttachment == null) file = attendance.saveFile(null, payroll, body);
            else {
                if (sharedAttachment[0] == null) sharedAttachment[0] = attendance.saveFile(null, payroll, body);
                file = sharedAttachment[0];
            }
        }
        else required(body.get("reason"), "失败原因", 1000);
        long payment =
                d.insert(
                        "INSERT INTO"
                            + " oa_hr_payment(item_id,request_token,amount,payment_date,status,reference_no,success_reference,attachment_id,reason,operator_id)"
                            + " VALUES(?,?,?,?,?,?,?,?,?,?)",
                        item,
                        token,
                        money(amount),
                        day,
                        status,
                        reference,
                        "paid".equals(status) ? reference : null,
                        file,
                        limited(body.get("reason"), 1000),
                        AuthContext.userId());
        d.history(
                "payroll",
                payroll,
                id(row.get("userId")),
                "paid".equals(status) ? "登记实际发放" : "登记发放失败",
                Map.of("paymentId", payment, "amount", amount, "referenceNo", reference));
        if (items(payroll).stream()
                .allMatch(
                        i ->
                                number(i.get("netAmount"), "实发")
                                                .compareTo(number(i.get("paidAmount"), "已付"))
                                        == 0)) d.closeTodos("hr-payroll-" + payroll);
        return d.one("SELECT id,item_id,amount,status FROM oa_hr_payment WHERE id=?", payment);
    }

    public List<Map<String, Object>> self() {
        d.require(SELF, "access");
        if (AuthContext.userId() == 1L || d.system.isSuperAdminIdentity(AuthContext.userId())) return List.of();
        return d
                .rows(
                        "SELECT i.*,p.period,p.batch_type,p.published FROM oa_hr_payroll_item i"
                                + " JOIN oa_hr_payroll p ON p.id=i.payroll_id WHERE i.user_id=? AND"
                                + " p.published=1 AND p.status='approved' ORDER BY p.period DESC,p.create_time DESC,p.id DESC",
                        AuthContext.userId())
                .stream()
                .map(
                        r -> {
                            var snapshot = d.object(r.remove("snapshotJson"));
                            r.put("employeeNo", snapshot.get("employeeNo"));
                            r.put("employeeName", snapshot.get("nickName"));
                            r.put("deptName", snapshot.get("deptName"));
                            r.putAll(payslipReadState(id(r.get("id")), AuthContext.userId()));
                            r.put("paidAmount", paid(id(r.get("id"))));
                            r.put("commissionSources", commissionSources(id(r.get("id"))));
                            return r;
                        })
                .toList();
    }

    @Transactional
    public Map<String, Object> acknowledge(long item) {
        d.require(SELF, "access");
        var row =
                d.one(
                        "SELECT i.id,i.payroll_id FROM oa_hr_payroll_item i JOIN oa_hr_payroll p ON"
                            + " p.id=i.payroll_id WHERE i.id=? AND i.user_id=? AND p.published=1 AND p.status='approved' FOR UPDATE",
                        item,
                        AuthContext.userId());
        var state = payslipReadState(item, AuthContext.userId());
        if (optionalId(state.get("viewed")) == 0) {
            d.history("payslip", item, AuthContext.userId(), "查看工资条", Map.of("payrollId", row.get("payrollId")));
        }
        d.jdbc.update("UPDATE oa_todo SET status='done' WHERE task_id=? AND user_id=? AND business_type='hrPayslip' AND business_id=? AND status='todo'",
                "hr-payslip-" + item, AuthContext.userId(), item);
        return payslipReadState(item, AuthContext.userId());
    }

    private Map<String, Object> payslipReadState(long item, long user) {
        var state = d.one("SELECT MIN(create_time) AS viewed_at FROM oa_hr_history WHERE entity_type='payslip' AND entity_id=? AND user_id=? AND action='查看工资条'", item, user);
        // Older versions only closed the employee's payslip notice; retain that state without inventing a timestamp.
        boolean legacyRead = state.get("viewedAt") == null && d.jdbc.queryForObject(
                "SELECT COUNT(*) FROM oa_todo WHERE task_id=? AND business_type='hrPayslip' AND business_id=? AND user_id=? AND status='done'",
                Long.class, "hr-payslip-" + item, item, user) > 0;
        state.put("id", item);
        state.put("viewed", state.get("viewedAt") != null || legacyRead ? 1 : 0);
        return state;
    }

    List<Map<String, Object>> commissionSources(long item) {
        return d.rows("""
                SELECT l.id,l.batch_id,l.amount,l.side,l.snapshot_json,b.period
                FROM oa_commission_line l JOIN oa_commission_batch b ON b.id=l.batch_id
                WHERE l.payroll_item_id=? ORDER BY l.id
                """, item).stream().map(r -> {
                    var snapshot=d.object(r.remove("snapshotJson"));
                    for (String field : List.of("customerName","jobTitle","talentName","holdReason","applicationId"))
                        r.put(field,snapshot.get(field));
                    return r;
                }).toList();
    }
}
