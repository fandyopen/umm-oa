package com.seal.oa.oa;

import com.seal.oa.common.ApiResult;
import com.seal.oa.web.ModuleActionRequirement;
import com.seal.oa.web.RequiresAnyModuleAction;
import com.seal.oa.web.RequiresModuleAction;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/project/crm/contracts")
public class CrmContractController {
    private final CrmContractService contracts;
    private final CrmContractWorkflowService workflow;
    public CrmContractController(CrmContractService contracts, CrmContractWorkflowService workflow) {
        this.contracts = contracts;
        this.workflow = workflow;
    }

    @GetMapping
    @RequiresAnyModuleAction({
            @ModuleActionRequirement(moduleId = 341201, action = "access"),
            @ModuleActionRequirement(moduleId = 341202, action = "access"),
            @ModuleActionRequirement(moduleId = 341203, action = "access"),
            @ModuleActionRequirement(moduleId = 341204, action = "access")
    })
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        List<Map<String, Object>> rows = contracts.list(query);
        int page = positive(query.get("pageNum"), 1);
        int size = Math.min(positive(query.get("pageSize"), 20), 200);
        long offset = (long) (page - 1) * size;
        Map<String, Object> result = ApiResult.page(rows.subList((int) Math.min(offset, rows.size()), (int) Math.min(offset + size, rows.size())), rows.size());
        result.put("summary", contracts.summary(rows));
        return result;
    }

    @GetMapping("/customers")
    @RequiresAnyModuleAction({
            @ModuleActionRequirement(moduleId = 341201, action = "access"),
            @ModuleActionRequirement(moduleId = 341202, action = "access"),
            @ModuleActionRequirement(moduleId = 341203, action = "access"),
            @ModuleActionRequirement(moduleId = 341204, action = "access"),
            @ModuleActionRequirement(moduleId = 341205, action = "access")
    })
    public Map<String, Object> customers(@RequestParam(defaultValue = "") String name) { return ApiResult.ok(contracts.customerOptions(name)); }

    @GetMapping("/{id}")
    @RequiresAnyModuleAction({
            @ModuleActionRequirement(moduleId = 341201, action = "access"),
            @ModuleActionRequirement(moduleId = 341202, action = "access"),
            @ModuleActionRequirement(moduleId = 341203, action = "access"),
            @ModuleActionRequirement(moduleId = 341204, action = "access"),
            @ModuleActionRequirement(moduleId = 341205, action = "access"),
            @ModuleActionRequirement(moduleId = 341101, action = "access")
    })
    public Map<String, Object> detail(@PathVariable long id) { return ApiResult.ok(contracts.detail(id)); }

    @PostMapping
    @RequiresModuleAction(moduleId = 341201, action = "add")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) { return ApiResult.ok(contracts.create(body)); }

    @PutMapping("/{id}")
    @RequiresModuleAction(moduleId = 341201, action = "edit")
    public Map<String, Object> update(@PathVariable long id, @RequestBody Map<String, Object> body) { return ApiResult.ok(contracts.update(id, body)); }

    @DeleteMapping("/{id}")
    @RequiresModuleAction(moduleId = 341201, action = "remove")
    public Map<String, Object> delete(@PathVariable long id) { contracts.delete(id); return ApiResult.ok(); }

    @PostMapping("/{id}/submit")
    @RequiresModuleAction(moduleId = 341201, action = "submit")
    public Map<String, Object> submit(@PathVariable long id, @RequestBody Map<String, Object> body) { return ApiResult.ok(workflow.submit(id, body)); }

    @PostMapping("/{id}/approve")
    @RequiresModuleAction(moduleId = 341202, action = "approve")
    // Compatibility for pending contracts created before the unified workflow was introduced.
    public Map<String, Object> approve(@PathVariable long id, @RequestBody Map<String, Object> body) { return ApiResult.ok(contracts.approveLegacy(id, body)); }

    @PostMapping("/{id}/sign")
    @RequiresModuleAction(moduleId = 341201, action = "sign")
    public Map<String, Object> sign(@PathVariable long id, @RequestBody Map<String, Object> body) { return ApiResult.ok(workflow.sign(id, body)); }

    @PostMapping("/{id}/revision")
    @RequiresModuleAction(moduleId = 341204, action = "edit")
    public Map<String, Object> revision(@PathVariable long id, @RequestBody Map<String, Object> body) { return ApiResult.ok(contracts.revision(id, body)); }

    @PostMapping("/{id}/finish")
    @RequiresModuleAction(moduleId = 341201, action = "edit")
    public Map<String, Object> finish(@PathVariable long id, @RequestBody Map<String, Object> body) { return ApiResult.ok(contracts.finish(id, body)); }

    @PostMapping("/{id}/receipts")
    @RequiresModuleAction(moduleId = 341203, action = "receive")
    public Map<String, Object> receipt(@PathVariable long id, @RequestBody Map<String, Object> body) { contracts.receipt(id, body); return ApiResult.ok(); }

    @PostMapping("/{id}/reminder-log")
    @RequiresModuleAction(moduleId = 341205, action = "handle")
    public Map<String, Object> handle(@PathVariable long id, @RequestBody Map<String, Object> body) { contracts.handleReminder(id, body); return ApiResult.ok(); }

    private int positive(String value, int fallback) {
        try { return Math.max(Integer.parseInt(value), 1); } catch (Exception ignored) { return fallback; }
    }
}
