package com.seal.oa.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.common.JdbcMaps;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ModuleService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ModuleActionService moduleActionService;

    public ModuleService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            ModuleActionService moduleActionService
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.moduleActionService = moduleActionService;
    }

    public List<Map<String, Object>> routers(
            Long tenantId,
            boolean admin,
            Set<String> permissions,
            Set<Long> allowedModuleIds
    ) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT module_id, tenant_id, parent_id, module_name, route_name, path, component, icon,
                       module_sort, hidden, perms, has_flow, flow_conditions, no_cache,
                       is_app, is_frame, query, redirect, status, create_time
                FROM sys_module
                WHERE tenant_id = ? AND status = '0'
                ORDER BY parent_id, module_sort, module_id
                """, tenantId));

        Set<Long> expandedAllowedIds = expandAncestors(rows, allowedModuleIds);
        Map<Long, Map<String, Object>> nodes = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            row.put("flowConditions", normalizeFlowConditions(row.get("flowConditions")));
            Long moduleId = number(row.get("moduleId"));
            if (!admin && !isModuleAllowed(row, moduleId, permissions, expandedAllowedIds)) {
                continue;
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("title", row.get("moduleName"));
            meta.put("icon", row.get("icon"));
            meta.put("perms", row.get("perms"));
            meta.put("noCache", number(row.get("noCache")) == 1);
            meta.put("flowConditions", text(row.get("flowConditions")) ? row.get("flowConditions") : null);

            Map<String, Object> node = new LinkedHashMap<>();
            node.put("moduleId", moduleId);
            node.put("menuId", moduleId);
            node.put("parentId", number(row.get("parentId")));
            node.put("moduleName", row.get("moduleName"));
            node.put("menuName", row.get("moduleName"));
            node.put("name", row.get("routeName"));
            node.put("path", row.get("path"));
            node.put("hidden", number(row.get("hidden")) == 1);
            node.put("component", row.get("component"));
            node.put("meta", meta);
            node.put("moduleSort", number(row.get("moduleSort")));
            node.put("orderNum", number(row.get("moduleSort")));
            node.put("visible", number(row.get("hidden")) == 1 ? "1" : "0");
            node.put("isCache", number(row.get("noCache")) == 1 ? "1" : "0");
            node.put("status", row.get("status"));
            node.put("createTime", row.get("createTime"));
            if (text(row.get("perms"))) {
                node.put("perms", row.get("perms"));
            }
            node.put("hasFlow", number(row.get("hasFlow")));
            if (text(row.get("redirect"))) {
                node.put("redirect", row.get("redirect"));
            }
            node.put("children", new ArrayList<Map<String, Object>>());
            nodes.put(moduleId, node);
        }

        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> node : nodes.values()) {
            Long parentId = number(node.get("parentId"));
            Map<String, Object> parent = nodes.get(parentId);
            if (parent == null || parentId == 0L) {
                roots.add(node);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
                children.add(node);
            }
        }

        if (!admin) {
            roots.removeIf(node -> {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
                return children.isEmpty() && !isAllowed(node, permissions);
            });
        }
        sortTree(roots);
        return roots;
    }

    public Set<Long> roleModuleIds(Long userId) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT DISTINCT rm.module_id
                FROM sys_role_module rm
                JOIN sys_role r ON r.role_id = rm.role_id
                JOIN sys_user_role ur ON ur.role_id = rm.role_id
                JOIN sys_module m ON m.module_id = rm.module_id
                WHERE ur.user_id = ? AND m.status = '0'
                  AND r.status = '0' AND r.del_flag = '0'
                """, Long.class, userId));
    }

    public List<Map<String, Object>> moduleRows(Long tenantId) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT module_id, tenant_id, parent_id, module_name, route_name, path, component, icon,
                       module_sort, hidden, perms, has_flow, flow_conditions, no_cache,
                       is_app, is_frame, query, redirect, status, create_time
                FROM sys_module WHERE tenant_id = ?
                ORDER BY parent_id, module_sort, module_id
                """, tenantId));
        rows.forEach(row -> row.put("flowConditions", normalizeFlowConditions(row.get("flowConditions"))));
        return rows;
    }

    public Map<String, Object> module(Long moduleId) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT module_id, tenant_id, parent_id, module_name, route_name, path, component, icon,
                       module_sort, hidden, perms, has_flow, flow_conditions, no_cache,
                       is_app, is_frame, query, redirect, status, create_time
                FROM sys_module WHERE module_id = ?
                """, moduleId));
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> result = rows.get(0);
        result.put("flowConditions", normalizeFlowConditions(result.get("flowConditions")));
        result.put("menuId", result.get("moduleId"));
        result.put("menuName", result.get("moduleName"));
        result.put("orderNum", result.get("moduleSort"));
        result.put("visible", number(result.get("hidden")) == 1 ? "1" : "0");
        result.put("isCache", number(result.get("noCache")) == 1 ? "1" : "0");
        String component = String.valueOf(result.getOrDefault("component", ""));
        result.put("menuType", "Layout".equals(component) || "ParentView".equals(component) ? "M" : "C");
        return result;
    }

    public Long create(Map<String, Object> body, Long tenantId) {
        Long moduleId = number(body.get("menuId"));
        if (moduleId == 0L) {
            moduleId = number(body.get("moduleId"));
        }
        if (moduleId == 0L) {
            moduleId = jdbc.queryForObject("SELECT COALESCE(MAX(module_id), 0) + 1 FROM sys_module", Long.class);
        }
        jdbc.update("""
                INSERT INTO sys_module (
                    module_id, tenant_id, parent_id, module_name, route_name, path,
                    component, icon, module_sort, hidden, perms, has_flow,
                    flow_conditions, no_cache, is_app, is_frame, query, redirect, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                moduleId,
                tenantId,
                number(body.get("parentId")),
                value(body.get("menuName"), body.get("moduleName"), "未命名模块"),
                value(body.get("routeName"), body.get("name"), "Module" + moduleId),
                value(body.get("path"), ""),
                value(body.get("component"), ""),
                value(body.get("icon"), ""),
                number(body.get("orderNum"), number(body.get("moduleSort"), 0L)),
                "1".equals(value(body.get("visible"), "0")) ? 1 : number(body.get("hidden"), 0L),
                value(body.get("perms"), ""),
                number(body.get("hasFlow"), 1L),
                normalizeFlowConditions(body.get("flowConditions")),
                "1".equals(value(body.get("isCache"), "0")) ? 1 : number(body.get("noCache"), 0L),
                value(body.get("isApp"), ""),
                value(body.get("isFrame"), ""),
                value(body.get("query"), ""),
                value(body.get("redirect"), ""),
                value(body.get("status"), "0")
        );
        moduleActionService.refreshModule(
                moduleId,
                value(body.get("perms"), ""),
                value(body.get("component"), "")
        );
        return moduleId;
    }

    public void update(Map<String, Object> body) {
        Long moduleId = number(body.get("menuId"), number(body.get("moduleId"), 0L));
        Map<String, Object> existing = module(moduleId);
        if (existing == null) {
            return;
        }
        long hasFlow = body.containsKey("hasFlow")
                ? number(body.get("hasFlow"), 1L)
                : number(existing.get("hasFlow"), 1L);
        String flowConditions = body.containsKey("flowConditions")
                ? normalizeFlowConditions(body.get("flowConditions"))
                : normalizeFlowConditions(existing.get("flowConditions"));
        jdbc.update("""
                UPDATE sys_module SET parent_id = ?, module_name = ?, route_name = ?, path = ?,
                    component = ?, icon = ?, module_sort = ?, hidden = ?, perms = ?,
                    has_flow = ?, flow_conditions = ?, no_cache = ?, is_app = ?,
                    is_frame = ?, query = ?, redirect = ?, status = ?
                WHERE module_id = ?
                """,
                number(body.get("parentId"), 0L),
                value(body.get("menuName"), body.get("moduleName"), ""),
                value(body.get("routeName"), body.get("name"), ""),
                value(body.get("path"), ""),
                value(body.get("component"), ""),
                value(body.get("icon"), ""),
                number(body.get("orderNum"), number(body.get("moduleSort"), 0L)),
                "1".equals(value(body.get("visible"), "0")) ? 1 : number(body.get("hidden"), 0L),
                value(body.get("perms"), ""),
                hasFlow,
                flowConditions,
                "1".equals(value(body.get("isCache"), "0")) ? 1 : number(body.get("noCache"), 0L),
                value(body.get("isApp"), ""),
                value(body.get("isFrame"), ""),
                value(body.get("query"), ""),
                value(body.get("redirect"), ""),
                value(body.get("status"), "0"),
                moduleId
        );
        moduleActionService.refreshModule(
                moduleId,
                value(body.get("perms"), ""),
                value(body.get("component"), "")
        );
    }

    private String normalizeFlowConditions(Object value) {
        String raw = value == null ? "" : String.valueOf(value).trim();
        if (raw.isEmpty()) {
            return "";
        }
        if (raw.length() > 1 && raw.startsWith("\"") && raw.endsWith("\"")) {
            raw = raw.substring(1, raw.length() - 1);
        }
        raw = raw.replace("\\\\\"", "\"").replace("\\\"", "\"");
        try {
            JsonNode node = objectMapper.readTree(raw);
            return node.isArray() ? objectMapper.writeValueAsString(node) : raw;
        } catch (Exception ignored) {
            return raw;
        }
    }

    public void delete(Long moduleId) {
        jdbc.update("DELETE FROM sys_role_module_action WHERE module_id = ?", moduleId);
        jdbc.update("DELETE FROM sys_module_action WHERE module_id = ?", moduleId);
        jdbc.update("DELETE FROM sys_module WHERE module_id = ? OR parent_id = ?", moduleId, moduleId);
    }

    private boolean isAllowed(Map<String, Object> row, Set<String> permissions) {
        String perms = String.valueOf(row.getOrDefault("perms", ""));
        return perms.isBlank() || permissions.contains(perms) || permissions.contains("*:*:*");
    }

    private boolean isModuleAllowed(
            Map<String, Object> row,
            Long moduleId,
            Set<String> permissions,
            Set<Long> allowedModuleIds
    ) {
        if (!allowedModuleIds.isEmpty()) {
            return allowedModuleIds.contains(moduleId);
        }
        return isAllowed(row, permissions);
    }

    private Set<Long> expandAncestors(
            List<Map<String, Object>> rows,
            Set<Long> allowedModuleIds
    ) {
        if (allowedModuleIds.isEmpty()) {
            return Set.of();
        }
        Map<Long, Long> parents = new HashMap<>();
        for (Map<String, Object> row : rows) {
            parents.put(number(row.get("moduleId")), number(row.get("parentId")));
        }
        Set<Long> expanded = new HashSet<>(allowedModuleIds);
        for (Long moduleId : allowedModuleIds) {
            Long parentId = parents.get(moduleId);
            while (parentId != null && parentId != 0L && expanded.add(parentId)) {
                parentId = parents.get(parentId);
            }
        }
        return expanded;
    }

    private void sortTree(List<Map<String, Object>> nodes) {
        nodes.sort(Comparator.comparingLong(node -> number(node.get("moduleSort"))));
        for (Map<String, Object> node : nodes) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
            if (!children.isEmpty()) {
                node.put("alwaysShow", true);
                sortTree(children);
            }
        }
    }

    private long number(Object value) {
        return number(value, 0L);
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

    private String value(Object... candidates) {
        String fallback = "";
        for (int index = 0; index < candidates.length; index++) {
            Object candidate = candidates[index];
            if (index == candidates.length - 1) {
                fallback = String.valueOf(candidate);
            } else if (candidate != null && !String.valueOf(candidate).isBlank()) {
                return String.valueOf(candidate);
            }
        }
        return fallback;
    }

    private boolean text(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }
}
