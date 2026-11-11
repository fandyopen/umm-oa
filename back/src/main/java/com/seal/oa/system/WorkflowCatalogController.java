package com.seal.oa.system;

import com.seal.oa.common.ApiResult;
import com.seal.oa.common.ApiException;
import com.seal.oa.web.ModuleActionRequirement;
import com.seal.oa.web.RequiresAnyModuleAction;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/system/workflows/catalog")
@RequiresAnyModuleAction({
        @ModuleActionRequirement(moduleId = 340773L, action = "design"),
        @ModuleActionRequirement(moduleId = 340772L, action = "manage"),
        @ModuleActionRequirement(moduleId = 340772L, action = "workflow"),
        @ModuleActionRequirement(moduleId = 340771L, action = "manage")
})
public class WorkflowCatalogController {
    private final WorkflowCatalogService catalog;
    public WorkflowCatalogController(WorkflowCatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/options")
    public Map<String, Object> options(@RequestParam(defaultValue = "0") long moduleId) {
        return ApiResult.ok(catalog.options(moduleId));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(catalog.create(body));
    }

    @GetMapping("/{id}")
    public Map<String, Object> designer(@PathVariable String id) { return ApiResult.ok(catalog.designer(id)); }

    @PutMapping("/{id}")
    public Map<String, Object> save(@PathVariable String id, @RequestBody Map<String, Object> body) {
        catalog.save(id, body);
        return ApiResult.ok(catalog.designer(id));
    }

    @PostMapping("/{id}/associate")
    public Map<String, Object> associate(@PathVariable String id, @RequestBody Map<String, Object> body) {
        long moduleId;
        try { moduleId = Long.parseLong(String.valueOf(body.getOrDefault("moduleId", 0))); }
        catch (NumberFormatException exception) { throw new ApiException(400, "请选择有效模块"); }
        catalog.associate(id, moduleId,
                String.valueOf(body.getOrDefault("actionCode", "")), body.get("expectedProcessId"), body.get("workflowRevision"));
        return ApiResult.ok();
    }
}
