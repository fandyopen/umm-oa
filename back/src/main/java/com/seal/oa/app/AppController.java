package com.seal.oa.app;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import com.seal.oa.common.JdbcMaps;
import com.seal.oa.system.ModuleActionService;
import com.seal.oa.web.RequiresModuleAction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
public class AppController {

    private final JdbcTemplate jdbc;
    private final ModuleActionService moduleActionService;

    public AppController(JdbcTemplate jdbc, ModuleActionService moduleActionService) {
        this.jdbc = jdbc;
        this.moduleActionService = moduleActionService;
    }

    @GetMapping("/app/user/list")
    public Map<String, Object> apps() {
        List<Map<String, Object>> apps = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT tenant_app_id, tenant_id, app_name, app_icon, app_path, app_type, status, create_time
                FROM sys_app WHERE status = '0' ORDER BY tenant_app_id
                """));
        apps.forEach(app -> app.put("modules", modulesForApp(((Number) app.get("tenantAppId")).longValue())));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apps", apps);
        data.put("createTable", true);
        return ApiResult.ok(data);
    }

    @GetMapping("/app/system/list")
    public Map<String, Object> systemApps() {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT tenant_app_id, tenant_id, app_name, app_icon, app_path, app_type, status, create_time
                FROM sys_app WHERE status = '0' ORDER BY tenant_app_id
                """));
        return ApiResult.page(rows, rows.size());
    }

    @GetMapping("/app/user/module/{tenantAppId}")
    public Map<String, Object> appModules(@PathVariable Long tenantAppId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("tenantAppId", tenantAppId);
        details.put("modules", modulesForApp(tenantAppId));
        return ApiResult.ok(details);
    }

    @GetMapping("/app/user/module/path")
    public Map<String, Object> moduleByPath(@RequestParam Map<String, String> query) {
        String component = query.getOrDefault("component", "");
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT tenant_module_id, tenant_app_id, module_name, module_path, component, icon, order_num, status
                FROM sys_app_module WHERE component = ? LIMIT 1
                """, component));
        if (rows.isEmpty()) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("tenantModuleId", 1);
            fallback.put("tenantAppId", 1);
            fallback.put("component", component);
            return ApiResult.ok(fallback);
        }
        return ApiResult.ok(rows.get(0));
    }

    @PostMapping("/app/user/self/app")
    public Map<String, Object> createApp(@RequestBody Map<String, Object> body) {
        jdbc.update("""
                INSERT INTO sys_app (tenant_id, app_name, app_icon, app_path, app_type, status)
                VALUES (?, ?, ?, ?, ?, '0')
                """,
                number(body.get("tenantId"), 1L),
                string(body.get("appName")),
                string(body.get("appIcon")),
                string(body.get("appPath")),
                string(body.get("appType")));
        Long id = jdbc.queryForObject("SELECT MAX(tenant_app_id) FROM sys_app", Long.class);
        return ApiResult.ok(id);
    }

    @PutMapping("/app/user/update")
    public Map<String, Object> updateApp(@RequestBody Map<String, Object> body) {
        jdbc.update("""
                UPDATE sys_app SET app_name = ?, app_icon = ?, app_path = ?, app_type = ?, status = ?
                WHERE tenant_app_id = ?
                """,
                string(body.get("appName")),
                string(body.get("appIcon")),
                string(body.get("appPath")),
                string(body.get("appType")),
                string(body.get("status"), "0"),
                number(body.get("tenantAppId"), 0L));
        return ApiResult.ok();
    }

    @DeleteMapping("/app/user/delete/{tenantAppId}")
    public Map<String, Object> deleteApp(@PathVariable Long tenantAppId) {
        jdbc.update("DELETE FROM sys_app_module WHERE tenant_app_id = ?", tenantAppId);
        jdbc.update("DELETE FROM sys_app WHERE tenant_app_id = ?", tenantAppId);
        return ApiResult.ok();
    }

    @PostMapping("/app/user/addModule")
    @RequiresModuleAction(moduleId = 340597L, action = "manage")
    public Map<String, Object> addModule(@RequestBody Map<String, Object> body) {
        if (body.get("tenantModuleIds") instanceof List<?> moduleIds) {
            saveModulePermissions(body, moduleIds);
            return ApiResult.ok();
        }
        jdbc.update("""
                INSERT INTO sys_app_module (
                    tenant_app_id, module_name, module_path, component, icon, order_num, status
                ) VALUES (?, ?, ?, ?, ?, ?, '0')
                """,
                number(body.get("tenantAppId"), 1L),
                string(body.get("moduleName")),
                string(body.get("modulePath")),
                string(body.get("component")),
                string(body.get("icon")),
                number(body.get("orderNum"), 0L));
        return ApiResult.ok();
    }

    @GetMapping("/app/user/roleMenuTree")
    @RequiresModuleAction(moduleId = 340597L, action = "access")
    public Map<String, Object> modulePermissionTree(@RequestParam Map<String, String> query) {
        List<Map<String, Object>> modules = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT module_id, parent_id, module_name
                FROM sys_module WHERE status = '0'
                ORDER BY parent_id, module_sort, module_id
                """));
        Map<Long, Map<String, Object>> nodes = new LinkedHashMap<>();
        for (Map<String, Object> module : modules) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", module.get("moduleId"));
            node.put("label", module.get("moduleName"));
            node.put("children", new ArrayList<Map<String, Object>>());
            nodes.put(((Number) module.get("moduleId")).longValue(), node);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> module : modules) {
            Map<String, Object> node = nodes.get(((Number) module.get("moduleId")).longValue());
            long parentId = ((Number) module.get("parentId")).longValue();
            Map<String, Object> parent = nodes.get(parentId);
            if (parent == null || parentId == 0L) {
                roots.add(node);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
                children.add(node);
            }
        }

        long roleId = number(query.get("roleId"), 0L);
        if (roleId <= 0L) {
            throw new ApiException(400, "roleId is required");
        }
        for (Map<String, Object> module : modules) {
            long moduleId = ((Number) module.get("moduleId")).longValue();
            Map<String, Object> node = nodes.get(moduleId);
            if (node == null) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children =
                    (List<Map<String, Object>>) node.get("children");
            for (Map<String, Object> action : moduleActionService.actions(moduleId)) {
                Map<String, Object> actionNode = new LinkedHashMap<>();
                actionNode.put(
                        "id",
                        "action:" + moduleId + ":" + action.get("actionCode")
                );
                actionNode.put("label", action.get("actionName"));
                actionNode.put("moduleId", moduleId);
                actionNode.put("actionCode", action.get("actionCode"));
                actionNode.put("isAction", true);
                actionNode.put("children", List.of());
                children.add(actionNode);
            }
        }

        List<Object> checkedKeys = new ArrayList<>();
        List<String> roleKeys = jdbc.queryForList(
                "SELECT role_key FROM sys_role WHERE role_id = ? AND del_flag = '0'",
                String.class,
                roleId
        );
        if (roleKeys.isEmpty()) {
            throw new ApiException(404, "Role not found");
        }
        if (roleKeys.contains("admin")) {
            checkedKeys.addAll(jdbc.queryForList(
                    "SELECT module_id FROM sys_module WHERE status = '0' ORDER BY module_id",
                    Long.class
            ));
            for (Map<String, Object> module : modules) {
                long moduleId = ((Number) module.get("moduleId")).longValue();
                for (Map<String, Object> action : moduleActionService.actions(moduleId)) {
                    checkedKeys.add("action:" + moduleId + ":" + action.get("actionCode"));
                }
            }
        } else {
            checkedKeys.addAll(jdbc.queryForList("""
                    SELECT module_id FROM sys_role_module WHERE role_id = ? ORDER BY module_id
                    """, Long.class, roleId));
            for (Map<String, Object> action : moduleActionService.roleActions(roleId)) {
                checkedKeys.add(
                        "action:" + action.get("moduleId") + ":" + action.get("actionCode")
                );
            }
        }

        Map<String, Object> result = ApiResult.ok();
        result.put("menus", roots);
        result.put("checkedKeys", checkedKeys);
        return result;
    }

    @GetMapping({"/app/user/deptMenuTree", "/app/user/userMenuTree"})
    @RequiresModuleAction(moduleId = 340597L, action = "access")
    public Map<String, Object> deprecatedModulePermissionTree() {
        throw new ApiException(410, "模块授权已统一为角色授权，请使用角色页签");
    }

    @GetMapping("/crm/company/getCompany")
    public Map<String, Object> companies() {
        return ApiResult.ok(JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT tenant_id, tenant_name AS name, contact_name, contact_phone, status
                FROM sys_tenant WHERE status = '0' ORDER BY tenant_id
                """)));
    }

    @PostMapping("/crm/company/add")
    public Map<String, Object> addCompany(@RequestBody Map<String, Object> body) {
        jdbc.update("""
                INSERT INTO sys_tenant (tenant_name, contact_name, contact_phone, status)
                VALUES (?, ?, ?, '0')
                """,
                string(body.get("name")),
                string(body.get("contactName")),
                string(body.get("contactPhone")));
        return ApiResult.ok(jdbc.queryForObject("SELECT MAX(tenant_id) FROM sys_tenant", Long.class));
    }

    @GetMapping("/crm/company/change/{tenantId}")
    public Map<String, Object> switchCompany(@PathVariable Long tenantId) {
        jdbc.update("UPDATE sys_user SET tenant_id = ? WHERE user_id = ?", tenantId, AuthContext.userId());
        return ApiResult.ok();
    }

    private List<Map<String, Object>> modulesForApp(Long tenantAppId) {
        return JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT tenant_module_id, tenant_app_id, module_name, module_path, component, icon, order_num, status
                FROM sys_app_module WHERE tenant_app_id = ? AND status = '0' ORDER BY order_num
                """, tenantAppId));
    }

    private void saveModulePermissions(Map<String, Object> body, List<?> moduleIds) {
        long roleId = number(body.get("roleId"), 0L);
        if (roleId <= 0L) {
            throw new ApiException(400, "模块授权仅支持角色");
        }
        moduleActionService.syncRole(roleId, moduleIds, moduleIds);
    }

    private long number(Object value, long defaultValue) {
        try {
            return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private String string(Object value) {
        return string(value, "");
    }

    private String string(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }
}
