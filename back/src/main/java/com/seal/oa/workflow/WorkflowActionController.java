package com.seal.oa.workflow;

import com.seal.oa.common.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import com.seal.oa.common.ApiException;
import com.seal.oa.system.SystemService;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class WorkflowActionController {

    private final WorkflowActionService actionService;
    private final WorkflowApiRegistry apiRegistry;
    private final SystemService systemService;

    public WorkflowActionController(WorkflowActionService actionService, WorkflowApiRegistry apiRegistry, SystemService systemService) {
        this.actionService = actionService;
        this.apiRegistry = apiRegistry;
        this.systemService = systemService;
    }

    @GetMapping("/workflow/action/apis")
    public Map<String, Object> apis(@RequestParam long moduleId) {
        long userId = systemService.requireCurrentUser().userId();
        if (!systemService.hasModuleAction(userId, 340772L, "manage")
                && !systemService.hasModuleAction(userId, 340772L, "workflow")
                && !systemService.hasModuleAction(userId, 340771L, "manage")) {
            throw new ApiException(403, "没有按钮流程配置权限");
        }
        apiRegistry.requireCatalogTenant(moduleId, systemService.requireCurrentUser().tenantId());
        return ApiResult.ok(apiRegistry.catalog(moduleId));
    }

    @GetMapping("/workflow/action/handlers")
    public Map<String, Object> handlers() {
        return ApiResult.ok(actionService.handlerKeys());
    }

    @PostMapping("/workflow/action/execute")
    public Map<String, Object> execute(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(actionService.execute(body));
    }
}
