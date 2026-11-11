package com.seal.oa.system;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.JdbcMaps;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Reads the existing configuration sources; never creates or publishes a workflow. */
@Service
public class WorkflowManagementService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SystemService system;

    public WorkflowManagementService(JdbcTemplate jdbc, ObjectMapper json, SystemService system) {
        this.jdbc = jdbc;
        this.json = json;
        this.system = system;
    }

    public Map<String, Object> list(SystemService.UserAccount user, Map<String, String> query) {
        List<Map<String, Object>> configurations = configurations(user);
        List<Map<String, Object>> modules = configurations.stream()
                .map(row -> Map.<String, Object>of("value", row.get("moduleFilter"), "label", row.get("modulePath")))
                .distinct().toList();
        String keyword = text(query.get("keyword")).trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> rows = configurations.stream()
                .filter(row -> matches(row, "source", query.get("source")))
                .filter(row -> matches(row, "state", query.get("state")))
                .filter(row -> matches(row, "moduleFilter", query.get("module")))
                .filter(row -> matches(row, "tenantAppId", query.get("tenantAppId")))
                .filter(row -> keyword.isEmpty() || (text(row.get("modulePath")) + " " + text(row.get("actionName"))
                        + " " + text(row.get("actionCode")) + " " + text(row.get("flowName"))).toLowerCase(Locale.ROOT).contains(keyword))
                .map(this::summary).toList();
        return Map.of("rows", rows, "total", rows.size(), "modules", modules,
                "canDesign", canDesign(user.userId()),
                "statistics", Map.of("total", configurations.size(),
                        "active", configurations.stream().filter(row -> "active".equals(row.get("state"))).count(),
                        "draft", configurations.stream().filter(row -> Boolean.TRUE.equals(row.get("hasDraft"))).count(),
                        "closed", configurations.stream().filter(row -> "closed".equals(row.get("state"))).count()));
    }

    public Map<String, Object> detail(SystemService.UserAccount user, Map<String, String> query) {
        String key = text(query.get("key"));
        Map<String, Object> row = configurations(user).stream().filter(item -> key.equals(item.get("key")))
                .findFirst().orElseThrow(() -> new ApiException(404, "流程配置不存在或不属于当前企业"));
        Map<String, Object> result = new LinkedHashMap<>(row);
        result.put("publishedNodes", nodes(object(row.get("publishedProcess")), user.tenantId()));
        result.put("draftNodes", nodes(object(row.get("draftProcess")), user.tenantId()));
        result.remove("publishedProcess");
        result.remove("draftProcess");
        result.put("canDesign", canDesign(user.userId()));
        return result;
    }

    private List<Map<String, Object>> configurations(SystemService.UserAccount user) {
        long tenant = user.tenantId();
        Map<Long, Map<String, Object>> modules = new LinkedHashMap<>();
        for (Map<String, Object> module : rows("SELECT module_id,parent_id,module_name,status FROM sys_module WHERE tenant_id=?", tenant)) {
            modules.put(number(module.get("moduleId")), module);
        }
        Map<String, Map<String, Object>> actions = new LinkedHashMap<>();
        for (Map<String, Object> row : rows("""
                SELECT a.module_id,a.action_code,a.action_name,a.handler_key,a.flow_design_enabled,
                       a.status AS action_status,a.create_time,a.update_time AS action_update_time,
                       m.module_name
                FROM sys_module_action a JOIN sys_module m ON m.module_id=a.module_id
                WHERE m.tenant_id=? AND a.status<>'2'
                ORDER BY m.module_sort,m.module_id,a.action_sort,a.action_code
                """, tenant)) {
            actions.put(row.get("moduleId") + ":" + row.get("actionCode"), row);
        }
        Map<String, Map<String, Object>> buttons = new LinkedHashMap<>();
        for (Map<String, Object> binding : rows("""
                SELECT b.tenant_app_id,b.module_id,b.action_code,b.process_design_id,b.approval_enabled,
                       b.update_time AS binding_update_time,d.status AS process_status,
                       d.process_data AS published_process,d.update_time AS design_update_time
                FROM oa_module_action_flow b JOIN sys_module m ON m.module_id=b.module_id
                LEFT JOIN oa_process_design d ON d.id=b.process_design_id AND d.tenant_id=m.tenant_id
                WHERE m.tenant_id=? AND b.tenant_id=? ORDER BY b.id
                """, tenant, tenant)) {
            Map<String, Object> action = actions.get(binding.get("moduleId") + ":" + binding.get("actionCode"));
            if (action == null) continue;
            Map<String, Object> row = button(action, number(binding.get("tenantAppId")), modules);
            row.putAll(binding);
            buttons.put(text(row.get("key")), row);
        }
        for (Map<String, Object> draft : rows("""
                SELECT business_key,payload_json,COALESCE(update_time,create_time) AS draft_update_time
                FROM biz_record WHERE tenant_id=? AND module='lowcode:button-flow-draft' ORDER BY id
                """, tenant)) {
            String[] parts = text(draft.get("businessKey")).split(":", 3);
            if (parts.length != 3 || number(parts[0]) <= 0) continue;
            Map<String, Object> action = actions.get(parts[1] + ":" + parts[2]);
            if (action == null) continue;
            Map<String, Object> seed = button(action, number(parts[0]), modules);
            Map<String, Object> row = buttons.computeIfAbsent(text(seed.get("key")), ignored -> seed);
            Map<String, Object> payload = object(draft.get("payloadJson"));
            row.put("hasDraft", true);
            row.put("draftProcess", payload.getOrDefault("processData", Map.of()));
            row.put("draftApprovalEnabled", enabled(payload.get("approvalEnabled")));
            row.put("draftUpdateTime", draft.get("draftUpdateTime"));
        }
        Set<String> configuredActions = new LinkedHashSet<>();
        buttons.values().forEach(row -> configuredActions.add(row.get("moduleId") + ":" + row.get("actionCode")));
        for (Map.Entry<String, Map<String, Object>> entry : actions.entrySet()) {
            if (enabled(entry.getValue().get("flowDesignEnabled")) && !configuredActions.contains(entry.getKey())) {
                Map<String, Object> row = button(entry.getValue(), 1L, modules);
                buttons.put(text(row.get("key")), row);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>(buttons.values());
        for (Map<String, Object> row : result) finish(row);
        for (Map<String, Object> catalog : rows("""
                SELECT c.id AS workflow_id,c.module_id,c.flow_name,c.revision AS workflow_revision,
                       c.approval_enabled,c.draft_data,c.draft_approval_enabled,c.create_time,
                       c.update_time AS draft_update_time,d.process_data AS published_process,
                       d.status AS process_status,d.update_time AS design_update_time
                FROM oa_workflow_catalog c LEFT JOIN oa_process_design d ON d.id=c.id AND d.tenant_id=c.tenant_id
                WHERE c.tenant_id=? ORDER BY c.create_time,c.id
                """, tenant)) {
            String id = text(catalog.get("workflowId"));
            Map<String, Object> bound = result.stream().filter(row -> id.equals(text(row.get("processDesignId")))).findFirst().orElse(null);
            boolean attached = bound != null;
            Map<String, Object> seed = new LinkedHashMap<>();
            seed.put("moduleId", catalog.get("moduleId"));
            seed.put("moduleName", modules.getOrDefault(number(catalog.get("moduleId")), Map.of()).getOrDefault("moduleName", "所属模块已删除"));
            seed.put("actionCode", "—"); seed.put("actionName", "未关联按钮");
            seed.put("actionStatus", "0"); seed.put("flowDesignEnabled", true);
            seed.put("handlerKey", "catalog");
            Map<String, Object> row = attached ? new LinkedHashMap<>(bound) : button(seed, 1, modules);
            if (attached) result.remove(bound);
            row.putAll(catalog);
            row.put("key", "catalog:" + id);
            row.put("source", "catalog"); row.put("bound", attached);
            row.put("processDesignId", id);
            row.put("hasDraft", catalog.get("draftData") != null);
            row.put("draftProcess", catalog.get("draftData"));
            row.remove("draftData");
            finish(row);
            result.add(row);
        }
        Map<String, String> formNames = new LinkedHashMap<>();
        List<Map<String, Object>> forms = rows("""
                SELECT form_key,parent_key,form_type,flow_name,status,tenant_app_id,process_data,
                       create_time,update_time FROM oa_form_definition WHERE tenant_id=? ORDER BY id
                """, tenant);
        forms.forEach(form -> formNames.put(text(form.get("formKey")), text(form.get("flowName"))));
        for (Map<String, Object> form : forms) {
            if (!"flow".equals(form.get("formType"))) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", "form:" + form.get("formKey"));
            row.put("source", "form");
            row.put("formId", form.get("formKey"));
            row.put("tenantAppId", form.get("tenantAppId"));
            row.put("moduleFilter", "form:" + text(form.get("parentKey")));
            row.put("modulePath", "低代码表单 / " + formNames.getOrDefault(text(form.get("parentKey")), "未分组"));
            row.put("moduleName", form.get("flowName"));
            row.put("actionName", form.get("flowName"));
            row.put("actionCode", "—");
            row.put("flowName", form.get("flowName"));
            row.put("flowDesignEnabled", true);
            row.put("moduleEnabled", !Set.of("disabled", "deleted", "1", "2").contains(text(form.get("status"))));
            row.put("actionStatus", "0");
            row.put("handlerKey", "lowcode.form");
            row.put("processStatus", form.get("status"));
            row.put("processDesignId", form.get("formKey"));
            row.put("approvalEnabled", true);
            boolean draft = "draft".equals(form.get("status"));
            row.put("hasDraft", draft);
            row.put(draft ? "draftProcess" : "publishedProcess", form.get("processData"));
            if (draft) row.put("draftApprovalEnabled", true);
            row.put("createTime", form.get("createTime"));
            row.put("designUpdateTime", form.get("updateTime"));
            finish(row);
            result.add(row);
        }
        result.sort(Comparator.comparing((Map<String, Object> row) -> text(row.get("modulePath")))
                .thenComparing(row -> text(row.get("actionName"))).thenComparing(row -> text(row.get("key"))));
        return result;
    }

    private Map<String, Object> button(Map<String, Object> action, long app, Map<Long, Map<String, Object>> modules) {
        Map<String, Object> row = new LinkedHashMap<>(action);
        long moduleId = number(action.get("moduleId"));
        row.put("key", "button:" + app + ":" + moduleId + ":" + action.get("actionCode"));
        row.put("source", "button");
        row.put("tenantAppId", app);
        row.put("moduleFilter", "module:" + moduleId);
        row.put("flowName", action.get("moduleName") + " · " + action.get("actionName"));
        row.put("hasDraft", false);
        row.put("flowDesignEnabled", enabled(action.get("flowDesignEnabled")));
        boolean active = true;
        List<String> path = new ArrayList<>();
        Set<Long> visited = new LinkedHashSet<>();
        for (Map<String, Object> current = modules.get(moduleId); current != null && visited.add(number(current.get("moduleId")));
             current = modules.get(number(current.get("parentId")))) {
            path.add(0, text(current.get("moduleName")));
            active &= "0".equals(text(current.get("status")));
        }
        row.put("modulePath", String.join(" / ", path));
        row.put("moduleEnabled", active);
        return row;
    }

    private void finish(Map<String, Object> row) {
        boolean published = "published".equals(row.get("processStatus"));
        row.put("published", published);
        row.put("approvalEnabled", enabled(row.get("approvalEnabled")));
        String state = "catalog".equals(row.get("source")) && !Boolean.TRUE.equals(row.get("bound")) ? published ? "unbound" : "unpublished"
                : !Boolean.TRUE.equals(row.get("moduleEnabled")) || !"0".equals(row.get("actionStatus")) || !enabled(row.get("flowDesignEnabled")) ? "closed"
                : text(row.get("handlerKey")).isBlank() ? "integration"
                : !published ? Boolean.TRUE.equals(row.get("hasDraft")) || !text(row.get("processDesignId")).isBlank() ? "unpublished" : "unconfigured"
                : !enabled(row.get("approvalEnabled")) ? "disabled" : "active";
        row.put("state", state);
        row.put("stateLabel", switch (state) {
            case "unbound" -> "待关联";
            case "closed" -> "已关闭";
            case "integration" -> "待接入";
            case "unpublished" -> "未发布";
            case "unconfigured" -> "待配置";
            case "disabled" -> "未启用";
            default -> "已生效";
        });
        Object latest = row.get("createTime");
        for (String field : List.of("actionUpdateTime", "bindingUpdateTime", "designUpdateTime", "draftUpdateTime")) {
            Object candidate = row.get(field);
            if (candidate != null && (latest == null || text(candidate).compareTo(text(latest)) > 0)) latest = candidate;
        }
        row.put("updateTime", latest);
    }

    private Map<String, Object> summary(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>(row);
        result.remove("publishedProcess");
        result.remove("draftProcess");
        return result;
    }

    private List<Map<String, Object>> nodes(Map<String, Object> process, Long tenant) {
        if (!(process.get("nodes") instanceof List<?> raw)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : raw) {
            Map<String, Object> node = object(item);
            if (node.isEmpty()) continue;
            Map<String, Object> displayed = new LinkedHashMap<>();
            for (String field : List.of("id", "name", "type", "approver", "approvalMode", "condition")) displayed.put(field, node.get(field));
            displayed.put("approverLabel", approver(text(node.get("approver")), tenant));
            result.add(displayed);
        }
        return result;
    }

    private String approver(String rule, Long tenant) {
        if (rule.isBlank()) return "未设置审批人";
        String key = rule.startsWith("role:") ? rule.substring(5) : rule;
        List<String> roles = jdbc.queryForList("SELECT role_name FROM sys_role WHERE tenant_id=? AND (role_key=? OR role_name=?) AND del_flag='0'", String.class, tenant, key, key);
        if (!roles.isEmpty()) return "角色：" + String.join("、", roles);
        if (rule.startsWith("role:")) return "角色：" + key + "（未找到）";
        List<String> users = jdbc.queryForList("SELECT nick_name FROM sys_user WHERE tenant_id=? AND del_flag='0' AND (user_name=? OR phonenumber=? OR nick_name=?)",
                String.class, tenant, rule, rule, rule);
        if (!users.isEmpty()) return "指定成员：" + String.join("、", users);
        return rule;
    }

    private boolean canDesign(Long userId) {
        return system.hasModuleAction(userId, 340773L, "design")
                || system.hasModuleAction(userId, 340772L, "manage")
                || system.hasModuleAction(userId, 340772L, "workflow")
                || system.hasModuleAction(userId, 340771L, "manage");
    }

    private List<Map<String, Object>> rows(String sql, Object... args) { return JdbcMaps.camelCase(jdbc.queryForList(sql, args)); }
    private boolean matches(Map<String, Object> row, String field, String value) { return value == null || value.isBlank() || value.equals(text(row.get(field))); }
    private boolean enabled(Object value) { return Boolean.TRUE.equals(value) || "1".equals(text(value)) || "true".equalsIgnoreCase(text(value)); }
    private long number(Object value) { try { return Long.parseLong(text(value)); } catch (NumberFormatException ignored) { return 0; } }
    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        try {
            Map<String, Object> result = json.readValue(text(value), new TypeReference<LinkedHashMap<String, Object>>() {});
            return result == null ? Map.of() : result;
        }
        catch (Exception ignored) { return Map.of(); }
    }
}
