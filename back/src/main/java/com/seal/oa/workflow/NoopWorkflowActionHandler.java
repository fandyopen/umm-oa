package com.seal.oa.workflow;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class NoopWorkflowActionHandler implements WorkflowActionHandler {

    @Override
    public Set<String> handlerKeys() {
        return Set.of("workflow.noop");
    }

    @Override
    public Object execute(WorkflowActionContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "流程审批已完成");
        result.put("moduleId", context.moduleId());
        result.put("actionCode", context.actionCode());
        result.put("payload", context.payload());
        return result;
    }
}
