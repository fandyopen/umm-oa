package com.seal.oa.system;

import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import com.seal.oa.system.SystemService.UserAccount;
import com.seal.oa.web.RequiresModuleAction;
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
@RequiresModuleAction(moduleId = 340771L, action = "access")
public class ModuleController {

    private final ModuleService moduleService;
    private final SystemService systemService;
    private final ModuleActionService moduleActionService;

    public ModuleController(
            ModuleService moduleService,
            SystemService systemService,
            ModuleActionService moduleActionService
    ) {
        this.moduleService = moduleService;
        this.systemService = systemService;
        this.moduleActionService = moduleActionService;
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

    @PostMapping("/{moduleId}/actions")
    public Map<String, Object> createAction(
            @PathVariable Long moduleId,
            @RequestBody Map<String, Object> body
    ) {
        requireModule(moduleId);
        moduleActionService.createAction(moduleId, body);
        return ApiResult.ok();
    }

    @PutMapping("/{moduleId}/actions/{actionCode}")
    public Map<String, Object> updateAction(
            @PathVariable Long moduleId,
            @PathVariable String actionCode,
            @RequestBody Map<String, Object> body
    ) {
        requireModule(moduleId);
        moduleActionService.updateAction(moduleId, actionCode, body);
        return ApiResult.ok();
    }

    @DeleteMapping("/{moduleId}/actions/{actionCode}")
    public Map<String, Object> deleteAction(
            @PathVariable Long moduleId,
            @PathVariable String actionCode
    ) {
        requireModule(moduleId);
        moduleActionService.deleteAction(moduleId, actionCode);
        return ApiResult.ok();
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        UserAccount user = systemService.requireCurrentUser();
        return ApiResult.ok(moduleService.create(body, user.tenantId()));
    }

    @PutMapping
    public Map<String, Object> update(@RequestBody Map<String, Object> body) {
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
        requireModule(moduleId);
        moduleService.delete(moduleId);
        return ApiResult.ok();
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
