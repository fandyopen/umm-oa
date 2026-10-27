package com.seal.oa.workflow;

import java.util.Map;

public record WorkflowActionContext(
        long requestId,
        long moduleId,
        String actionCode,
        String handlerKey,
        Map<String, Object> config,
        Map<String, Object> payload,
        Long operatorId,
        String operatorName
) {
}
