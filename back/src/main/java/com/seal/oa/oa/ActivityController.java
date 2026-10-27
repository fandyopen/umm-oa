package com.seal.oa.oa;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiResult;
import com.seal.oa.common.JdbcMaps;
import com.seal.oa.lowcode.LowCodeWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public Map<String, Object> waitingTasks() {
        return tasksByStatus("todo");
    }

    @GetMapping("/project/activity/taskDoneList")
    public Map<String, Object> completedTasks() {
        return tasksByStatus("done");
    }

    @GetMapping("/project/activity/taskCopyList")
    public Map<String, Object> copiedTasks() {
        return tasksByStatus("copy");
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
        if ("lowcode".equals(value(body.get("type"), ""))
                || workflowService.isManagedTask(
                        value(body.get("taskId"), ""),
                        value(body.get("instanceId"), "")
                )) {
            Map<String, Object> completion = new LinkedHashMap<>(body);
            if (body.get("variablesStr") != null) {
                completion.put("variables", value(body.get("variablesStr"), "{}"));
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

    @GetMapping({"/project/regular/menu/list/build", "/project/regular/menu/list", "/project/regular/menu"})
    public Map<String, Object> regularMenus() {
        return ApiResult.ok(List.of());
    }

    @PostMapping("/project/regular/menu")
    public Map<String, Object> addRegularMenu(@RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @DeleteMapping("/project/regular/menu/{menuId}")
    public Map<String, Object> deleteRegularMenu(@PathVariable Long menuId) {
        return ApiResult.ok();
    }

    private Map<String, Object> tasksByStatus(String status) {
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
        return ApiResult.page(rows, rows.size());
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
