package com.seal.oa.workflow;

import java.util.Set;

public interface WorkflowActionHandler {

    Set<String> handlerKeys();

    default boolean supports(String handlerKey) {
        return handlerKey != null && handlerKeys().contains(handlerKey);
    }

    default void validate(WorkflowActionContext context) {
    }

    Object execute(WorkflowActionContext context);
}
