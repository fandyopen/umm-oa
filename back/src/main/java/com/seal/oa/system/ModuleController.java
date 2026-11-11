package com.seal.oa.system;

import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import com.seal.oa.system.SystemService.UserAccount;
import com.seal.oa.web.ModuleActionRequirement;
import com.seal.oa.web.RequiresAnyModuleAction;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/system/module")
@RequiresAnyModuleAction({
        @ModuleActionRequirement(moduleId = 340773L, action = "design"),
        @ModuleActionRequirement(moduleId = 340771L, action = "access"),
        @ModuleActionRequirement(moduleId = 340772L, action = "access")
})
public class ModuleController {

    private final ModuleService moduleService;
    private final SystemService systemService;
    private final ModuleActionService moduleActionService;
    private final WorkflowCatalogService catalog;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public ModuleController(
            ModuleService moduleService,
            SystemService systemService,
            ModuleActionService moduleActionService,
            WorkflowCatalogService catalog,
            org.springframework.jdbc.core.JdbcTemplate jdbc
    ) {
        this.moduleService = moduleService;
        this.systemService = systemService;
        this.moduleActionService = moduleActionService;
        this.catalog = catalog; this.jdbc = jdbc;
    }

    @GetMapping("/list")
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        UserAccount user = systemService.requireCurrentUser();
        List<Map<String, Object>> rows = moduleService.moduleRows(user.tenantId());
        String menuName = query.getOrDefault("menuName", "").trim().toLowerCase();
        String status = query.getOrDefault("status", "").trim();
        List<Map<String, Object>> filtered = rows.stream()
                .filter(row -> menuName.isEmpty()
                        || String.valueOf(row.get("moduleName")).toLowerCase().contains(menuName))
                .filter(row -> status.isEmpty() || status.equals(String.valueOf(row.get("status"))))
                .toList();
        return ApiResult.page(filtered, filtered.size());
    }

    @GetMapping("/tree")
    public Map<String, Object> tree() {
        UserAccount user = systemService.requireCurrentUser();
        boolean admin = systemService.roleKeys(user.userId()).contains("admin");
        return ApiResult.ok(moduleService.routers(
                user.tenantId(),
                admin,
                Set.copyOf(systemService.permissions(user.userId())),
                moduleService.roleModuleIds(user.userId())
        ));
    }

    @GetMapping("/management-tree")
    public Map<String, Object> managementTree() {
        UserAccount user = systemService.requireCurrentUser();
        Map<String, Object> result = ApiResult.ok(moduleService.managementTree(user.tenantId()));
        result.put("canManageStructure", systemService.hasModuleAction(user.userId(), 340772L, "manage"));
        result.put("canManageWorkflow", canManageWorkflow(user.userId()));
        return result;
    }

    @GetMapping("/{moduleId}")
    public Map<String, Object> detail(@PathVariable Long moduleId) {
        return ApiResult.ok(requireModule(moduleId));
    }

    @GetMapping("/{moduleId}/actions")
    public Map<String, Object> actions(
            @PathVariable Long moduleId,
            @RequestParam Map<String, String> query
    ) {
        requireModule(moduleId);
        boolean includeDisabled = "true".equalsIgnoreCase(query.getOrDefault("includeDisabled", "false"));
        boolean workflowOnly = "true".equalsIgnoreCase(query.getOrDefault("workflowOnly", "false"));
        if (workflowOnly) {
            return ApiResult.ok(moduleActionService.workflowActions(moduleId));
        }
        return ApiResult.ok(includeDisabled
                ? moduleActionService.allActions(moduleId)
                : moduleActionService.actions(moduleId));
    }

    @GetMapping("/{moduleId}/actions/next-code")
    public Map<String, Object> nextActionCode(@PathVariable Long moduleId) {
        requireModule(moduleId);
        return ApiResult.ok(Map.of(
                "actionCode",
                moduleActionService.nextActionCode(moduleId)
        ));
    }

    @PostMapping("/{moduleId}/actions")
    public Map<String, Object> createAction(
            @PathVariable Long moduleId,
            @RequestBody Map<String, Object> body
    ) {
        requireStructureManagement();
        requireModule(moduleId);
        return ApiResult.ok(moduleActionService.createAction(moduleId, body));
    }

    @PutMapping("/{moduleId}/actions/{actionCode}")
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> updateAction(
            @PathVariable Long moduleId,
            @PathVariable String actionCode,
            @RequestBody Map<String, Object> body
    ) {
        if (!body.isEmpty() && Set.of("flowDesignEnabled", "workflowId", "workflowRevision", "expectedProcessId").containsAll(body.keySet())) {
            if (!canManageWorkflow(systemService.requireCurrentUser().userId())) {
                throw new ApiException(403, "没有按钮流程配置权限");
            }
        } else {
            requireStructureManagement();
        }
        requireModule(moduleId);
        jdbc.queryForList("SELECT module_id FROM sys_module WHERE module_id=? FOR UPDATE", moduleId);
        // Associate before updating the action: both are atomic and lock module/catalog/action in that order.
        if (body.containsKey("workflowId")) catalog.associate(String.valueOf(body.get("workflowId")), moduleId, actionCode,
                body.get("expectedProcessId"), body.get("workflowRevision"));
        moduleActionService.updateAction(moduleId, actionCode, body);
        return ApiResult.ok();
    }

    @DeleteMapping("/{moduleId}/actions/{actionCode}")
    public Map<String, Object> deleteAction(
            @PathVariable Long moduleId,
            @PathVariable String actionCode
    ) {
        requireStructureManagement();
        requireModule(moduleId);
        moduleActionService.deleteAction(moduleId, actionCode);
        return ApiResult.ok();
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        requireStructureManagement();
        UserAccount user = systemService.requireCurrentUser();
        return ApiResult.ok(moduleService.create(body, user.tenantId()));
    }

    @PutMapping
    public Map<String, Object> update(@RequestBody Map<String, Object> body) {
        requireStructureManagement();
        Object moduleId = body.containsKey("menuId") ? body.get("menuId") : body.get("moduleId");
        if (moduleId == null) {
            throw new ApiException(400, "moduleId is required");
        }
        requireModule(Long.parseLong(String.valueOf(moduleId)));
        moduleService.update(body);
        return ApiResult.ok();
    }

    @DeleteMapping("/{moduleId}")
    public Map<String, Object> delete(@PathVariable Long moduleId) {
        requireStructureManagement();
        requireModule(moduleId);
        moduleService.delete(moduleId);
        return ApiResult.ok();
    }

    private boolean canManageWorkflow(Long userId) {
        return systemService.hasModuleAction(userId, 340773L, "design")
                || systemService.hasModuleAction(userId, 340772L, "manage")
                || systemService.hasModuleAction(userId, 340772L, "workflow")
                || systemService.hasModuleAction(userId, 340771L, "manage");
    }

    private void requireStructureManagement() {
        if (!systemService.hasModuleAction(systemService.requireCurrentUser().userId(), 340772L, "manage")) {
            throw new ApiException(403, "没有模块结构和按钮管理权限");
        }
    }

    private Map<String, Object> requireModule(Long moduleId) {
        Map<String, Object> module = moduleService.module(moduleId);
        if (module == null) {
            throw new ApiException(404, "Module not found");
        }
        UserAccount user = systemService.requireCurrentUser();
        long tenantId = ((Number) module.get("tenantId")).longValue();
        if (tenantId != user.tenantId()) {
            throw new ApiException(403, "Module belongs to another tenant");
        }
        return module;
    }
}
