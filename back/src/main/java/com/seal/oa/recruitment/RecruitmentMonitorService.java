package com.seal.oa.recruitment;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.system.DataScopeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.seal.oa.recruitment.RecruitmentService.*;

/** Read-only projections of recruitment facts. No workflow or business state is changed here. */
@Service
public class RecruitmentMonitorService {
    public static final long MODULE = 341304L;
    static final int STAGNANT_DAYS = 3;
    static final Set<String> ACTIVE = Set.of("recommended", "feedback", "interview", "offer", "entry", "guarantee", "entered");
    static final List<String> METRICS = List.of("active", "feedback", "interview", "entry", "guarantee", "pending", "overdue", "stagnant", "followups", "recommended", "interviewed", "offered", "entered", "completed");
    private final RecruitmentService s;
    private final DataScopeService scopes;

    public RecruitmentMonitorService(RecruitmentService s, DataScopeService scopes) { this.s = s; this.scopes = scopes; }

    record Period(LocalDate start, LocalDate end) {
        boolean includes(LocalDateTime time) { return time != null && !time.toLocalDate().isBefore(start) && !time.toLocalDate().isAfter(end); }
    }
    record Snapshot(List<Map<String,Object>> people, Map<String,List<Map<String,Object>>> details, Map<String,Object> options, Period period, LocalDateTime now) {}

    @Transactional(readOnly = true)
    public Map<String,Object> overview(Map<String,String> query) {
        Snapshot data = snapshot(query);
        Map<String,Object> summary = new LinkedHashMap<>();
        METRICS.forEach(key -> summary.put(key, data.details.get(key).size()));
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("people", data.people);
        result.put("options", data.options);
        result.put("startDate", data.period.start);
        result.put("endDate", data.period.end);
        result.put("asOf", data.now.toString().replace('T', ' '));
        result.put("stagnantDays", STAGNANT_DAYS);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String,Object> details(Map<String,String> query) {
        String metric = text(query.get("metric"));
        if (!METRICS.contains(metric)) throw new ApiException(400, "未知统计指标");
        Snapshot data = snapshot(query);
        return s.page(data.details.get(metric), query);
    }

    private Snapshot snapshot(Map<String,String> query) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        Period period = new Period(queryDate(query, "startDate", now.toLocalDate().withDayOfMonth(1)), queryDate(query, "endDate", now.toLocalDate()));
        if (period.end.isBefore(period.start)) throw new ApiException(400, "结束日期不能早于开始日期");
        DataScopeService.Scope scope = scopes.resolve(AuthContext.userId());
        // Scope belongs to the person whose work is being monitored, never the customer's or job owner's department.
        Map<Long,Map<String,Object>> users = new LinkedHashMap<>();
        for (Map<String,Object> user : s.rows("""
                SELECT u.user_id,u.nick_name AS user_name,u.dept_id,d.dept_name,u.status,u.del_flag,
                    CASE WHEN EXISTS (SELECT 1 FROM sys_user_role ur JOIN sys_role_module rm ON rm.role_id=ur.role_id
                         WHERE ur.user_id=u.user_id AND rm.module_id IN (341301,341302,341303)) THEN 1 ELSE 0 END AS recruitment_member
                FROM sys_user u LEFT JOIN sys_dept d ON d.dept_id=u.dept_id WHERE u.tenant_id=1
                """)) {
            if (visible(scope, num(user.get("deptId")), num(user.get("userId")))) users.put(num(user.get("userId")), user);
        }
        List<Map<String,Object>> apps = s.rows("""
                SELECT a.id,a.job_id,a.talent_id,a.consultant_id AS user_id,a.stage,a.submitted_date,a.feedback_due_date,
                    a.feedback_status,a.offer_date,a.expected_entry_date,a.actual_entry_date,a.guarantee_end,a.guarantee_status,
                    a.create_time,j.customer_id,j.title AS job_title,t.name AS talent_name,m.name AS customer_name,
                    (SELECT MIN(i.interview_time) FROM oa_recruit_interview i WHERE i.application_id=a.id AND i.status='scheduled') AS interview_time,
                    (SELECT MIN(i.update_time) FROM oa_recruit_interview i WHERE i.application_id=a.id AND i.status='completed' AND i.result='pending') AS pending_result_time
                FROM oa_recruit_application a JOIN oa_recruit_job j ON j.id=a.job_id
                JOIN oa_recruit_talent t ON t.id=a.talent_id JOIN oa_merchant m ON m.id=j.customer_id
                WHERE a.tenant_id=1
                """).stream().filter(r -> users.containsKey(num(r.get("userId")))).toList();
        List<Map<String,Object>> talents = s.rows("""
                SELECT id AS talent_id,name AS talent_name,owner_user_id AS user_id,status,create_time
                FROM oa_recruit_talent WHERE tenant_id=1 AND status NOT IN ('deleted','merged')
                """).stream().filter(r -> users.containsKey(num(r.get("userId")))).toList();
        List<Map<String,Object>> tasks = s.rows("""
                SELECT x.id,x.talent_id,x.owner_user_id AS user_id,x.title AS task_title,x.due_date,x.create_time,t.name AS talent_name
                FROM oa_recruit_task x JOIN oa_recruit_talent t ON t.id=x.talent_id
                WHERE t.tenant_id=1 AND x.status='todo'
                """).stream().filter(r -> users.containsKey(num(r.get("userId")))).toList();
        List<Map<String,Object>> exceptions = s.rows("""
                SELECT x.id,a.id AS application_id,a.talent_id,a.job_id,j.customer_id,x.owner_user_id AS user_id,
                    x.content AS task_title,x.create_time,t.name AS talent_name,j.title AS job_title,m.name AS customer_name
                FROM oa_recruit_exception x JOIN oa_recruit_application a ON a.id=x.application_id
                JOIN oa_recruit_job j ON j.id=a.job_id JOIN oa_recruit_talent t ON t.id=a.talent_id
                JOIN oa_merchant m ON m.id=j.customer_id WHERE a.tenant_id=1 AND x.status='open'
                """).stream().filter(r -> users.containsKey(num(r.get("userId")))).toList();

        Map<String,Object> options = options(users, apps, exceptions);
        Predicate<Map<String,Object>> matches = r -> matches(query, r, users);
        List<Map<String,Object>> filteredApps = apps.stream().filter(matches).toList();
        Set<Long> appIds = filteredApps.stream().map(r -> num(r.get("id"))).collect(Collectors.toSet());
        Set<Long> talentIds = new HashSet<>();
        filteredApps.forEach(r -> talentIds.add(num(r.get("talentId"))));
        talents.stream().filter(matches).forEach(r -> talentIds.add(num(r.get("talentId"))));
        tasks.stream().filter(matches).forEach(r -> talentIds.add(num(r.get("talentId"))));
        Map<Long,List<Map<String,Object>>> appEvents = events("application", appIds);
        Map<Long,List<Map<String,Object>>> talentEvents = events("talent", talentIds);
        Map<String,List<Map<String,Object>>> details = new LinkedHashMap<>();
        METRICS.forEach(key -> details.put(key, new ArrayList<>()));
        List<Map<String,Object>> allActivities = new ArrayList<>();
        Set<Long> activeTalents = apps.stream().filter(r -> ACTIVE.contains(text(r.get("stage")))).map(r -> num(r.get("talentId"))).collect(Collectors.toSet());

        for (Map<String,Object> app : filteredApps) {
            Map<String,Object> row = record(app, "application", num(app.get("id")), users);
            List<Map<String,Object>> history = appEvents.getOrDefault(num(app.get("id")), List.of());
            LocalDateTime created = time(app.get("createTime"));
            List<Map<String,Object>> contacts = talentEvents.getOrDefault(num(app.get("talentId")), List.of()).stream()
                    .filter(e -> "contact".equals(e.get("eventType")) && !time(e.get("createTime")).isBefore(created)).toList();
            List<Map<String,Object>> activity = new ArrayList<>(history.stream().filter(this::effective)
                    .filter(e -> !"create".equals(e.get("eventType")) || app.get("submittedDate") != null).toList());
            if (ACTIVE.contains(text(app.get("stage")))) activity.addAll(contacts);
            latest(row, activity);
            allActivities.add(row);
            addFollowup(details, row, activity, period);
            milestone(details, row, "recommended", first(history, e -> ("create".equals(e.get("eventType")) && app.get("submittedDate") != null && !text(e.get("content")).contains("草稿"))
                    || ("recommend".equals(e.get("eventType")) && !"withdraw".equals(operation(e)))), app.get("submittedDate"), period);
            milestone(details, row, "interviewed", first(history, e -> "interview_result".equals(e.get("eventType"))), null, period);
            milestone(details, row, "offered", first(history, e -> "offer".equals(e.get("eventType")) && "issue".equals(operation(e))), app.get("offerDate"), period);
            // Actual entry is a business date and can be corrected or backfilled; never substitute the recording date.
            milestone(details, row, "entered", null, app.get("actualEntryDate"), period);
            milestone(details, row, "completed", first(history, e -> "guarantee".equals(e.get("eventType")) && "complete".equals(operation(e))), null, period);
            if (ACTIVE.contains(text(app.get("stage")))) {
                List<String> reasons = new ArrayList<>();
                if (Set.of("recommended", "feedback").contains(text(app.get("stage"))) && Set.of("pending", "need_info").contains(text(app.get("feedbackStatus"))) && past(app.get("feedbackDueDate"), now)) reasons.add("客户反馈超期");
                if ("interview".equals(app.get("stage")) && beforeNow(app.get("interviewTime"), now)) reasons.add("面试结果未登记");
                if ("interview".equals(app.get("stage")) && app.get("pendingResultTime") != null && ChronoUnit.DAYS.between(time(app.get("pendingResultTime")), now) >= STAGNANT_DAYS) reasons.add("面试待定结果超过3天");
                if ("entry".equals(app.get("stage")) && past(app.get("expectedEntryDate"), now)) reasons.add("预计入职已过期");
                if ("active".equals(app.get("guaranteeStatus")) && past(app.get("guaranteeEnd"), now)) reasons.add("保证期到期未处理");
                pending(details, row, reasons, created, now);
                details.get("active").add(row);
                String stage = text(app.get("stage"));
                if (Set.of("recommended", "feedback").contains(stage)) details.get("feedback").add(row);
                if (Set.of("interview", "entry", "guarantee").contains(stage)) details.get(stage).add(row);
                if ("entered".equals(stage)) details.get("guarantee").add(row);
            }
        }
        for (Map<String,Object> talent : talents.stream().filter(matches).toList()) {
            Map<String,Object> row = record(talent, "talent", num(talent.get("talentId")), users);
            row.put("stage", talent.get("status"));
            List<Map<String,Object>> contacts = talentEvents.getOrDefault(num(talent.get("talentId")), List.of()).stream().filter(e -> "contact".equals(e.get("eventType"))).toList();
            latest(row, contacts);
            allActivities.add(row);
            addFollowup(details, row, contacts, period);
            if (!activeTalents.contains(num(talent.get("talentId"))) && Set.of("uncontacted", "contacting").contains(text(talent.get("status")))) pending(details, row, new ArrayList<>(), time(talent.get("createTime")), now);
        }
        for (Map<String,Object> task : tasks.stream().filter(matches).toList()) {
            Map<String,Object> row = record(task, "task", num(task.get("id")), users);
            row.put("stage", "todo");
            row.put("dueDate", task.get("dueDate"));
            // A different call to the same candidate must not reset this task's own deadline or inactivity.
            pending(details, row, new ArrayList<>(past(task.get("dueDate"), now) ? List.of("跟进任务超期") : List.of()), time(task.get("createTime")), now);
        }
        for (Map<String,Object> exception : exceptions.stream().filter(matches).toList()) {
            Map<String,Object> row = record(exception, "exception", num(exception.get("id")), users);
            row.put("stage", "open");
            // Open exceptions have no deadline in the source model. Do not invent an overdue date.
            pending(details, row, new ArrayList<>(), time(exception.get("createTime")), now);
        }
        // One person following the same candidate through several records still counts as one candidate.
        Map<String,Map<String,Object>> uniqueFollowups = new LinkedHashMap<>();
        for (Map<String,Object> row : details.get("followups")) {
            String key = row.get("userId") + ":" + row.get("talentId");
            Map<String,Object> old = uniqueFollowups.get(key);
            if (old == null || text(row.get("metricTime")).compareTo(text(old.get("metricTime"))) > 0) uniqueFollowups.put(key, row);
        }
        details.put("followups", new ArrayList<>(uniqueFollowups.values()));
        details.forEach((key, rows) -> rows.sort(Comparator.comparing((Map<String,Object> r) -> text(r.get("metricTime"))).reversed().thenComparing(r -> text(r.get("key")))));
        List<Map<String,Object>> people = people(users, details, query, allActivities);
        return new Snapshot(people, details, options, period, now);
    }

    private boolean visible(DataScopeService.Scope scope, long department, long user) {
        return scope.all() || user == AuthContext.userId() || !scope.selfOnly() && scope.deptIds().contains(department);
    }

    private boolean matches(Map<String,String> query, Map<String,Object> row, Map<Long,Map<String,Object>> users) {
        Map<String,Object> user = users.get(num(row.get("userId")));
        return user != null && filter(user, query, "deptId") && filter(row, query, "userId") && filter(row, query, "customerId") && filter(row, query, "jobId");
    }

    private Map<String,Object> options(Map<Long,Map<String,Object>> users, List<Map<String,Object>> apps, List<Map<String,Object>> exceptions) {
        List<Map<String,Object>> related = new ArrayList<>(apps); related.addAll(exceptions);
        Map<Long,Map<String,Object>> departments = new LinkedHashMap<>(), customers = new LinkedHashMap<>(), jobs = new LinkedHashMap<>();
        users.values().forEach(u -> departments.put(num(u.get("deptId")), option(num(u.get("deptId")), text(u.get("deptName")))));
        related.forEach(r -> { customers.put(num(r.get("customerId")), option(num(r.get("customerId")), text(r.get("customerName")))); jobs.put(num(r.get("jobId")), Map.of("id", num(r.get("jobId")), "name", text(r.get("jobTitle")), "customerId", num(r.get("customerId")))); });
        return Map.of("departments", departments.values(), "users", users.values().stream().map(u -> Map.of("id", num(u.get("userId")), "name", text(u.get("userName")), "deptId", num(u.get("deptId")))).toList(), "customers", customers.values(), "jobs", jobs.values());
    }

    private Map<String,Object> option(long id, String name) { return Map.of("id", id, "name", name); }

    private Map<Long,List<Map<String,Object>>> events(String type, Set<Long> ids) {
        Map<Long,List<Map<String,Object>>> result = new HashMap<>();
        List<Long> all = new ArrayList<>(ids);
        for (int offset = 0; offset < all.size(); offset += 400) {
            List<Long> batch = all.subList(offset, Math.min(offset + 400, all.size()));
            List<Object> args = new ArrayList<>(); args.add(type); args.addAll(batch);
            for (Map<String,Object> event : s.rows("SELECT e.entity_id,e.event_type,e.content,e.payload_json,e.create_time,u.nick_name AS operator_name FROM oa_recruit_event e LEFT JOIN sys_user u ON u.user_id=e.operator_id WHERE e.entity_type=? AND e.entity_id IN (" + String.join(",", Collections.nCopies(batch.size(), "?")) + ") ORDER BY e.create_time,e.id", args.toArray())) {
                result.computeIfAbsent(num(event.get("entityId")), ignored -> new ArrayList<>()).add(event);
            }
        }
        return result;
    }

    private Map<String,Object> record(Map<String,Object> source, String type, long id, Map<Long,Map<String,Object>> users) {
        Map<String,Object> row = new LinkedHashMap<>();
        for (String key : List.of("talentId", "talentName", "userId", "jobId", "jobTitle", "customerId", "customerName", "stage", "taskTitle", "applicationId")) if (source.get(key) != null) row.put(key, source.get(key));
        row.put("key", type + "-" + id);
        row.put("entityType", type);
        row.put("recordId", id);
        Map<String,Object> user = users.get(num(source.get("userId")));
        row.put("userName", text(user.get("userName")));
        row.put("deptName", text(user.get("deptName")));
        return row;
    }

    private String operation(Map<String,Object> event) { return text(s.object(event.get("payloadJson")).get("operation")); }

    private boolean effective(Map<String,Object> event) {
        String type = text(event.get("eventType"));
        if ("create".equals(type)) return !text(event.get("content")).contains("草稿");
        if ("recommend".equals(type)) return true;
        if (Set.of("feedback", "interview_save", "interview_notify", "interview_cancel", "interview_result", "departure", "replacement", "replacement_completed").contains(type)) return true;
        if ("offer".equals(type)) return Set.of("issue", "accept", "reject", "withdraw").contains(operation(event));
        return Set.of("entry", "guarantee", "exception").contains(type);
    }

    private void latest(Map<String,Object> row, List<Map<String,Object>> activity) {
        activity.stream().max(Comparator.comparing(e -> time(e.get("createTime")))).ifPresent(event -> {
            row.put("lastFollowupTime", stamp(time(event.get("createTime"))));
            row.put("lastFollowupContent", text(event.get("content")));
            row.put("operatorName", text(event.get("operatorName")));
        });
    }

    private void addFollowup(Map<String,List<Map<String,Object>>> details, Map<String,Object> source, List<Map<String,Object>> activity, Period period) {
        activity.stream().filter(e -> period.includes(time(e.get("createTime")))).max(Comparator.comparing(e -> time(e.get("createTime")))).ifPresent(event -> {
            Map<String,Object> row = new LinkedHashMap<>(source);
            row.put("metricTime", stamp(time(event.get("createTime"))));
            row.put("lastFollowupContent", text(event.get("content")));
            row.put("operatorName", text(event.get("operatorName")));
            details.get("followups").add(row);
        });
    }

    private Map<String,Object> first(List<Map<String,Object>> events, Predicate<Map<String,Object>> match) { return events.stream().filter(match).findFirst().orElse(null); }

    private void milestone(Map<String,List<Map<String,Object>>> details, Map<String,Object> source, String metric, Map<String,Object> event, Object fallbackDate, Period period) {
        LocalDateTime occurred = event == null ? time(fallbackDate) : time(event.get("createTime"));
        if (!period.includes(occurred)) return;
        Map<String,Object> row = new LinkedHashMap<>(source);
        row.put("metricTime", stamp(occurred));
        if (event != null) { row.put("operatorName", text(event.get("operatorName"))); row.put("metricContent", text(event.get("content"))); }
        details.get(metric).add(row);
    }

    private void pending(Map<String,List<Map<String,Object>>> details, Map<String,Object> row, List<String> reasons, LocalDateTime created, LocalDateTime now) {
        LocalDateTime last = row.get("lastFollowupTime") == null ? created : time(row.get("lastFollowupTime"));
        long days = Math.max(0, ChronoUnit.DAYS.between(last, now));
        row.put("idleDays", days);
        row.put("trackingSince", stamp(last));
        row.put("stagnant", days >= STAGNANT_DAYS);
        row.put("overdueReasons", reasons);
        row.put("metricTime", stamp(last));
        details.get("pending").add(row);
        if (!reasons.isEmpty()) details.get("overdue").add(row);
        if (days >= STAGNANT_DAYS) details.get("stagnant").add(row);
    }

    private List<Map<String,Object>> people(Map<Long,Map<String,Object>> users, Map<String,List<Map<String,Object>>> details, Map<String,String> query, List<Map<String,Object>> allActivities) {
        Set<Long> relevant = details.values().stream().flatMap(Collection::stream).map(r -> num(r.get("userId"))).collect(Collectors.toSet());
        List<Map<String,Object>> result = new ArrayList<>();
        for (Map<String,Object> user : users.values()) {
            long id = num(user.get("userId"));
            if (!filter(user, query, "deptId") || !filter(user, query, "userId")) continue;
            boolean zeroMember = num(user.get("recruitmentMember")) == 1 && "0".equals(user.get("status")) && "0".equals(user.get("delFlag")) && text(query.get("customerId")).isBlank() && text(query.get("jobId")).isBlank();
            if (!relevant.contains(id) && !zeroMember) continue;
            Map<String,Object> row = new LinkedHashMap<>();
            for (String key : List.of("userId", "userName", "deptId", "deptName")) row.put(key, user.get(key));
            METRICS.forEach(metric -> row.put(metric, details.get(metric).stream().filter(r -> num(r.get("userId")) == id).count()));
            allActivities.stream().filter(r -> num(r.get("userId")) == id && r.get("lastFollowupTime") != null)
                    .map(r -> text(r.get("lastFollowupTime"))).max(String::compareTo).ifPresent(value -> row.put("lastFollowupTime", value));
            // Include recent followups on records already completed, too.
            details.get("followups").stream().filter(r -> num(r.get("userId")) == id).map(r -> text(r.get("metricTime"))).max(String::compareTo)
                    .filter(value -> value.compareTo(text(row.get("lastFollowupTime"))) > 0).ifPresent(value -> row.put("lastFollowupTime", value));
            result.add(row);
        }
        result.sort(Comparator.comparingLong((Map<String,Object> r) -> num(r.get("overdue"))).reversed().thenComparing(r -> text(r.get("userName"))));
        return result;
    }

    private LocalDate queryDate(Map<String,String> q, String key, LocalDate fallback) { return text(q.get(key)).isBlank() ? fallback : date(q.get(key), "统计日期"); }
    private boolean past(Object value, LocalDateTime now) { LocalDateTime time = time(value); return time != null && time.toLocalDate().isBefore(now.toLocalDate()); }
    private boolean beforeNow(Object value, LocalDateTime now) { LocalDateTime time = time(value); return time != null && time.isBefore(now); }
    private String stamp(LocalDateTime value) { return value.toString().replace('T', ' '); }
    private LocalDateTime time(Object value) {
        if (value == null) return null;
        if (value instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        if (value instanceof java.sql.Date d) return d.toLocalDate().atStartOfDay();
        String raw = text(value).replace(' ', 'T');
        return raw.length() == 10 ? LocalDate.parse(raw).atStartOfDay() : LocalDateTime.parse(raw);
    }
}
