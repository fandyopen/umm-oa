package com.seal.oa.workflow;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.JdbcMaps;
import com.seal.oa.lowcode.LowCodeWorkflowService;
import com.seal.oa.system.SystemService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class ApprovalInboxService {
    public static final long MODULE = 341600L;
    private final JdbcTemplate jdbc;
    private final SystemService system;
    private final LowCodeWorkflowService workflows;
    private final ApprovalDecisionService decisions;

    private static final String FROM = """
            FROM oa_process_task t JOIN oa_process_instance i ON i.id=t.instance_id
            LEFT JOIN sys_user au ON au.user_id=t.assignee_id AND au.tenant_id=i.tenant_id
            LEFT JOIN sys_dept ad ON ad.dept_id=au.dept_id AND ad.tenant_id=i.tenant_id
            LEFT JOIN sys_user su ON su.user_id=i.starter_id AND su.tenant_id=i.tenant_id
            LEFT JOIN sys_dept sd ON sd.dept_id=su.dept_id AND sd.tenant_id=i.tenant_id
            LEFT JOIN oa_workflow_request r ON i.business_type='workflowAction' AND r.id=i.business_id AND r.tenant_id=i.tenant_id
            LEFT JOIN sys_module m ON m.module_id=r.module_id AND m.tenant_id=i.tenant_id
            LEFT JOIN oa_approval_decision a ON a.task_id=t.id AND a.tenant_id=i.tenant_id
            """;
    private static final String TITLE = "COALESCE(r.title,(SELECT MIN(td.title) FROM oa_todo td WHERE td.task_id=t.id),t.task_name)";
    private static final String COLUMNS = """
            t.id AS task_id,t.instance_id,t.task_name,t.assignee_id,t.assignee_name,t.status AS task_status,
            t.create_time AS push_time,t.complete_time,i.starter_id,i.starter_name,i.start_time,i.status AS instance_status,
            COALESCE(a.assignee_dept_id,au.dept_id) AS approver_dept_id,
            COALESCE(a.assignee_dept_name,ad.dept_name) AS approver_dept_name,sd.dept_name AS starter_dept_name,
            m.module_name AS business_label,r.action_code,r.module_id,i.business_type,
            a.operator_id,a.operator_name,a.operator_dept_name,a.decision,a.delegated,
            """ + TITLE + " AS title ";

    public ApprovalInboxService(JdbcTemplate jdbc, SystemService system, LowCodeWorkflowService workflows,
                                ApprovalDecisionService decisions) {
        this.jdbc=jdbc; this.system=system; this.workflows=workflows; this.decisions=decisions;
    }

    private SystemService.UserAccount requireAccess() {
        var user = system.requireCurrentUser();
        if (!"0".equals(user.status()) || !system.hasModuleAccess(user.userId(), MODULE))
            throw new ApiException(403, "没有待审批页面访问权限");
        return user;
    }

    public Map<String, Object> options() {
        var user = requireAccess();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("canApprove", system.hasModuleAction(user.userId(), MODULE, "approve"));
        result.put("departments", JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT dept_id,parent_id,dept_name FROM sys_dept WHERE tenant_id=? AND del_flag='0' ORDER BY order_num,dept_id
                """, user.tenantId())));
        result.put("users", JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT u.user_id,u.nick_name,u.user_name,d.dept_name FROM sys_user u
                LEFT JOIN sys_dept d ON d.dept_id=u.dept_id AND d.tenant_id=u.tenant_id
                WHERE u.tenant_id=? AND u.del_flag='0' ORDER BY u.nick_name,u.user_id
                """, user.tenantId())));
        result.put("businesses", JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT DISTINCT m.module_id,m.module_name FROM oa_workflow_request r
                JOIN sys_module m ON m.module_id=r.module_id AND m.tenant_id=r.tenant_id
                WHERE r.tenant_id=? ORDER BY m.module_name
                """, user.tenantId())));
        return result;
    }

    public Map<String, Object> list(Map<String, String> query) {
        var user = requireAccess();
        StringBuilder where = new StringBuilder(" WHERE i.tenant_id=? AND (i.process_key LIKE 'business:%' OR i.process_key LIKE 'form:%')");
        List<Object> args = new ArrayList<>(List.of(user.tenantId()));
        String tab = query.getOrDefault("tab", "todo");
        switch (tab) {
            case "todo" -> where.append(" AND t.status='todo' AND i.status='running'");
            case "done" -> where.append(" AND t.status='done'");
            case "all" -> { }
            default -> throw new ApiException(400, "审批列表类型无效");
        }
        String keyword = query.getOrDefault("keyword", "").trim().toLowerCase(Locale.ROOT);
        if (!keyword.isEmpty()) {
            where.append(" AND LOWER(CONCAT(").append(TITLE).append(", ' ',COALESCE(i.starter_name,''),' ',COALESCE(t.assignee_name,''),' ',COALESCE(a.operator_name,''))) LIKE ?");
            args.add("%" + keyword + "%");
        }
        for (String key : List.of("assigneeId", "starterId", "moduleId")) {
            String value = query.getOrDefault(key, "");
            if (value.isBlank()) continue;
            where.append(" AND ").append(switch (key) { case "assigneeId" -> "t.assignee_id"; case "starterId" -> "i.starter_id"; default -> "r.module_id"; }).append("=?");
            args.add(positive(value));
        }
        if (!query.getOrDefault("deptId", "").isBlank()) {
            Set<Long> departments = departmentIds(user.tenantId(), positive(query.get("deptId")));
            where.append(" AND COALESCE(a.assignee_dept_id,au.dept_id) IN (")
                    .append(String.join(",", Collections.nCopies(departments.size(), "?"))).append(")");
            args.addAll(departments);
        }
        for (String key : List.of("startDate", "endDate")) {
            if (query.getOrDefault(key, "").isBlank()) continue;
            try {
                LocalDate day = LocalDate.parse(query.get(key));
                where.append(" AND i.start_time ").append("startDate".equals(key) ? ">=?" : "<?");
                args.add(java.sql.Timestamp.valueOf(("endDate".equals(key) ? day.plusDays(1) : day).atStartOfDay()));
            } catch (RuntimeException error) { throw new ApiException(400, "发起日期格式无效"); }
        }
        int size = (int) Math.min(100, positive(query.getOrDefault("pageSize", "15")));
        long page = positive(query.getOrDefault("pageNum", "1"));
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + FROM + where, Long.class, args.toArray());
        args.add(size); args.add(Math.min((page - 1) * size, Integer.MAX_VALUE));
        var rows = JdbcMaps.camelCase(jdbc.queryForList("SELECT " + COLUMNS + FROM + where
                + " ORDER BY COALESCE(t.complete_time,t.create_time) DESC,t.id DESC LIMIT ? OFFSET ?", args.toArray()));
        boolean allowed = system.hasModuleAction(user.userId(), MODULE, "approve");
        boolean selfApprovalAllowed = system.isSuperAdminIdentity(user.userId());
        for (var row : rows) {
            row.put("canApprove", allowed && pending(row) && (selfApprovalAllowed || number(row.get("starterId")) != user.userId()));
            if (row.get("operatorName") == null && "done".equals(row.get("taskStatus"))) {
                Map<String, Object> audit = decisions.find(text(row.get("taskId")));
                row.put("operatorName", audit.get("operatorName"));
                row.put("operatorId", audit.get("operatorId"));
            }
            row.put("businessLabel", row.get("businessLabel") == null ? "表单审批" : row.get("businessLabel"));
        }
        return Map.of("rows", rows, "total", total == null ? 0L : total, "canApprove", allowed);
    }

    public Map<String, Object> detail(String taskId) {
        var user = requireAccess();
        var rows = JdbcMaps.camelCase(jdbc.queryForList("SELECT " + COLUMNS + FROM + " WHERE t.id=? AND i.tenant_id=?", taskId, user.tenantId()));
        if (rows.isEmpty()) throw new ApiException(404, "审批任务不存在");
        var summary = rows.get(0);
        var detail = workflows.approvalDetail(taskId, user.tenantId());
        boolean canApprove = system.hasModuleAction(user.userId(), MODULE, "approve") && pending(summary)
                && (system.isSuperAdminIdentity(user.userId()) || number(summary.get("starterId")) != user.userId());
        detail.put("approvalInbox", true);
        detail.put("canApprove", canApprove);
        detail.put("approvalSummary", summary);
        var records = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT t.id AS task_id,t.task_name,t.assignee_id,t.assignee_name,t.status AS task_status,
                    t.create_time,t.complete_time,COALESCE(a.assignee_dept_name,d.dept_name) AS assignee_dept_name
                FROM oa_process_task t JOIN oa_process_instance i ON i.id=t.instance_id
                LEFT JOIN sys_user u ON u.user_id=t.assignee_id AND u.tenant_id=i.tenant_id
                LEFT JOIN sys_dept d ON d.dept_id=u.dept_id AND d.tenant_id=i.tenant_id
                LEFT JOIN oa_approval_decision a ON a.task_id=t.id
                WHERE t.instance_id=? AND i.tenant_id=? ORDER BY t.create_time,t.id
                """, summary.get("instanceId"), user.tenantId()));
        for (var record : records) record.put("audit", decisions.find(text(record.get("taskId"))));
        detail.put("approvalRecords", records);
        return detail;
    }

    @Transactional
    public Map<String, Object> decide(Map<String, Object> body) {
        var user = requireAccess();
        if (!system.hasModuleAction(user.userId(), MODULE, "approve")) throw new ApiException(403, "没有代审批权限");
        String taskId = text(body.get("taskId"));
        var tasks = jdbc.queryForList("""
                SELECT t.* FROM oa_process_task t JOIN oa_process_instance i ON i.id=t.instance_id
                WHERE t.id=? AND i.tenant_id=?
                  AND (i.process_key LIKE 'business:%' OR i.process_key LIKE 'form:%') FOR UPDATE
                """, taskId, user.tenantId());
        if (tasks.isEmpty()) throw new ApiException(404, "审批任务不存在");
        var task = tasks.get(0);
        var instance = jdbc.queryForMap("SELECT * FROM oa_process_instance WHERE id=? FOR UPDATE", task.get("instance_id"));
        if (!"todo".equals(task.get("status")) || !"running".equals(instance.get("status")))
            throw new ApiException(409, "审批已办理或撤回，请刷新后重试");
        boolean selfApprovalAllowed = system.isSuperAdminIdentity(user.userId());
        if (!selfApprovalAllowed && number(instance.get("starter_id")) == user.userId()) throw new ApiException(403, "不能审批本人发起的申请");
        if (!(body.get("pass") instanceof Boolean pass)) throw new ApiException(400, "请选择同意或驳回");
        String comment = text(body.get("comment")).trim();
        if (comment.length() > 2000) throw new ApiException(400, "审批意见最多2000字");
        if (!pass && comment.isBlank()) throw new ApiException(400, "驳回时请填写审批意见");
        if (comment.isBlank()) comment = "同意";
        Map<String, Object> completion = Map.of("taskId", taskId, "instanceId", instance.get("id"),
                "variables", Map.of("pass", pass, "comment", comment));
        return WorkflowApprovalContext.decide(text(instance.get("id")), taskId, AuthContext.userId(),
                number(task.get("assignee_id")), selfApprovalAllowed, () -> workflows.complete(completion, user.userId()));
    }

    private Set<Long> departmentIds(long tenant, long selected) {
        var rows = jdbc.queryForList("SELECT dept_id,parent_id FROM sys_dept WHERE tenant_id=? AND del_flag='0'", tenant);
        if (rows.stream().noneMatch(row -> number(row.get("dept_id")) == selected)) throw new ApiException(400, "部门不存在");
        Set<Long> ids = new LinkedHashSet<>(List.of(selected));
        boolean changed;
        do {
            changed = false;
            for (var row : rows) if (ids.contains(number(row.get("parent_id")))) changed |= ids.add(number(row.get("dept_id")));
        } while (changed);
        return ids;
    }

    private boolean pending(Map<String, Object> row) { return "todo".equals(row.get("taskStatus")) && "running".equals(row.get("instanceStatus")); }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static long number(Object value) { return value instanceof Number n ? n.longValue() : 0L; }
    private static long positive(String value) {
        try { long number = Long.parseLong(value); if (number > 0) return number; }
        catch (RuntimeException ignored) { }
        throw new ApiException(400, "筛选或分页参数无效");
    }
}
