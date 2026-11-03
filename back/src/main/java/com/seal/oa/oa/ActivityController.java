package com.seal.oa.oa;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiResult;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.JdbcMaps;
import com.seal.oa.lowcode.LowCodeWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.Locale;

@RestController
public class ActivityController {

    private final JdbcTemplate jdbc;
    private final ParityRecordService parityRecords;
    private final LowCodeWorkflowService workflowService;

    public ActivityController(
            JdbcTemplate jdbc,
            ParityRecordService parityRecords,
            LowCodeWorkflowService workflowService
    ) {
        this.jdbc = jdbc;
        this.parityRecords = parityRecords;
        this.workflowService = workflowService;
    }

    @GetMapping("/project/activity/taskWaitList")
    public Map<String, Object> waitingTasks(@RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam Map<String, String> query) {
        if ("contract".equals(query.get("businessCategory")) || !value(query.get("queryAll"), "").isBlank()) {
            return filteredWaitingTasksPage(pageNum, pageSize, query);
        }
        return tasksByStatus("todo", pageNum, pageSize);
    }

    @GetMapping("/project/activity/taskDoneList")
    public Map<String, Object> completedTasks(@RequestParam(defaultValue = "1") int pageNum,
                                              @RequestParam(defaultValue = "10") int pageSize) {
        return tasksByStatus("done", pageNum, pageSize);
    }

    @GetMapping("/project/activity/taskCopyList")
    public Map<String, Object> copiedTasks(@RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "10") int pageSize) {
        return tasksByStatus("copy", pageNum, pageSize);
    }

    @GetMapping("/project/activity/started")
    public Map<String, Object> startedTasks() {
        List<Map<String, Object>> rows = new ArrayList<>(JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT id, tenant_id, user_id, title, business_type, business_id,
                       process_instance_id, task_id, status, create_time
                FROM oa_todo
                WHERE user_id = ? AND process_instance_id IS NOT NULL
                  AND NOT EXISTS (
                      SELECT 1 FROM oa_process_instance i
                      WHERE i.id = oa_todo.process_instance_id
                  )
                ORDER BY id DESC
                """, AuthContext.userId())));
        rows.addAll(workflowService.tasks("start", AuthContext.userId()));
        return ApiResult.page(rows, rows.size());
    }

    @PostMapping({"/project/activity/complete", "/project/activity/end/process",
            "/project/activity/back", "/project/activity/delegate"})
    public Map<String, Object> completeTask(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        if (request.getRequestURI().endsWith("/delegate")
                && workflowService.isContractApprovalTask(value(body.get("taskId"), ""))) {
            throw new ApiException(409, "合同审批须按已发布流程的节点办理，暂不支持临时转办");
        }
        long protectedId = number(body.get("todoId"), number(body.get("id"), 0L));
        List<String> protectedTypes = jdbc.queryForList(
                "SELECT business_type FROM oa_todo WHERE id = ? OR task_id = ?", String.class, protectedId, value(body.get("taskId"), ""));
        if (protectedTypes.contains(CustomerFollowupReminderService.BUSINESS_TYPE)) {
            throw new ApiException(409, "请进入客户列表新增跟进，跟进保存成功后待办自动完成");
        }
        if (protectedTypes.stream().anyMatch(type -> "crmRisk".equals(type) || "crmContractApproval".equals(type))) {
            throw new ApiException(409, "客户风险请处理对应业务，历史合同审批请在待办详情办理，完成后待办自动关闭");
        }
        if ("lowcode".equals(value(body.get("type"), ""))
                || workflowService.isManagedTask(
                        value(body.get("taskId"), ""),
                        value(body.get("instanceId"), "")
                )) {
            Map<String, Object> completion = new LinkedHashMap<>(body);
            if (body.get("variablesStr") != null) {
                completion.put("variables", value(body.get("variablesStr"), "{}"));
            } else if (body.get("variables") instanceof Map<?, ?> supplied) {
                Map<String, Object> variables = new LinkedHashMap<>();
                supplied.forEach((key, value) -> variables.put(String.valueOf(key), value));
                variables.putIfAbsent("comment", value(body.get("comment"), "同意"));
                if (request.getRequestURI().endsWith("/back") || request.getRequestURI().endsWith("/end/process")) {
                    variables.put("pass", false);
                } else {
                    variables.putIfAbsent("pass", true);
                }
                completion.put("variables", variables);
            } else {
                Map<String, Object> variables = new LinkedHashMap<>();
                variables.put("comment", value(body.get("comment"), "同意"));
                variables.put(
                        "pass",
                        request.getRequestURI().endsWith("/back")
                                || request.getRequestURI().endsWith("/end/process")
                                ? "false"
                                : "true"
                );
                completion.put("variables", variables);
            }
            return ApiResult.ok(workflowService.complete(completion, AuthContext.userId()));
        }
        long todoId = number(body.get("todoId"), number(body.get("id"), 0L));
        if (todoId > 0L) {
            jdbc.update("UPDATE oa_todo SET status = 'done' WHERE id = ?", todoId);
        }
        return ApiResult.ok();
    }

    private Map<String, Object> tasksByStatus(String status, int pageNum, int pageSize) {
        if ("todo".equals(status)) {
            return waitingTasksPage(pageNum, pageSize);
        }
        List<Map<String, Object>> rows = new ArrayList<>(JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT id, tenant_id, user_id, title, business_type, business_id,
                       process_instance_id, task_id, status, create_time
                FROM oa_todo
                WHERE user_id = ? AND status = ?
                  AND NOT EXISTS (
                      SELECT 1 FROM oa_process_instance i
                      WHERE i.id = oa_todo.process_instance_id
                  )
                ORDER BY id DESC
                """, AuthContext.userId(), status)));
        if ("todo".equals(status) || "done".equals(status)) {
            rows.addAll(workflowService.tasks(status, AuthContext.userId()));
        }
        if (rows.isEmpty() && "todo".equals(status)) {
            rows = parityRecords.rows("todo:wait:" + AuthContext.username());
        }
        int total = rows.size();
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        int from = Math.min((safePage - 1) * safeSize, total);
        int to = Math.min(from + safeSize, total);
        decorateCrmTasks(rows);
        return ApiResult.page(rows.subList(from, to), total);
    }

    private Map<String, Object> waitingTasksPage(int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        int candidateLimit = safePage * safeSize;
        Long legacyCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM oa_todo
                WHERE user_id = ? AND status = 'todo'
                  AND NOT EXISTS (
                      SELECT 1 FROM oa_process_instance i
                      WHERE i.id = oa_todo.process_instance_id
                  )
                """, Long.class, AuthContext.userId());
        List<Map<String, Object>> rows = new ArrayList<>(JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT id, tenant_id, user_id, title, business_type, business_id,
                       process_instance_id, task_id, status, create_time
                FROM oa_todo
                WHERE user_id = ? AND status = 'todo'
                  AND NOT EXISTS (
                      SELECT 1 FROM oa_process_instance i
                      WHERE i.id = oa_todo.process_instance_id
                  )
                ORDER BY create_time DESC, id DESC
                LIMIT ?
                """, AuthContext.userId(), candidateLimit)));
        rows.addAll(workflowService.tasks("todo", AuthContext.userId(), candidateLimit));
        rows.sort(Comparator.comparing(this::createTime, Comparator.nullsLast(Comparator.reverseOrder())));

        long total = (legacyCount == null ? 0L : legacyCount)
                + workflowService.waitingCount(AuthContext.userId());
        if (total == 0L) {
            rows = parityRecords.rows("todo:wait:" + AuthContext.username());
            total = rows.size();
        }
        int from = Math.min((safePage - 1) * safeSize, rows.size());
        int to = Math.min(from + safeSize, rows.size());
        decorateCrmTasks(rows);
        return ApiResult.page(rows.subList(from, to), total);
    }

    private Map<String, Object> filteredWaitingTasksPage(int pageNum, int pageSize, Map<String, String> query) {
        List<Map<String, Object>> rows = new ArrayList<>(JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT id, tenant_id, user_id, title, business_type, business_id,
                       process_instance_id, task_id, status, create_time
                FROM oa_todo
                WHERE user_id = ? AND status = 'todo'
                  AND NOT EXISTS (
                      SELECT 1 FROM oa_process_instance i WHERE i.id = oa_todo.process_instance_id
                  )
                ORDER BY create_time DESC, id DESC
                """, AuthContext.userId())));
        rows.addAll(workflowService.tasks("todo", AuthContext.userId()));
        if (rows.isEmpty()) rows = new ArrayList<>(parityRecords.rows("todo:wait:" + AuthContext.username()));
        rows.sort(Comparator.comparing(this::createTime, Comparator.nullsLast(Comparator.reverseOrder())));
        decorateCrmTasks(rows);
        int unfilteredTotal = rows.size();
        String keyword = value(query.get("queryAll"), "").trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> filtered = rows.stream()
                .filter(row -> !"contract".equals(query.get("businessCategory")) || isContractApproval(row))
                .filter(row -> keyword.isEmpty() || List.of("title", "processName", "taskName", "applyUserName", "form").stream()
                        .anyMatch(key -> value(row.get(key), "").toLowerCase(Locale.ROOT).contains(keyword)))
                .toList();
        int size = Math.min(100, Math.max(1, pageSize));
        int from = (int) Math.min((long) (Math.max(1, pageNum) - 1) * size, filtered.size());
        Map<String, Object> result = ApiResult.page(filtered.subList(from, Math.min(from + size, filtered.size())), filtered.size());
        result.put("unfilteredTotal", unfilteredTotal);
        return result;
    }

    private boolean isContractApproval(Map<String, Object> row) {
        if ("crmContractApproval".equals(row.get("businessType"))) return true;
        return "workflowAction".equals(row.get("businessType"))
                && row.get("form") instanceof Map<?, ?> form && number(form.get("contractId"), 0L) > 0;
    }

    private void decorateCrmTasks(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            String type = value(row.get("businessType"), "");
            boolean customerFollowup = CustomerFollowupReminderService.BUSINESS_TYPE.equals(type);
            if (!customerFollowup && !"crmRisk".equals(type) && !"crmContractApproval".equals(type)) continue;
            Map<String, Object> form = new LinkedHashMap<>();
            form.put("title", row.get("title"));
            long businessId = number(row.get("businessId"), 0L);
            if ("crmContractApproval".equals(type)) form.put("contractId", businessId);
            else {
                form.put("customerId", businessId);
                String taskId = value(row.get("taskId"), "");
                if (taskId.startsWith("crm-contract-")) {
                    String[] parts = taskId.split("-");
                    if (parts.length > 2) form.put("contractId", number(parts[2], 0L));
                }
            }
            row.put("form", form);
            row.put("formId", businessId);
            row.put("type", row.get("status"));
            row.put("processName", customerFollowup ? "客户首次跟进" : "crmRisk".equals(type) ? "客户风险提醒" : "合同审批");
            row.put("taskName", customerFollowup ? "请新增客户跟进" : "crmRisk".equals(type) ? "跟进处理" : "合同及收费审核");
            row.put("applyUserName", "客户与合同管理");
            row.put("lastHistoricActivity", Map.of("comment", "", "assigneeName", ""));
        }
    }

    private Timestamp createTime(Map<String, Object> row) {
        Object value = row.get("createTime");
        return value instanceof Timestamp timestamp ? timestamp : null;
    }

    private long number(Object value, long defaultValue) {
        try {
            return value instanceof Number number
                    ? number.longValue()
                    : Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private String value(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }
}
