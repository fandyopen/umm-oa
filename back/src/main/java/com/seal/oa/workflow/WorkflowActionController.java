package com.seal.oa.workflow;

import com.seal.oa.common.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class WorkflowActionController {

    private final WorkflowActionService actionService;

    public WorkflowActionController(WorkflowActionService actionService) {
        this.actionService = actionService;
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
