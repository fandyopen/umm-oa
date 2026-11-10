package com.seal.oa.workflow;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Original HTTP APIs cannot bypass an enabled generic action approval. */
@Component
public class WorkflowApiGuardInterceptor implements HandlerInterceptor {
    private final WorkflowApiRegistry registry;
    public WorkflowApiGuardInterceptor(WorkflowApiRegistry registry) {
        this.registry = registry;
    }
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !java.util.Set.of("POST", "PUT", "PATCH", "DELETE").contains(request.getMethod())) return true;
        com.seal.oa.web.RequiresModuleAction requirement = method.getMethodAnnotation(com.seal.oa.web.RequiresModuleAction.class);
        if (requirement == null) requirement = method.getBeanType().getAnnotation(com.seal.oa.web.RequiresModuleAction.class);
        if (requirement != null && !requirement.action().isBlank()) registry.requireDirectExecutionAllowed(requirement.moduleId(), requirement.action());
        com.seal.oa.web.RequiresAnyModuleAction any = method.getMethodAnnotation(com.seal.oa.web.RequiresAnyModuleAction.class);
        if (any == null) any = method.getBeanType().getAnnotation(com.seal.oa.web.RequiresAnyModuleAction.class);
        if (any != null) for (com.seal.oa.web.ModuleActionRequirement item : any.value()) registry.requireDirectExecutionAllowed(item.moduleId(), item.action());
        return true;
    }
}
