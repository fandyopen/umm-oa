package com.seal.oa.workflow;

import com.seal.oa.common.ApiResult;
import com.seal.oa.web.RequiresModuleAction;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/project/approvals")
@RequiresModuleAction(moduleId = ApprovalInboxService.MODULE, action = "access")
public class ApprovalInboxController {
    private final ApprovalInboxService inbox;

    public ApprovalInboxController(ApprovalInboxService inbox) { this.inbox = inbox; }

    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return ApiResult.ok(inbox.list(query));
    }

    @GetMapping("/options")
    public Map<String, Object> options() { return ApiResult.ok(inbox.options()); }

    @GetMapping("/detail")
    public Map<String, Object> detail(@RequestParam String taskId) {
        return ApiResult.ok(inbox.detail(taskId));
    }

    @PostMapping("/decision")
    @RequiresModuleAction(moduleId = ApprovalInboxService.MODULE, action = "approve")
    public Map<String, Object> decision(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(inbox.decide(body));
    }
}
