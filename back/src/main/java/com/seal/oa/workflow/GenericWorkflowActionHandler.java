package com.seal.oa.workflow;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;

@Component
public class GenericWorkflowActionHandler implements WorkflowActionHandler {
    private final WorkflowApiRegistry registry;

    public GenericWorkflowActionHandler(WorkflowApiRegistry registry) { this.registry = registry; }
    @Override public Set<String> handlerKeys() { return Set.of(WorkflowApiRegistry.HANDLER); }
    @Override public void validateConfiguration(long moduleId, String actionCode, Map<String, Object> config) { registry.require(moduleId, config); }
    @Override public void validate(WorkflowActionContext context) { registry.validate(context); }
    @Override public void submitted(WorkflowActionContext context) { context.payload().put("_workflowApplicantId", context.operatorId()); }
    @Override public Object execute(WorkflowActionContext context) { return registry.execute(context); }
}
