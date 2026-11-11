package com.seal.oa.system;

import com.seal.oa.common.ApiResult;
import com.seal.oa.web.RequiresModuleAction;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/system/workflows")
@RequiresModuleAction(moduleId = 340773L, action = "access")
public class WorkflowManagementController {
    private final WorkflowManagementService workflows;
    private final SystemService system;

    public WorkflowManagementController(WorkflowManagementService workflows, SystemService system) {
        this.workflows = workflows;
        this.system = system;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return ApiResult.ok(workflows.list(system.requireCurrentUser(), query));
    }

    @GetMapping("/detail")
    public Map<String, Object> detail(@RequestParam Map<String, String> query) {
        return ApiResult.ok(workflows.detail(system.requireCurrentUser(), query));
    }
}
