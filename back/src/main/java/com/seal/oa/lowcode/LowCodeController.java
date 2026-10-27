package com.seal.oa.lowcode;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import com.seal.oa.common.JdbcMaps;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
public class LowCodeController {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final LowCodeWorkflowService workflowService;
    private final ModuleFlowBindingService flowBindingService;

    public LowCodeController(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            LowCodeWorkflowService workflowService,
            ModuleFlowBindingService flowBindingService
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.workflowService = workflowService;
        this.flowBindingService = flowBindingService;
    }

    @GetMapping("/form/list")
    public Map<String, Object> formList(@RequestParam Map<String, String> query) {
        long tenantAppId = number(query.get("tenantAppId"), 0L);
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT form_key, parent_key, form_type, flow_name, icon, create_time
                FROM oa_form_definition
                WHERE (? = 0 OR tenant_app_id = ?)
                ORDER BY create_time, id
                """, tenantAppId, tenantAppId));
        Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", row.get("formKey"));
            node.put("name", row.get("flowName"));
            node.put("type", row.get("formType"));
            node.put("icon", row.get("icon"));
            node.put("createTime", row.get("createTime"));
            node.put("children", new ArrayList<Map<String, Object>>());
            nodes.put(String.valueOf(row.get("formKey")), node);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> node = nodes.get(String.valueOf(row.get("formKey")));
            String parentKey = row.get("parentKey") == null ? "" : String.valueOf(row.get("parentKey"));
            Map<String, Object> parent = nodes.get(parentKey);
            if (parent == null) {
                roots.add(node);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
                children.add(node);
            }
        }
        Map<String, Object> result = ApiResult.ok();
        result.put("entryList", roots);
        return result;
    }

    @PostMapping("/form/create")
    public Map<String, Object> createForm(@RequestBody Map<String, Object> body) {
        String formKey = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> basicSetting = object(body.get("basicSetting"));
        String flowName = value(basicSetting.get("flowName"), value(body.get("name"), "未命名表单"));
        String type = value(body.get("type"), "form");
        jdbc.update("""
                INSERT INTO oa_form_definition (
                    tenant_id, tenant_app_id, form_key, parent_key, form_type, flow_name,
                    icon, basic_setting, form_data, process_data, status, create_by
                ) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'published', ?)
                """,
                number(body.get("tenantAppId"), 0L),
                formKey,
                parentKey(body.get("parentId")),
                type,
                flowName,
                number(basicSetting.get("flowImg"), 0L),
                json(basicSetting),
                json(body.getOrDefault("formData", Map.of())),
                json(body.getOrDefault("processData", Map.of())),
                AuthContext.username());
        Map<String, Object> result = ApiResult.ok(formKey);
        result.put("formId", formKey);
        return result;
    }

    @PostMapping("/form/detail")
    public Map<String, Object> formDetail(@RequestBody Map<String, Object> body) {
        Map<String, Object> row = requireForm(String.valueOf(body.get("formId")));
        return formResult(row);
    }

    @PostMapping("/form/update")
    public Map<String, Object> updateForm(@RequestBody Map<String, Object> body) {
        String formKey = String.valueOf(body.get("formId"));
        requireForm(formKey);
        Map<String, Object> basicSetting = object(body.get("basicSetting"));
        jdbc.update("""
                UPDATE oa_form_definition SET flow_name = ?, icon = ?, basic_setting = ?,
                    form_data = ?, process_data = ?, update_time = ?
                WHERE form_key = ?
                """,
                value(basicSetting.get("flowName"), "未命名表单"),
                number(basicSetting.get("flowImg"), 0L),
                json(basicSetting),
                json(body.getOrDefault("formData", Map.of())),
                json(body.getOrDefault("processData", Map.of())),
                Timestamp.valueOf(LocalDateTime.now()),
                formKey);
        return ApiResult.ok(formKey);
    }

    @PostMapping("/form/remove")
    public Map<String, Object> removeForm(@RequestBody Map<String, Object> body) {
        String formKey = String.valueOf(body.get("formId"));
        jdbc.update("DELETE FROM oa_form_definition WHERE form_key = ? OR parent_key = ?", formKey, formKey);
        jdbc.update("DELETE FROM oa_form_submission WHERE form_key = ?", formKey);
        return ApiResult.ok();
    }

    @PostMapping("/data/create")
    public Map<String, Object> createData(@RequestBody Map<String, Object> body) {
        jdbc.update("""
                INSERT INTO oa_form_submission (
                    tenant_id, tenant_app_id, form_key, data_json, create_by
                ) VALUES (1, ?, ?, ?, ?)
                """,
                number(body.get("tenantAppId"), 0L),
                value(body.get("formId"), ""),
                json(body.getOrDefault("fields", Map.of())),
                AuthContext.username());
        return ApiResult.ok(jdbc.queryForObject("SELECT MAX(id) FROM oa_form_submission", Long.class));
    }

    @PostMapping("/data/find")
    public Map<String, Object> findData(@RequestBody Map<String, Object> body) {
        String formKey = value(body.get("formId"), "");
        int pageSize = Math.min(Math.max((int) number(body.get("pageSize"), 10L), 1), 200);
        int pageNum = Math.max((int) number(body.get("pageNum"), 1L), 1);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM oa_form_submission WHERE form_key = ?",
                Long.class,
                formKey
        );
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT id, form_key, data_json, create_by, create_time
                FROM oa_form_submission WHERE form_key = ?
                ORDER BY id DESC LIMIT ? OFFSET ?
                """, formKey, pageSize, (pageNum - 1) * pageSize));
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>(object(String.valueOf(row.get("dataJson"))));
            item.put("_id", row.get("id"));
            item.put("createBy", row.get("createBy"));
            item.put("createTime", row.get("createTime"));
            data.add(item);
        }
        Map<String, Object> result = ApiResult.ok();
        result.put("data", data);
        result.put("total", total == null ? 0 : total);
        result.put("header", formHeader(formKey));
        return result;
    }

    @PostMapping("/data/detail")
    public Map<String, Object> dataDetail(@RequestBody Map<String, Object> body) {
        long dataId = number(body.get("dataId"), number(body.get("id"), 0L));
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT id, data_json, create_by, create_time FROM oa_form_submission WHERE id = ?",
                dataId));
        if (rows.isEmpty()) {
            throw new ApiException(404, "Form data not found");
        }
        Map<String, Object> result = ApiResult.ok();
        result.putAll(object(String.valueOf(rows.get(0).get("dataJson"))));
        result.put("_id", dataId);
        result.put("createBy", rows.get(0).get("createBy"));
        result.put("createTime", rows.get(0).get("createTime"));
        return result;
    }

    @PostMapping("/data/remove")
    public Map<String, Object> removeData(@RequestBody Map<String, Object> body) {
        jdbc.update("DELETE FROM oa_form_submission WHERE id = ?",
                number(body.get("dataId"), 0L));
        return ApiResult.ok();
    }

    @PostMapping("/activiti/mongo/setProcess")
    public Map<String, Object> setProcess(@RequestBody Map<String, Object> body) {
        long tenantAppId = number(body.get("tenantAppId"), 1L);
        long menuId = number(body.get("menuId"), 0L);
        String actionCode = value(body.get("actionCode"), "page").trim();
        if (actionCode.isBlank()) {
            actionCode = "page";
        }
        boolean approvalEnabled = bool(body.get("approvalEnabled"), true);
        String requestedId = value(body.get("_id"), "");
        String processId = !requestedId.isBlank() && !"null".equalsIgnoreCase(requestedId)
                ? requestedId
                : "process-" + tenantAppId + "-" + menuId
                + ("page".equals(actionCode) ? "" : "-" + actionCode.replaceAll("[^a-zA-Z0-9_-]", "_"));
        int updated = jdbc.update("""
                UPDATE oa_process_design SET tenant_app_id = ?, menu_id = ?, menu_name = ?,
                    process_data = ?, status = 'published', create_by = ?, update_time = ?
                WHERE id = ?
                """,
                tenantAppId,
                menuId,
                value(body.get("menuName"), ""),
                json(body.getOrDefault("processData", Map.of())),
                AuthContext.username(),
                Timestamp.valueOf(LocalDateTime.now()),
                processId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO oa_process_design (
                        id, tenant_id, tenant_app_id, menu_id, menu_name, process_data,
                        status, create_by, update_time
                    ) VALUES (?, 1, ?, ?, ?, ?, 'published', ?, ?)
                    """,
                    processId,
                    tenantAppId,
                    menuId,
                    value(body.get("menuName"), ""),
                    json(body.getOrDefault("processData", Map.of())),
                    AuthContext.username(),
                    Timestamp.valueOf(LocalDateTime.now()));
        }
        flowBindingService.saveBinding(
                tenantAppId,
                menuId,
                actionCode,
                processId,
                approvalEnabled,
                AuthContext.username()
        );
        return ApiResult.ok(processId);
    }

    @PostMapping("/activiti/mongo/detailProcess")
    public Map<String, Object> detailProcess(@RequestBody Map<String, Object> body) {
        long tenantAppId = number(body.get("tenantAppId"), 1L);
        long menuId = number(body.get("menuId"), 0L);
        String actionCode = value(body.get("actionCode"), "page").trim();
        if (actionCode.isBlank()) {
            actionCode = "page";
        }
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT d.id, d.process_data, d.menu_id, d.menu_name, d.update_time,
                       b.id AS binding_id, b.action_code, b.approval_enabled
                FROM oa_module_action_flow b
                JOIN oa_process_design d ON d.id = b.process_design_id
                WHERE b.tenant_app_id = ? AND b.module_id = ? AND b.action_code = ?
                ORDER BY b.update_time DESC LIMIT 1
                """, tenantAppId, menuId, actionCode));
        if (rows.isEmpty() && "page".equals(actionCode)) {
            rows = JdbcMaps.camelCase(jdbc.queryForList("""
                    SELECT id, process_data, menu_id, menu_name, update_time
                    FROM oa_process_design WHERE tenant_app_id = ? AND menu_id = ?
                    ORDER BY update_time DESC LIMIT 1
                    """, tenantAppId, menuId));
        }
        Map<String, Object> result = ApiResult.ok();
        if (!rows.isEmpty()) {
            result.put("_id", rows.get(0).get("id"));
            result.put("menuId", rows.get(0).get("menuId"));
            result.put("menuName", rows.get(0).get("menuName"));
            result.put("processData", object(String.valueOf(rows.get(0).get("processData"))));
            result.put("updateTime", rows.get(0).get("updateTime"));
            result.put("actionCode", actionCode);
            result.put("approvalEnabled", bool(rows.get(0).get("approvalEnabled"), true));
        }
        return result;
    }

    @GetMapping("/activiti/mongoWorkflow/taskWaitCount")
    public Map<String, Object> taskWaitCount() {
        Map<String, Object> result = ApiResult.ok();
        result.put("taskWaitCount", workflowService.waitingCount(AuthContext.userId()));
        return result;
    }

    @GetMapping("/activiti/workflow/read/{workflowId}/{taskId}/{read}")
    public Map<String, Object> markTaskRead(
            @PathVariable String workflowId,
            @PathVariable String taskId,
            @PathVariable int read
    ) {
        return ApiResult.ok();
    }

    @GetMapping({"/activiti/mongoWorkflow/taskWaitList", "/activiti/mongoWorkflow/taskStartList",
            "/activiti/mongoWorkflow/taskDoneList", "/activiti/mongoWorkflow/taskCopyList"})
    public Map<String, Object> workflowTasks(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String kind = uri.endsWith("taskStartList")
                ? "start"
                : uri.endsWith("taskDoneList")
                ? "done"
                : uri.endsWith("taskCopyList") ? "copy" : "todo";
        List<Map<String, Object>> rows = workflowService.tasks(kind, AuthContext.userId());
        return ApiResult.page(rows, rows.size());
    }

    @PostMapping("/activiti/mongo/submitProcess")
    public Map<String, Object> submitProcess(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(workflowService.submit(
                body,
                AuthContext.userId(),
                AuthContext.username()
        ));
    }

    @PostMapping("/activiti/process/complete")
    public Map<String, Object> completeProcess(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(workflowService.complete(body, AuthContext.userId()));
    }

    private Map<String, Object> formResult(Map<String, Object> row) {
        Map<String, Object> result = ApiResult.ok();
        result.put("_id", row.get("formKey"));
        result.put("id", row.get("formKey"));
        result.put("formId", row.get("formKey"));
        result.put("type", row.get("formType"));
        result.put("parentId", row.get("parentKey"));
        result.put("basicSetting", object(String.valueOf(row.get("basicSetting"))));
        result.put("formData", object(String.valueOf(row.get("formData"))));
        result.put("processData", object(String.valueOf(row.get("processData"))));
        result.put("createTime", row.get("createTime"));
        result.put("updateTime", row.get("updateTime"));
        return result;
    }

    private Map<String, Object> requireForm(String formKey) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT * FROM oa_form_definition WHERE form_key = ?
                """, formKey));
        if (rows.isEmpty()) {
            throw new ApiException(404, "Form not found");
        }
        return rows.get(0);
    }

    private List<Map<String, Object>> formHeader(String formKey) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT form_data FROM oa_form_definition WHERE form_key = ?", formKey);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, Object> data = object(String.valueOf(rows.get(0).get("form_data")));
        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, Object>> header = new ArrayList<>();
        collectFields(data.get("fields"), seen, header);
        return header;
    }

    private void collectFields(Object raw, Set<String> seen, List<Map<String, Object>> header) {
        if (!(raw instanceof List<?> fields)) {
            return;
        }
        for (Object item : fields) {
            if (item instanceof List<?> nested) {
                collectFields(nested, seen, header);
            } else if (item instanceof Map<?, ?> field) {
                String model = value(field.get("vModel"), value(field.get("prop"), ""));
                String label = value(field.get("label"), model);
                if (!model.isBlank() && seen.add(model)) {
                    header.add(Map.of("label", label, "vModel", model));
                }
            }
        }
    }

    private Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        if (value == null || String.valueOf(value).isBlank() || "null".equals(String.valueOf(value))) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(String.valueOf(value), new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private String json(Object value) {
        if (value == null) {
            return "{}";
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new ApiException(500, "Unable to serialize form data");
        }
    }

    private String parentKey(Object value) {
        String parent = value(value, "");
        return parent.isBlank() || "-1".equals(parent) ? null : parent;
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

    private boolean bool(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value);
        return "1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
    }
}
