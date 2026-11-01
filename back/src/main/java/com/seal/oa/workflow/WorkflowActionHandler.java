package com.seal.oa.workflow;

import java.util.Set;

public interface WorkflowActionHandler {

    Set<String> handlerKeys();

    default boolean supports(String handlerKey) {
        return handlerKey != null && handlerKeys().contains(handlerKey);
    }

    default boolean supportsAction(long moduleId, String actionCode) {
        return true;
    }

    default void validate(WorkflowActionContext context) {
    }

    default void submitted(WorkflowActionContext context) {
    }

    default void workflowStateChanged(WorkflowActionContext context, String instanceId,
                                      String businessStatus, String taskId, String taskName) {
    }

    default void validateCompletion(WorkflowActionContext context, String instanceId,
                                    String taskId, boolean pass, String comment, Long operatorId) {
    }

    Object execute(WorkflowActionContext context);
}
