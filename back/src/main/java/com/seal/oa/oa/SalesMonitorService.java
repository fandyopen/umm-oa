package com.seal.oa.oa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.JdbcMaps;
import com.seal.oa.system.DataScopeService;
import com.seal.oa.system.MemberIdentity;
import com.seal.oa.system.SystemService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/** Read-only sales facts; all summaries and drilldowns share the same scoped projection. */
@Service
public class SalesMonitorService {
    // Stable organization identity; changing the department's display name does not change its scope.
    static final long SALES_DEPARTMENT_ID = 326L;
    static final List<String> METRICS = List.of("customers", "newCustomers", "contacted", "followups", "activeOpportunities", "newOpportunities", "demand", "quotation", "negotiation", "won", "pendingContracts", "signedContracts", "signedAmount", "receivedAmount", "stagnant", "overdue", "stagnantOpportunities");
    static final Map<String,String> STAGES = Map.of("需求确认", "demand", "方案报价", "quotation", "商务谈判", "negotiation", "赢单", "won", "已赢单", "won");
    static final Set<String> CLOSED = Set.of("赢单", "已赢单", "输单", "已输单", "已关闭");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final DataScopeService scopes;
    private final SystemService system;

    public SalesMonitorService(JdbcTemplate jdbc, ObjectMapper json, DataScopeService scopes, SystemService system) {
        this.jdbc = jdbc; this.json = json; this.scopes = scopes; this.system = system;
    }
    record Period(LocalDate start, LocalDate end) {
        boolean includes(LocalDateTime time, LocalDateTime now) { return time != null && !time.isAfter(now) && !time.toLocalDate().isBefore(start) && !time.toLocalDate().isAfter(end); }
    }
    record Snapshot(List<Map<String,Object>> people, Map<String,List<Map<String,Object>>> details, Map<String,Object> options, Map<String,Object> rules, Period period, LocalDateTime now) {}

    @Transactional(readOnly = true)
    public Map<String,Object> overview(Map<String,String> query) {
        Snapshot data = snapshot(query);
        Map<String,Object> summary = new LinkedHashMap<>();
        METRICS.forEach(key -> summary.put(key, value(key, data.details.get(key))));
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("summary", summary); result.put("people", data.people); result.put("options", data.options);
        result.put("rules", data.rules); result.put("stagnantDays", 3); result.put("asOf", data.now.toString());
        result.put("startDate", data.period.start); result.put("endDate", data.period.end);
        result.put("scope", scopes.resolve(AuthContext.userId()).selfOnly() ? "self" : "department");
        result.put("canManageRules", system.hasModuleAction(AuthContext.userId(), 341112L, "rule_manage"));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String,Object> details(Map<String,String> query) {
        String metric = query.getOrDefault("metric", "");
        if (!METRICS.contains(metric)) throw new ApiException(400, "未知统计指标");
        var rows = snapshot(query).details.get(metric);
        int page = positive(query.getOrDefault("pageNum", "1"), 1000000), size = positive(query.getOrDefault("pageSize", "10"), 100);
        int start = (int)Math.min(rows.size(), (long)(page - 1) * size);
        return Map.of("code", 200, "rows", rows.subList(start, Math.min(rows.size(), start + size)), "total", rows.size());
    }

    private Snapshot snapshot(Map<String,String> query) {
        long tenant = system.requireCurrentUser().tenantId();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        Period period = new Period(date(query.get("startDate"), now.toLocalDate().withDayOfMonth(1)), date(query.get("endDate"), now.toLocalDate()));
        if (period.end.isBefore(period.start) || ChronoUnit.DAYS.between(period.start, period.end) > 365) throw new ApiException(400, "统计时间须正序且不超过366天");
        for (String key : List.of("deptId", "userId", "customerId")) if (!text(query.get(key)).isBlank() && !text(query.get(key)).matches("[1-9][0-9]*")) throw new ApiException(400, "筛选条件无效");
        var scope = scopes.resolve(AuthContext.userId());
        Set<Long> salesDepartments = salesDepartments(tenant);
        Map<Long,Map<String,Object>> users = new LinkedHashMap<>();
        for (var u : rows("""
                SELECT u.user_id,u.nick_name AS user_name,u.dept_id,d.dept_name
                FROM sys_user u LEFT JOIN sys_dept d ON d.dept_id=u.dept_id AND d.tenant_id=u.tenant_id
                WHERE u.tenant_id=? AND u.status='0' AND u.del_flag='0' AND %s ORDER BY u.user_id
                """.formatted(MemberIdentity.condition("u")), tenant)) {
            if (!salesDepartments.contains(num(u.get("deptId")))) continue;
            if (scope.all() || num(u.get("userId")) == AuthContext.userId() || (!scope.selfOnly() && scope.deptIds().contains(num(u.get("deptId"))))) users.put(num(u.get("userId")), u);
        }
        Map<Long,Map<String,Object>> customers = new LinkedHashMap<>();
        for (var c : rows("SELECT * FROM oa_merchant WHERE tenant_id=? AND status='0' AND public_pool='0'", tenant)) {
            if (users.containsKey(num(c.get("ownerUserId")))) customers.put(num(c.get("id")), c);
        }
        List<Map<String,Object>> opportunities = new ArrayList<>();
        for (var raw : rows("SELECT id,payload_json,create_time FROM biz_record WHERE tenant_id=? AND module='/project/business/list'", tenant)) {
            var p = parse(raw.get("payloadJson"));
            if (!users.containsKey(num(p.get("ownerUserId")))) continue;
            p.put("recordCreated", raw.get("createTime")); p.putIfAbsent("id", raw.get("id")); opportunities.add(p);
        }
        Map<String,Object> options = options(users, customers, opportunities);
        Map<String,Object> rules = new LinkedHashMap<>(Map.of("firstFollowupDays", 3, "followupIntervalDays", 7, "stagnantOpportunityDays", 14));
        var config = rows("SELECT config_value FROM sys_config WHERE config_key=?", "crm.sales.rules." + tenant);
        if (!config.isEmpty()) rules.putAll(parse(config.get(0).get("configValue")));
        Map<String,List<Map<String,Object>>> details = new LinkedHashMap<>();
        METRICS.forEach(k -> details.put(k, new ArrayList<>()));
        Map<Long,List<Map<String,Object>>> followups = rows("""
                SELECT f.*,COALESCE(u.nick_name,f.owner_user_name,'历史未记录') AS operator_name
                FROM oa_customer_followup f JOIN oa_merchant m ON m.id=f.merchant_id AND m.tenant_id=f.tenant_id
                LEFT JOIN sys_user u ON u.user_id=f.owner_user_id AND u.tenant_id=f.tenant_id WHERE f.tenant_id=?
                ORDER BY f.followup_time DESC,f.id DESC
                """, tenant).stream().filter(f -> customers.containsKey(num(f.get("merchantId"))) && time(f.get("followupTime")) != null && !time(f.get("followupTime")).isAfter(now) && !text(f.get("content")).isBlank())
                .collect(Collectors.groupingBy(f -> num(f.get("merchantId"))));
        for (var c : customers.values()) {
            var row = record("customer", num(c.get("id")), num(c.get("ownerUserId")), num(c.get("id")), text(c.get("name")), users);
            if (!matches(query, row)) continue;
            row.put("stage", c.get("cooperationStatus"));
            LocalDateTime since = time(c.get("assignedAt")); if (since == null) since = time(c.get("createTime"));
            final LocalDateTime assigned = since;
            var activity = followups.getOrDefault(num(c.get("id")), List.of()).stream().filter(f -> assigned == null || !time(f.get("followupTime")).isBefore(assigned)).toList();
            Map<String,Object> latest = activity.isEmpty() ? Map.of() : activity.get(0);
            LocalDateTime last = time(latest.get("followupTime"));
            annotate(row, last, latest.get("content"), latest.get("operatorName"), since, now);
            details.get("customers").add(row);
            milestone(details, "newCustomers", row, time(c.get("createTime")), "客户建档", period, now);
            for (var f : activity) {
                var event = new LinkedHashMap<>(row); event.put("key", "followup:" + f.get("id"));
                event.put("operatorName", f.get("operatorName")); event.put("metricContent", f.get("content"));
                milestone(details, "followups", event, time(f.get("followupTime")), text(f.get("content")), period, now);
            }
            var contact = details.get("followups").stream().filter(f -> Objects.equals(f.get("customerId"), row.get("customerId"))).findFirst();
            contact.ifPresent(f -> details.get("contacted").add(new LinkedHashMap<>(f)));
            if (last == null) last = since;
            if (last != null && !last.plusDays(3).isAfter(now)) details.get("stagnant").add(row);
            LocalDateTime due = time(latest.get("nextFollowupTime"));
            if (due == null && last != null) due = last.plusDays(num(rules.get(activity.isEmpty() ? "firstFollowupDays" : "followupIntervalDays")));
            row.put("dueTime", due);
            if (due != null && due.isBefore(now)) {
                row.put("reason", activity.isEmpty() ? "本次分配后首次跟进超期" : latest.get("nextFollowupTime") != null ? "已超过下次跟进计划时间" : "已超过持续跟进间隔");
                details.get("overdue").add(row);
            }
        }
        for (var o : opportunities) {
            long customerId = num(o.get("customerId"));
            var row = record("opportunity", num(o.get("id")), num(o.get("ownerUserId")), customerId, text(o.get("name")), users);
            if (!matches(query, row)) continue;
            row.put("customerName", o.getOrDefault("customerName", "未关联客户"));
            String stage = text(o.getOrDefault("stage", o.get("businessState"))); row.put("stage", stage);
            row.put("amount", decimal(o.getOrDefault("expectedAmount", o.get("amount"))));
            LocalDateTime created = time(o.get("recordCreated"));
            LocalDateTime latest = null; Map<String,Object> last = Map.of();
            Map<String,Map<String,Object>> first = new LinkedHashMap<>();
            for (var event : history(o.get("sealBusinessStages"))) {
                LocalDateTime at = time(event.get("recordTime"));
                if (at == null || at.isAfter(now)) continue;
                if (latest == null || at.isAfter(latest)) { latest = at; last = event; }
                String metric = STAGES.get(text(event.get("state")));
                if (metric != null && (!first.containsKey(metric) || at.isBefore(time(first.get(metric).get("recordTime"))))) first.put(metric, event);
            }
            annotate(row, latest, last.get("remark"), last.getOrDefault("operatorName", "历史未记录"), created, now);
            for (var e : first.entrySet()) {
                var event = new LinkedHashMap<>(row); event.put("operatorName", e.getValue().getOrDefault("operatorName", "历史未记录"));
                milestone(details, e.getKey(), event, time(e.getValue().get("recordTime")), text(e.getValue().get("state")) + "：" + text(e.getValue().get("remark")), period, now);
            }
            milestone(details, "newOpportunities", row, created, "商机建档", period, now);
            if (!CLOSED.contains(stage)) {
                details.get("activeOpportunities").add(row);
                LocalDateTime base = latest == null ? created : latest;
                if (base != null && !base.plusDays(num(rules.get("stagnantOpportunityDays"))).isAfter(now)) {
                    row.put("reason", "商机连续" + rules.get("stagnantOpportunityDays") + "天未推进，普通编辑不重置计时"); details.get("stagnantOpportunities").add(row);
                }
            }
        }
        // The first signed version is the contract acquisition fact. Later revisions cannot rewrite it.
        var versions = rows("""
                SELECT v.* FROM oa_crm_contract_version v JOIN oa_crm_contract c ON c.id=v.contract_id
                WHERE c.tenant_id=? AND c.deleted=0 AND v.signed_date IS NOT NULL ORDER BY v.version_no,v.id
                """, tenant).stream().collect(Collectors.groupingBy(v -> num(v.get("contractId"))));
        for (var c : rows("SELECT * FROM oa_crm_contract WHERE tenant_id=? AND deleted=0", tenant)) {
            var customer = customers.get(num(c.get("customerId"))); if (customer == null) continue;
            var row = record("contract", num(c.get("id")), num(customer.get("ownerUserId")), num(c.get("customerId")), text(c.get("name")), users);
            if (!matches(query, row)) continue;
            row.put("customerName", customer.get("name")); row.put("stage", c.get("status"));
            var signed = versions.getOrDefault(num(c.get("id")), List.of());
            if (signed.isEmpty()) {
                if (Set.of("draft", "pending", "approved", "rejected", "withdrawn").contains(text(c.get("status")))) details.get("pendingContracts").add(row);
                continue;
            }
            var first = signed.get(0); var snapshot = parse(first.get("snapshotJson"));
            Object amount = snapshot.get("amount");
            if (amount == null && num(c.get("versionNo")) == num(first.get("versionNo")) && num(c.get("effectiveVersion")) == num(first.get("versionNo"))) amount = c.get("amount");
            row.put("amount", amount == null ? null : decimal(amount));
            row.put("operatorName", "历史未记录");
            String basis = "首次签署版本 V" + first.get("versionNo") + (amount == null ? "，历史快照缺少金额，仅计签约数量" : "，按该版本金额计入");
            milestone(details, "signedContracts", row, time(first.get("signedDate")), basis, period, now);
            if (amount != null) milestone(details, "signedAmount", row, time(first.get("signedDate")), basis, period, now);
        }
        for (var f : rows("""
                SELECT f.*,u.nick_name AS operator_name FROM oa_finance_record f JOIN oa_merchant m ON m.id=f.customer_id
                LEFT JOIN sys_user u ON u.user_name=f.create_by AND u.tenant_id=m.tenant_id
                WHERE m.tenant_id=? AND f.status='confirmed' AND f.record_type IN ('receipt','refund','reversal')
                """, tenant)) {
            var customer = customers.get(num(f.get("customerId"))); if (customer == null) continue;
            var row = record("finance", num(f.get("id")), num(customer.get("ownerUserId")), num(f.get("customerId")), "已确认" + Map.of("receipt", "回款", "refund", "退款", "reversal", "冲销").get(text(f.get("recordType"))), users);
            if (!matches(query, row)) continue;
            row.put("contractId", f.get("contractId")); row.put("customerName", customer.get("name")); row.put("operatorName", f.get("operatorName"));
            row.put("amount", decimal(f.get("amount")).multiply("receipt".equals(f.get("recordType")) ? BigDecimal.ONE : BigDecimal.ONE.negate()));
            milestone(details, "receivedAmount", row, time(f.get("businessDate")), text(row.get("name")), period, now);
        }
        details.values().forEach(list -> list.sort(Comparator.comparing((Map<String,Object> r) -> text(r.get("metricTime"))).reversed().thenComparing(r -> text(r.get("key")))));
        List<Map<String,Object>> people = new ArrayList<>();
        for (var u : users.values()) {
            var person = new LinkedHashMap<>(u); if (!matches(query, person)) continue;
            long id = num(u.get("userId"));
            if (num(query.get("customerId")) > 0 && details.values().stream().flatMap(Collection::stream).noneMatch(r -> num(r.get("userId")) == id)) continue;
            METRICS.forEach(k -> person.put(k, value(k, details.get(k).stream().filter(r -> num(r.get("userId")) == id).toList())));
            person.put("lastFollowupTime", details.get("customers").stream().filter(r -> num(r.get("userId")) == id).map(r -> text(r.get("lastFollowupTime"))).max(String::compareTo).orElse(""));
            people.add(person);
        }
        return new Snapshot(people, details, options, rules, period, now);
    }

    private Set<Long> salesDepartments(long tenant) {
        var departments = rows("SELECT dept_id,parent_id FROM sys_dept WHERE tenant_id=? AND status='0' AND del_flag='0'", tenant);
        Set<Long> result = new HashSet<>();
        if (departments.stream().noneMatch(d -> num(d.get("deptId")) == SALES_DEPARTMENT_ID)) return result;
        result.add(SALES_DEPARTMENT_ID);
        boolean added;
        do {
            added = false;
            for (var d : departments) if (result.contains(num(d.get("parentId")))) added |= result.add(num(d.get("deptId")));
        } while (added);
        return result;
    }

    private Map<String,Object> record(String type, long id, long owner, long customer, String name, Map<Long,Map<String,Object>> users) {
        var r = new LinkedHashMap<String,Object>(); var u = users.get(owner);
        r.put("key", type + ":" + id); r.put("entityType", type); r.put("recordId", id); r.put("customerId", customer);
        r.put("name", name); r.put("customerName", name); r.put("userId", owner); r.put("userName", u.get("userName")); r.put("deptId", u.get("deptId")); r.put("deptName", u.get("deptName"));
        return r;
    }
    private void annotate(Map<String,Object> r, LocalDateTime last, Object content, Object operator, LocalDateTime since, LocalDateTime now) {
        r.put("lastFollowupTime", last); r.put("lastFollowupContent", content); r.put("operatorName", operator); r.put("trackingSince", since);
        LocalDateTime base = last == null ? since : last;
        r.put("idleDays", base == null ? null : Math.max(0, ChronoUnit.DAYS.between(base, now)));
    }
    private void milestone(Map<String,List<Map<String,Object>>> details, String metric, Map<String,Object> row, LocalDateTime at, String content, Period period, LocalDateTime now) {
        if (!period.includes(at, now)) return;
        var r = new LinkedHashMap<>(row); r.put("metricTime", at); r.put("metricContent", content); details.get(metric).add(r);
    }
    private Object value(String metric, List<Map<String,Object>> rows) {
        return Set.of("signedAmount", "receivedAmount").contains(metric) ? rows.stream().map(r -> decimal(r.get("amount"))).reduce(BigDecimal.ZERO, BigDecimal::add) : rows.size();
    }
    private boolean matches(Map<String,String> query, Map<String,Object> row) {
        for (String key : List.of("deptId", "userId", "customerId")) if (num(query.get(key)) > 0 && row.containsKey(key) && num(query.get(key)) != num(row.get(key))) return false;
        return true;
    }
    private Map<String,Object> options(Map<Long,Map<String,Object>> users, Map<Long,Map<String,Object>> customers, List<Map<String,Object>> opportunities) {
        Map<Long,Map<String,Object>> depts = new LinkedHashMap<>(), cs = new LinkedHashMap<>();
        users.values().forEach(u -> depts.put(num(u.get("deptId")), Map.of("id", num(u.get("deptId")), "name", text(u.get("deptName")))));
        customers.values().forEach(c -> cs.put(num(c.get("id")), Map.of("id", num(c.get("id")), "name", text(c.get("name")))));
        opportunities.stream().filter(o -> num(o.get("customerId")) > 0).forEach(o -> cs.putIfAbsent(num(o.get("customerId")), Map.of("id", num(o.get("customerId")), "name", text(o.get("customerName")))));
        return Map.of("departments", depts.values(), "users", users.values().stream().map(u -> Map.of("id", num(u.get("userId")), "name", text(u.get("userName")), "deptId", num(u.get("deptId")))).toList(), "customers", cs.values());
    }
    private List<Map<String,Object>> rows(String sql, Object... args) { return JdbcMaps.camelCase(jdbc.queryForList(sql, args)); }
    private Map<String,Object> parse(Object v) { try { return v == null ? new LinkedHashMap<>() : json.readValue(v.toString(), new TypeReference<LinkedHashMap<String,Object>>() {}); } catch (Exception e) { return new LinkedHashMap<>(); } }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> history(Object v) { return v instanceof List<?> list ? list.stream().filter(Map.class::isInstance).map(r -> (Map<String,Object>)r).toList() : List.of(); }
    private static String text(Object v) { return Objects.toString(v, ""); }
    private static long num(Object v) { try { return Long.parseLong(text(v)); } catch (Exception e) { return 0; } }
    private static BigDecimal decimal(Object v) { try { return new BigDecimal(text(v)); } catch (Exception e) { return BigDecimal.ZERO; } }
    private static LocalDateTime time(Object v) {
        if (v == null) return null;
        if (v instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        if (v instanceof java.sql.Date d) return d.toLocalDate().atStartOfDay();
        String s = text(v).replace(' ', 'T');
        try { return s.length() == 10 ? LocalDate.parse(s).atStartOfDay() : LocalDateTime.parse(s); } catch (Exception e) { return null; }
    }
    private static LocalDate date(String v, LocalDate fallback) { try { return v == null || v.isBlank() ? fallback : LocalDate.parse(v); } catch (Exception e) { throw new ApiException(400, "日期格式须为YYYY-MM-DD"); } }
    private static int positive(String v, int max) { try { int n = Integer.parseInt(v); if (n > 0 && n <= max) return n; } catch (Exception ignored) {} throw new ApiException(400, "分页参数无效"); }
}
