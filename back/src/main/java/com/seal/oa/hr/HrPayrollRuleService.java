package com.seal.oa.hr;

import static com.seal.oa.hr.HrData.*;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/** Company rules are immutable versions; payrolls store the chosen rules and their provenance. */
@Service
public class HrPayrollRuleService {
    static final List<String> FIELDS = List.of("hoursPerDay", "personalPaidRate", "sickPaidRate",
            "absencePaidRate", "overtimeMultiplier", "latePenalty", "earlyPenalty");
    private final HrData d;

    public HrPayrollRuleService(HrData d) { this.d = d; }

    long tenant() { return d.system.requireCurrentUser().tenantId(); }

    Map<String, Object> decorate(Map<String, Object> row) {
        row.put("rules", d.object(row.remove("rulesJson")));
        return row;
    }

    public Map<String, Object> list(String month) {
        d.require(PAYROLL, "access");
        String selected = period(month);
        var versions = d.rows("SELECT r.*,u.nick_name AS creator_name FROM oa_hr_payroll_rule r "
                + "LEFT JOIN sys_user u ON u.user_id=r.create_by WHERE r.tenant_id=? ORDER BY r.version_no DESC", tenant())
                .stream().map(this::decorate).toList();
        var result = new LinkedHashMap<String, Object>();
        result.put("versions", versions);
        result.put("latestVersion", versions.isEmpty() ? 0 : versions.get(0).get("versionNo"));
        result.put("applicableRule", effective(selected));
        return result;
    }

    Map<String, Object> effective(String month) {
        var rows = d.rows("SELECT * FROM oa_hr_payroll_rule WHERE tenant_id=? AND effective_month<=? "
                + "ORDER BY effective_month DESC,version_no DESC LIMIT 1", tenant(), period(month));
        return rows.isEmpty() ? null : decorate(rows.get(0));
    }

    @Transactional
    public Map<String, Object> save(Map<String, Object> body) {
        d.require(PAYROLL, "rule_manage");
        long tenant = tenant();
        // Serialize version allocation and reject stale editors without changing any saved version.
        d.one("SELECT tenant_id FROM sys_tenant WHERE tenant_id=? FOR UPDATE", tenant);
        int latest = d.jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0) FROM oa_hr_payroll_rule WHERE tenant_id=?", Integer.class, tenant);
        if (!body.containsKey("baseVersion") || id(body.get("baseVersion")) != latest)
            throw new ApiException(409, "计薪规则已更新，请刷新后重新保存");
        String name = required(body.get("name"), "规则名称", 100);
        String month = period(body.get("effectiveMonth"));
        String reason = required(body.get("reason"), "规则设置或变更说明", 1000);
        var rules = validate(d.object(body.get("rules")));
        long id = d.insert("INSERT INTO oa_hr_payroll_rule(tenant_id,version_no,name,effective_month,rules_json,reason,create_by) VALUES(?,?,?,?,?,?,?)",
                tenant, latest + 1, name, month, d.json(rules), reason, AuthContext.userId());
        d.history("payrollRule", id, null, "保存公司计薪规则版本", Map.of("versionNo", latest + 1, "effectiveMonth", month, "rules", rules, "reason", reason));
        return decorate(d.one("SELECT * FROM oa_hr_payroll_rule WHERE id=? AND tenant_id=?", id, tenant));
    }

    Map<String, Object> validate(Map<String, Object> input) {
        var rules = new LinkedHashMap<String, Object>();
        for (String key : FIELDS) {
            if (input.get(key) == null || text(input.get(key)).isBlank())
                throw new ApiException(400, "请明确填写全部公司计薪参数");
            BigDecimal value = nonnegative(input.get(key), "计薪参数 " + key);
            if ("hoursPerDay".equals(key) && (value.signum() == 0 || value.compareTo(new BigDecimal("24")) > 0))
                throw new ApiException(400, "每日计薪工时须大于0且不超过24小时");
            if (key.endsWith("PaidRate") && value.compareTo(BigDecimal.ONE) > 0)
                throw new ApiException(400, "请假及缺勤计薪比例须在0至1之间");
            if (value.scale() > 4 || value.precision() - value.scale() > 10)
                throw new ApiException(400, "计薪参数最多支持4位小数、10位整数");
            rules.put(key, value.stripTrailingZeros());
        }
        return rules;
    }

    boolean same(Map<String, Object> first, Map<String, Object> second) {
        var a = validate(first);
        var b = validate(second);
        return FIELDS.stream().allMatch(k -> ((BigDecimal) a.get(k)).compareTo((BigDecimal) b.get(k)) == 0);
    }

    Map<String, Object> resolve(String month, Map<String, Object> body) {
        var rule = effective(month);
        if (rule == null) throw new ApiException(400, "本月尚未设置公司计薪规则，请在工资管理的计薪规则中设置生效月份");
        if (body.containsKey("ruleId") && id(body.get("ruleId")) != id(rule.get("id")))
            throw new ApiException(409, "本月适用计薪规则已更新，请重新打开核算窗口");
        var snapshot = new LinkedHashMap<>(d.object(rule.get("rules")));
        if (body.containsKey("rules") && !same(d.object(body.get("rules")), snapshot))
            throw new ApiException(400, "计薪参数须引用公司规则，请先维护计薪规则后再核算");
        snapshot.put("_rule", Map.of("id", rule.get("id"), "versionNo", rule.get("versionNo"),
                "name", rule.get("name"), "effectiveMonth", rule.get("effectiveMonth")));
        return snapshot;
    }

    Map<String, Object> forRecalculation(String month, Map<String, Object> body, Map<String, Object> original) {
        if (body.containsKey("ruleId")) return resolve(month, body);
        if (body.containsKey("rules") && !same(d.object(body.get("rules")), original))
            throw new ApiException(400, "请明确选择本月适用的公司计薪规则，或沿用本批次原规则");
        validate(original);
        return original;
    }

    boolean changed(String month, Map<String, Object> snapshot) {
        var rule = effective(month);
        return rule != null && (snapshot.get("_rule") == null
                || id(d.object(snapshot.get("_rule")).get("id")) != id(rule.get("id")));
    }
}
