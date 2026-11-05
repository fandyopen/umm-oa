package com.seal.oa.hr;

import static com.seal.oa.hr.HrData.*;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.oa.CrmContractService;
import com.seal.oa.oa.CustomerFollowupReminderService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.*;

@Service
public class HrEmployeeService {
    private final HrData d;
    private final CrmContractService contracts;
    private final CustomerFollowupReminderService reminders;
    private final TransactionTemplate transactions;

    public HrEmployeeService(
            HrData d,
            CrmContractService contracts,
            CustomerFollowupReminderService reminders,
            PlatformTransactionManager transactionManager) {
        this.d = d;
        this.contracts = contracts;
        this.reminders = reminders;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public void ensure(long user) {
        d.jdbc.update(
                "INSERT INTO oa_hr_employee(user_id,employee_no,employment_status) SELECT"
                    + " user_id,CONCAT('EMP',user_id),CASE WHEN del_flag='2' THEN 'departed' ELSE"
                    + " 'active' END FROM sys_user WHERE user_id=? AND NOT EXISTS(SELECT 1 FROM"
                    + " oa_hr_employee WHERE user_id=?)",
                user,
                user);
    }

    @Transactional
    public void memberCreated(long user) {
        ensure(user);
        d.history("employee", user, user, "新增成员，待补录任职档案", Map.of());
    }

    /** Existing member actions have their own permissions and approval flow. */
    @Transactional
    public void memberChanged(long user, String type, Map<String, Object> body, String reason) {
        ensure(user);
        var before = d.employee(user);
        if ("entry".equals(type) && before.get("entryDate") != null)
            throw new ApiException(409, "已有入职记录，不能重复入职");
        if ("regular".equals(type) && before.get("regularDate") != null)
            throw new ApiException(409, "已有转正记录");
        if (user == 1) throw new ApiException(403, "固定超级管理员不能离职或调整部门");
        if (!"0".equals(text(before.get("delFlag")))) throw new ApiException(409, "仅有效成员可办理此操作");
        if (d.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM oa_hr_event WHERE user_id=? AND status IN"
                                + " ('approving','scheduled')",
                        Long.class,
                        user)
                > 0) throw new ApiException(409, "员工存在审批中或待生效的异动，请先处理原记录");
        LocalDate effective = LocalDate.now();
        apply(user, type, effective, body);
        d.event(user, type, effective, reason, employmentSnapshot(before), body, "effective");
    }

    Map<String, Object> employmentSnapshot(Map<String, Object> employee) {
        var result = new LinkedHashMap<String, Object>();
        for (String key :
                List.of(
                        "deptId",
                        "deptName",
                        "entryDate",
                        "exitDate",
                        "regularDate",
                        "employmentStatus")) result.put(key, employee.get(key));
        return result;
    }

    @Transactional
    public Map<String, Object> accounts(long user, Map<String, Object> b) {
        d.require(EMPLOYEES, "salary_manage");
        var old = d.employee(user);
        d.revision(old, b);
        d.jdbc.update(
                "UPDATE oa_hr_employee SET"
                    + " account_name=?,bank_name=?,bank_account=?,revision=revision+1,update_time=CURRENT_TIMESTAMP"
                    + " WHERE user_id=?",
                limited(b.get("accountName"), 100),
                limited(b.get("bankName"), 200),
                limited(b.get("bankAccount"), 100),
                user);
        d.history("employee", user, user, "维护发薪账户", Map.of());
        return detail(user);
    }

    @Transactional
    public void memberRemoved(long user) {
        ensure(user);
        var before = d.employee(user);
        if ("2".equals(text(before.get("delFlag")))) {
            d.jdbc.update(
                    "UPDATE oa_hr_employee SET employment_status='departed',revision=revision+1"
                        + " WHERE user_id=?",
                    user);
            createHandover(user);
        } else if (!"departed".equals(before.get("employmentStatus")))
            memberChanged(user, "dismiss", Map.of(), "删除账号前办理离职与交接");
        else createHandover(user);
        d.history("employee", user, user, "移除成员账号，保留人事及薪资历史", Map.of());
    }

    public List<Map<String, Object>> list(Map<String, String> q) {
        d.require(EMPLOYEES, "access");
        List<Object> args = new ArrayList<>();
        String filter = d.scope.userFilter(AuthContext.userId(), args);
        return d
                .rows(
                        "SELECT"
                            + " u.user_id,u.nick_name,u.phonenumber,u.dept_id,d.dept_name,e.employee_no,e.entry_date,e.exit_date,e.regular_date,e.employment_status,e.revision"
                            + " FROM sys_user u JOIN oa_hr_employee e ON e.user_id=u.user_id LEFT"
                            + " JOIN sys_dept d ON d.dept_id=u.dept_id WHERE u.del_flag IN"
                            + " ('0','2')"
                                + filter
                                + " ORDER BY u.user_id",
                        args.toArray())
                .stream()
                .filter(
                        r ->
                                text(q.get("keyword")).isEmpty()
                                        || (text(r.get("nickName"))
                                                        + text(r.get("employeeNo"))
                                                        + text(r.get("phonenumber")))
                                                .contains(text(q.get("keyword"))))
                .filter(
                        r ->
                                text(q.get("status")).isEmpty()
                                        || text(q.get("status"))
                                                .equals(text(r.get("employmentStatus"))))
                .toList();
    }

    public Map<String, Object> detail(long user) {
        d.require(EMPLOYEES, "access");
        var row = new LinkedHashMap<>(d.employee(user));
        boolean sensitive =
                d.allowed(EMPLOYEES, "salary_view") || d.allowed(EMPLOYEES, "salary_manage");
        if (!sensitive) {
            row.remove("accountName");
            row.remove("bankName");
            row.remove("bankAccount");
        }
        row.put("salaryVisible", sensitive);
        row.put(
                "salaries",
                sensitive
                        ? d.rows(
                                "SELECT * FROM oa_hr_salary WHERE user_id=? ORDER BY effective_date"
                                        + " DESC",
                                user)
                        : List.of());
        row.put("events", events(user, false));
        row.put(
                "employmentPeriods",
                d.rows(
                        "SELECT entry_date,exit_date FROM oa_hr_employment_period WHERE user_id=?"
                                + " ORDER BY entry_date",
                        user));
        row.put(
                "attachments",
                d.rows(
                        "SELECT id,file_name,create_time FROM oa_hr_attachment WHERE user_id=? AND"
                                + " payroll_id IS NULL ORDER BY id DESC",
                        user));
        return row;
    }

    @Transactional
    public Map<String, Object> save(long user, Map<String, Object> b) {
        d.require(EMPLOYEES, "edit");
        var old = d.employee(user);
        d.revision(old, b);
        String no = required(b.get("employeeNo"), "员工编号", 64),
                ding = limited(b.get("dingtalkUserId"), 128);
        if (d.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM oa_hr_employee WHERE user_id<>? AND (employee_no=? OR"
                                + " (?<>'' AND dingtalk_user_id=?))",
                        Long.class,
                        user,
                        no,
                        ding,
                        ding)
                > 0) throw new ApiException(409, "工号或钉钉人员ID已绑定其他员工");
        LocalDate entry = nullableDate(b.get("entryDate")),
                regular = nullableDate(b.get("regularDate")),
                start = nullableDate(b.get("contractStart")),
                end = nullableDate(b.get("contractEnd"));
        if (entry != null && regular != null && regular.isBefore(entry)
                || start != null && end != null && end.isBefore(start))
            throw new ApiException(400, "日期先后顺序不正确");
        if (old.get("entryDate") != null && entry == null)
            throw new ApiException(400, "已登记的入职日期不能清空，请通过异动或更正处理");
        if (entry != null
                && old.get("exitDate") != null
                && entry.isAfter(date(old.get("exitDate"))))
            throw new ApiException(400, "入职日期不能晚于离职日期");
        if (entry != null) {
            if (d.jdbc.queryForObject(
                            "SELECT COUNT(*) FROM oa_hr_employment_period WHERE user_id=? AND"
                                    + " entry_date<>? AND (exit_date IS NULL OR exit_date>=?)",
                            Long.class,
                            user,
                            old.get("entryDate") == null ? entry : date(old.get("entryDate")),
                            entry)
                    > 0) throw new ApiException(409, "入职日期与已有任职期间重叠");
            int changed =
                    old.get("entryDate") == null
                            ? 0
                            : d.jdbc.update(
                                    "UPDATE oa_hr_employment_period SET entry_date=? WHERE"
                                            + " user_id=? AND entry_date=?",
                                    entry,
                                    user,
                                    date(old.get("entryDate")));
            if (changed == 0)
                d.insert(
                        "INSERT INTO oa_hr_employment_period(user_id,entry_date,exit_date)"
                                + " VALUES(?,?,?)",
                        user,
                        entry,
                        old.get("exitDate"));
        }
        d.jdbc.update(
                "UPDATE oa_hr_employee SET"
                    + " employee_no=?,dingtalk_user_id=?,entry_date=?,regular_date=?,employment_type=?,emergency_contact=?,emergency_phone=?,contract_start=?,contract_end=?,revision=revision+1,update_time=CURRENT_TIMESTAMP"
                    + " WHERE user_id=?",
                no,
                ding.isEmpty() ? null : ding,
                entry,
                regular,
                limited(b.get("employmentType"), 64),
                limited(b.get("emergencyContact"), 100),
                limited(b.get("emergencyPhone"), 32),
                start,
                end,
                user);
        if (d.allowed(EMPLOYEES, "salary_manage"))
            d.jdbc.update(
                    "UPDATE oa_hr_employee SET account_name=?,bank_name=?,bank_account=? WHERE"
                            + " user_id=?",
                    limited(b.get("accountName"), 100),
                    limited(b.get("bankName"), 200),
                    limited(b.get("bankAccount"), 100),
                    user);
        d.history(
                "employee",
                user,
                user,
                "修改档案",
                Map.of(
                        "employeeNo",
                        no,
                        "previousEntryDate",
                        text(old.get("entryDate")),
                        "entryDate",
                        text(entry)));
        return detail(user);
    }

    @Transactional
    public void importProfiles(Map<String, Object> b) {
        d.require(EMPLOYEES, "edit");
        Set<Long> seen = new HashSet<>();
        for (var row : d.records(b.get("rows"))) {
            long user = match(row);
            if (!seen.add(user)) throw new ApiException(400, "导入包含重复员工");
            var current = detail(user);
            current.putAll(row);
            save(user, current);
        }
    }

    long match(Map<String, Object> r) {
        String no = text(r.get("employeeNo")), ding = text(r.get("dingtalkUserId"));
        if (no.isEmpty() && ding.isEmpty())
            throw new ApiException(400, "必须提供员工编号或已绑定的钉钉人员ID，不能仅凭姓名匹配");
        var rows =
                d.rows(
                        "SELECT user_id,employee_no,dingtalk_user_id FROM oa_hr_employee WHERE"
                                + " (?<>'' AND employee_no=?) OR (?<>'' AND dingtalk_user_id=?)",
                        no,
                        no,
                        ding,
                        ding);
        if (rows.size() != 1) throw new ApiException(400, "无法唯一匹配员工：" + no + " " + ding);
        var row = rows.get(0);
        if (!no.isEmpty() && !no.equals(text(row.get("employeeNo")))
                || !ding.isEmpty() && !ding.equals(text(row.get("dingtalkUserId"))))
            throw new ApiException(400, "工号与钉钉人员ID不一致");
        long user = id(row.get("userId"));
        var employee = d.employee(user);
        if (!text(r.get("employeeName")).isEmpty()
                && !text(r.get("employeeName")).equals(text(employee.get("nickName"))))
            throw new ApiException(400, "姓名与员工编号不一致：" + no);
        return user;
    }

    @Transactional
    public Map<String, Object> salary(long user, Map<String, Object> b) {
        d.require(EMPLOYEES, "salary_manage");
        d.employee(user);
        LocalDate date = date(b.get("effectiveDate"));
        String type = required(b.get("payType"), "计薪方式", 16);
        if (!Set.of("monthly", "daily").contains(type))
            throw new ApiException(400, "计薪方式仅支持monthly或daily");
        var base = currency(b.get("baseAmount"), "工资标准");
        var allowance = currency(b.get("allowance"), "固定补贴");
        if (base.signum() < 0 || allowance.signum() < 0)
            throw new ApiException(400, "薪资标准与补贴不能为负数");
        long editId = optionalId(b.get("id"));
        if (editId > 0) {
            var previous =
                    d.one(
                            "SELECT * FROM oa_hr_salary WHERE id=? AND user_id=? FOR UPDATE",
                            editId,
                            user);
            d.expect(previous, "draft", "rejected");
            d.revision(previous, b);
        }
        if (d.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM oa_hr_salary WHERE user_id=? AND effective_date=? AND"
                                + " id<>?",
                        Long.class,
                        user,
                        date,
                        editId)
                > 0) throw new ApiException(409, "该生效日期已有薪资标准，历史记录不能覆盖");
        String reason = required(b.get("reason"), "定薪或调薪原因", 1000);
        if (editId > 0) {
            d.jdbc.update(
                    "UPDATE oa_hr_salary SET"
                        + " effective_date=?,pay_type=?,base_amount=?,allowance=?,reason=?,status='draft',workflow_request_id=NULL,revision=revision+1"
                        + " WHERE id=?",
                    date,
                    type,
                    base,
                    allowance,
                    reason,
                    editId);
            d.history("salary", editId, user, "修订薪资草稿", b);
            return d.one("SELECT id,revision,status FROM oa_hr_salary WHERE id=?", editId);
        }
        long salary =
                d.insert(
                        "INSERT INTO"
                            + " oa_hr_salary(user_id,effective_date,pay_type,base_amount,allowance,reason,operator_id)"
                            + " VALUES(?,?,?,?,?,?,?)",
                        user,
                        date,
                        type,
                        money(base),
                        money(allowance),
                        reason,
                        AuthContext.userId());
        d.history("salary", salary, user, "新增薪资标准", b);
        return d.one("SELECT id,revision,status FROM oa_hr_salary WHERE id=?", salary);
    }

    @Transactional
    public void importSalaries(Map<String, Object> b) {
        d.require(EMPLOYEES, "salary_manage");
        Set<String> seen = new HashSet<>();
        for (var row : d.records(b.get("rows"))) {
            long user = match(row);
            if (!seen.add(user + "/" + text(row.get("effectiveDate"))))
                throw new ApiException(400, "薪资标准重复");
            salary(user, row);
        }
    }

    public List<Map<String, Object>> events(long user, boolean salary) {
        return d.rows(
                "SELECT id,user_id,event_type,effective_date,reason,status,operator_id,create_time"
                        + " FROM oa_hr_event WHERE user_id=? ORDER BY effective_date DESC,id DESC",
                user);
    }

    public List<Map<String, Object>> changes() {
        d.require(CHANGES, "access");
        return d
                .rows(
                        "SELECT e.*,u.nick_name,p.employee_no,o.nick_name AS operator_name FROM"
                                + " oa_hr_event e JOIN sys_user u ON u.user_id=e.user_id JOIN"
                                + " oa_hr_employee p ON p.user_id=e.user_id LEFT JOIN sys_user o ON"
                                + " o.user_id=e.operator_id ORDER BY e.id DESC")
                .stream()
                .filter(
                        r ->
                                d.scope.canAccess(
                                        AuthContext.userId(),
                                        d.one(
                                                        "SELECT dept_id FROM sys_user WHERE"
                                                                + " user_id=?",
                                                        r.get("userId"))
                                                .get("deptId"),
                                        r.get("userId")))
                .toList();
    }

    @Transactional
    public Map<String, Object> change(long user, Map<String, Object> b) {
        d.require(CHANGES, "edit");
        var old = d.employee(user);
        d.revision(old, b);
        long editId = optionalId(b.get("eventId"));
        if (editId > 0) {
            var event =
                    d.one(
                            "SELECT * FROM oa_hr_event WHERE id=? AND user_id=? FOR UPDATE",
                            editId,
                            user);
            d.expect(event, "draft", "rejected");
            if (id(event.get("revision")) != id(b.get("eventRevision")))
                throw new ApiException(409, "异动版本已变化");
        }
        String type = required(b.get("eventType"), "异动类型", 32);
        String reason = required(b.get("reason"), "办理原因", 1000);
        LocalDate effective = date(b.get("effectiveDate"));
        if (!Set.of("entry", "regular", "transfer", "dismiss", "rehire").contains(type))
            throw new ApiException(400, "异动类型无效");
        if ("entry".equals(type) && old.get("entryDate") != null)
            throw new ApiException(409, "已有入职日期，无需重复办理入职");
        if ("regular".equals(type) && old.get("regularDate") != null)
            throw new ApiException(409, "已有转正记录");
        if ("rehire".equals(type)
                && old.get("exitDate") != null
                && !effective.isAfter(date(old.get("exitDate"))))
            throw new ApiException(400, "重新入职日期必须晚于上次离职");
        if (Set.of("dismiss", "transfer").contains(type) && user == 1)
            throw new ApiException(403, "固定超级管理员不能离职或调部门");
        if ("departed".equals(old.get("employmentStatus")) && !"rehire".equals(type))
            throw new ApiException(409, "已离职员工仅可办理重新入职");
        if ("rehire".equals(type) && !"departed".equals(old.get("employmentStatus")))
            throw new ApiException(409, "仅离职员工可重新入职");
        if (old.get("entryDate") != null && effective.isBefore(date(old.get("entryDate"))))
            throw new ApiException(400, "生效日期不能早于本次入职");
        if ("transfer".equals(type)) {
            long dept = id(b.get("deptId"));
            if (d.jdbc.queryForObject(
                            "SELECT COUNT(*) FROM sys_dept WHERE dept_id=? AND status='0' AND"
                                    + " del_flag='0'",
                            Long.class,
                            dept)
                    == 0) throw new ApiException(400, "目标部门无效");
        }
        if (d.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM oa_hr_event WHERE user_id=? AND status IN"
                                + " ('approving','scheduled')",
                        Long.class,
                        user)
                > 0) throw new ApiException(409, "请先完成或取消该员工已有的待生效异动");
        if (editId > 0) {
            d.jdbc.update(
                    "UPDATE oa_hr_event SET"
                        + " event_type=?,effective_date=?,reason=?,before_json=?,after_json=?,status='draft',workflow_request_id=NULL,revision=revision+1"
                        + " WHERE id=?",
                    type,
                    effective,
                    reason,
                    d.json(employmentSnapshot(old)),
                    d.json(b),
                    editId);
            d.history("event", editId, user, "修订异动草稿", Map.of("reason", reason));
            return d.one("SELECT id,revision,status FROM oa_hr_event WHERE id=?", editId);
        }
        long eventId = d.event(user, type, effective, reason, employmentSnapshot(old), b, "draft");
        return d.one("SELECT id,revision,status FROM oa_hr_event WHERE id=?", eventId);
    }

    void apply(long user, String type, LocalDate effective, Map<String, Object> b) {
        var before = d.employee(user);
        if ("dismiss".equals(type)
                && d.jdbc.queryForObject(
                                "SELECT COUNT(*) FROM oa_process_task WHERE assignee_id=? AND"
                                    + " status='todo'",
                                Long.class,
                                user)
                        > 0) throw new ApiException(409, "该员工还有待审批任务，请先转办或办结，再办理离职");
        if ("entry".equals(type) && before.get("entryDate") != null)
            throw new ApiException(409, "已有入职记录，不能重复入职");
        if ("regular".equals(type) && before.get("regularDate") != null)
            throw new ApiException(409, "已有转正记录");
        if (before.get("entryDate") != null && effective.isBefore(date(before.get("entryDate"))))
            throw new ApiException(409, "异动日期早于当前任职期间");
        if ("rehire".equals(type) && !"departed".equals(before.get("employmentStatus")))
            throw new ApiException(409, "员工已不处于离职状态");
        if ("transfer".equals(type)
                && d.jdbc.queryForObject(
                                "SELECT COUNT(*) FROM sys_dept WHERE dept_id=? AND status='0' AND"
                                        + " del_flag='0'",
                                Long.class,
                                id(b.get("deptId")))
                        == 0) throw new ApiException(409, "目标部门已停用或删除");
        switch (type) {
            case "entry", "rehire" -> {
                d.insert(
                        "INSERT INTO oa_hr_employment_period(user_id,entry_date) VALUES(?,?)",
                        user,
                        effective);
                d.jdbc.update(
                        "UPDATE oa_hr_employee SET"
                            + " entry_date=?,exit_date=NULL,regular_date=NULL,employment_status='active'"
                            + " WHERE user_id=?",
                        effective,
                        user);
                d.jdbc.update("UPDATE sys_user SET del_flag='0',status='1' WHERE user_id=?", user);
                if ("rehire".equals(type)) {
                    d.jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", user);
                    d.jdbc.update("DELETE FROM sys_dept_principal WHERE user_id=?", user);
                }
            }
            case "regular" ->
                    d.jdbc.update(
                            "UPDATE oa_hr_employee SET regular_date=? WHERE user_id=?",
                            effective,
                            user);
            case "transfer" -> {
                d.jdbc.update(
                        "UPDATE sys_dept SET leader='' WHERE dept_id IN(SELECT dept_id FROM"
                                + " sys_dept_principal WHERE user_id=?)",
                        user);
                d.jdbc.update("DELETE FROM sys_dept_principal WHERE user_id=?", user);
                d.jdbc.update(
                        "UPDATE sys_user SET dept_id=? WHERE user_id=?", id(b.get("deptId")), user);
            }
            case "dismiss" -> {
                if (before.get("entryDate") != null)
                    d.jdbc.update(
                            "UPDATE oa_hr_employment_period SET exit_date=? WHERE user_id=? AND"
                                    + " entry_date=?",
                            effective,
                            user,
                            date(before.get("entryDate")));
                d.jdbc.update(
                        "UPDATE oa_hr_employee SET employment_status='departed',exit_date=? WHERE"
                                + " user_id=?",
                        effective,
                        user);
                d.jdbc.update("UPDATE sys_user SET del_flag='2',status='1' WHERE user_id=?", user);
                d.jdbc.update(
                        "UPDATE sys_dept SET leader='' WHERE dept_id IN(SELECT dept_id FROM"
                                + " sys_dept_principal WHERE user_id=?)",
                        user);
                d.jdbc.update("DELETE FROM sys_dept_principal WHERE user_id=?", user);
                createHandover(user);
            }
            default -> throw new ApiException(400, "异动类型无效");
        }
        d.jdbc.update(
                "UPDATE oa_hr_employee SET revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE"
                        + " user_id=?",
                user);
    }

    public void applyDue() {
        for (var e :
                d.rows(
                        "SELECT * FROM oa_hr_event WHERE status='scheduled' AND"
                                + " effective_date<=CURRENT_DATE ORDER BY effective_date,id")) {
            try {
                transactions.executeWithoutResult(
                        tx -> {
                            var current =
                                    d.one(
                                            "SELECT * FROM oa_hr_event WHERE id=? FOR UPDATE",
                                            e.get("id"));
                            if (!"scheduled".equals(current.get("status"))) return;
                            apply(
                                    id(current.get("userId")),
                                    text(current.get("eventType")),
                                    date(current.get("effectiveDate")),
                                    d.object(current.get("afterJson")));
                            d.jdbc.update(
                                    "UPDATE oa_hr_event SET status='effective',revision=revision+1"
                                            + " WHERE id=?",
                                    e.get("id"));
                            d.history(
                                    "event",
                                    id(e.get("id")),
                                    id(e.get("userId")),
                                    "异动到期生效",
                                    Map.of());
                        });
            } catch (Exception error) {
                transactions.executeWithoutResult(
                        tx -> {
                            String reason = "生效失败，请修订后重提：" + text(error.getMessage());
                            d.jdbc.update(
                                    "UPDATE oa_hr_event SET"
                                        + " status='rejected',reason=?,revision=revision+1 WHERE"
                                        + " id=? AND status='scheduled'",
                                    reason.substring(0, Math.min(1000, reason.length())),
                                    e.get("id"));
                            if ("dismiss".equals(e.get("eventType")))
                                d.jdbc.update(
                                        "UPDATE oa_hr_employee SET"
                                            + " employment_status='active',revision=revision+1"
                                            + " WHERE user_id=? AND employment_status='leaving'",
                                        e.get("userId"));
                            d.history(
                                    "event",
                                    id(e.get("id")),
                                    id(e.get("userId")),
                                    "异动生效失败",
                                    Map.of("error", reason));
                        });
            }
        }
    }

    @Transactional
    public void cancel(long event, Map<String, Object> b) {
        d.require(CHANGES, "edit");
        var e = d.one("SELECT * FROM oa_hr_event WHERE id=? FOR UPDATE", event);
        d.employee(id(e.get("userId")));
        d.expect(e, "scheduled");
        d.jdbc.update("UPDATE oa_hr_event SET status='cancelled' WHERE id=?", event);
        if ("dismiss".equals(e.get("eventType")))
            d.jdbc.update(
                    "UPDATE oa_hr_employee SET employment_status='active',revision=revision+1 WHERE"
                            + " user_id=?",
                    e.get("userId"));
        d.history(
                "event",
                event,
                id(e.get("userId")),
                "取消待生效异动",
                Map.of("reason", required(b.get("reason"), "取消原因", 1000)));
    }

    void createHandover(long user) {
        for (var spec :
                List.of(
                        new String[] {"customer", "oa_merchant", "owner_user_id", "name"},
                        new String[] {"job", "oa_recruit_job", "owner_user_id", "title"},
                        new String[] {"talent", "oa_recruit_talent", "owner_user_id", "name"},
                        new String[] {
                            "application", "oa_recruit_application", "consultant_id", "id"
                        })) {
            for (var row :
                    d.rows(
                            "SELECT id,"
                                    + spec[3]
                                    + " AS title FROM "
                                    + spec[1]
                                    + " WHERE "
                                    + spec[2]
                                    + "=?",
                            user))
                d.jdbc.update(
                        "INSERT INTO oa_hr_handover(user_id,business_type,business_id,title) SELECT"
                                + " ?,?,?,? WHERE NOT EXISTS(SELECT 1 FROM oa_hr_handover WHERE"
                                + " user_id=? AND business_type=? AND business_id=?)",
                        user,
                        spec[0],
                        row.get("id"),
                        "交接" + spec[0] + "：" + row.get("title"),
                        user,
                        spec[0],
                        row.get("id"));
        }
        long manager =
                d.jdbc.queryForObject(
                        "SELECT COALESCE((SELECT p.user_id FROM sys_dept_principal p JOIN sys_user"
                            + " u ON u.dept_id=p.dept_id WHERE u.user_id=? AND p.user_id<>?),1)",
                        Long.class,
                        user,
                        user);
        d.jdbc.update(
                "UPDATE oa_todo SET status='done' WHERE user_id=? AND status='todo' AND"
                        + " business_type='hrPayslip'",
                user);
        d.jdbc.update(
                "UPDATE oa_todo SET user_id=? WHERE user_id=? AND status='todo' AND"
                        + " process_instance_id IS NULL AND business_type<>'hrPayslip'",
                manager,
                user);
        if (d.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM oa_hr_handover WHERE user_id=? AND status='todo'",
                        Long.class,
                        user)
                > 0)
            d.todo(
                    manager,
                    "离职交接待处理（员工编号 " + user + "）",
                    "hrHandover",
                    user,
                    "hr-handover-" + user);
    }

    public List<Map<String, Object>> handovers(long user) {
        d.require(CHANGES, "access");
        d.employee(user);
        return d.rows("SELECT * FROM oa_hr_handover WHERE user_id=? ORDER BY id", user);
    }

    @Transactional
    public void handover(long record, Map<String, Object> b) {
        d.require(CHANGES, "handover");
        var h = d.one("SELECT * FROM oa_hr_handover WHERE id=? FOR UPDATE", record);
        long from = id(h.get("userId"));
        d.employee(from);
        d.expect(h, "todo");
        long to = id(b.get("targetUserId"));
        var target = d.employee(to);
        if (to == from || !"0".equals(target.get("delFlag")) || !"0".equals(target.get("status")))
            throw new ApiException(400, "接收人须为其他有效在职成员");
        var spec =
                switch (text(h.get("businessType"))) {
                    case "customer" -> new String[] {"oa_merchant", "owner_user_id"};
                    case "job" -> new String[] {"oa_recruit_job", "owner_user_id"};
                    case "talent" -> new String[] {"oa_recruit_talent", "owner_user_id"};
                    case "application" -> new String[] {"oa_recruit_application", "consultant_id"};
                    default -> throw new ApiException(400, "交接类型无效");
                };
        int changed =
                d.jdbc.update(
                        "UPDATE "
                                + spec[0]
                                + " SET "
                                + spec[1]
                                + "=? WHERE id=? AND "
                                + spec[1]
                                + "=?",
                        to,
                        h.get("businessId"),
                        from);
        if (changed == 0) throw new ApiException(409, "业务归属已变化，请核实后处理");
        if ("customer".equals(h.get("businessType"))) {
            d.jdbc.update(
                    "UPDATE oa_merchant SET owner_user_name=? WHERE id=?",
                    target.get("nickName"),
                    h.get("businessId"));
            contracts.syncOwnership(id(h.get("businessId")));
            reminders.synchronizeCustomer(id(h.get("businessId")));
        }
        d.jdbc.update(
                "UPDATE oa_hr_handover SET status='done',target_user_id=? WHERE id=?", to, record);
        d.history("handover", record, from, "交接完成", Map.of("targetUserId", to));
        if (d.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM oa_hr_handover WHERE user_id=? AND status='todo'",
                        Long.class,
                        from)
                == 0) d.closeTodos("hr-handover-" + from);
    }
}
