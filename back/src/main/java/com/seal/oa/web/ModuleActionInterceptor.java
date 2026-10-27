package com.seal.oa.web;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.system.SystemService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Comparator;
import java.util.List;

@Component
public class ModuleActionInterceptor implements HandlerInterceptor {

    private static final List<PathRule> BUSINESS_PATH_RULES = List.of(
            new PathRule("/project/business", 340590L),
            new PathRule("/project/merchant", 340352L),
            new PathRule("/project/bid", 340644L)
    ).stream()
            .sorted(Comparator.comparingInt((PathRule rule) -> rule.prefix().length()).reversed())
            .toList();

    private final SystemService systemService;

    public ModuleActionInterceptor(SystemService systemService) {
        this.systemService = systemService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequiresModuleAction requirement =
                method.getMethodAnnotation(RequiresModuleAction.class);
        RequiresAnyModuleAction anyRequirement =
                method.getMethodAnnotation(RequiresAnyModuleAction.class);
        if (requirement == null) {
            requirement = method.getBeanType().getAnnotation(RequiresModuleAction.class);
        }
        if (anyRequirement == null) {
            anyRequirement = method.getBeanType().getAnnotation(RequiresAnyModuleAction.class);
        }
        Long userId = AuthContext.userId();
        if (userId == null) {
            throw new ApiException(401, "认证失败，无法访问系统资源");
        }

        if (anyRequirement != null) {
            return requireAny(userId, anyRequirement.value());
        }

        if (requirement == null) {
            PathRule rule = businessRule(request.getRequestURI());
            if (rule == null) {
                return true;
            }
            return require(userId, rule.moduleId(), actionFor(request));
        }

        String action = requirement.action().isBlank()
                ? actionFor(request)
                : requirement.action();
        return require(userId, requirement.moduleId(), action);
    }

    private boolean require(Long userId, long moduleId, String action) {
        if (isAllowed(userId, moduleId, action)) {
            return true;
        }
        throw new ApiException(403, "没有该模块的操作权限");
    }

    private boolean requireAny(Long userId, ModuleActionRequirement[] requirements) {
        for (ModuleActionRequirement requirement : requirements) {
            if (isAllowed(userId, requirement.moduleId(), requirement.action())) {
                return true;
            }
        }
        throw new ApiException(403, "没有该模块的操作权限");
    }

    private boolean isAllowed(Long userId, long moduleId, String action) {
        if ("access".equals(action)) {
            return systemService.hasModuleAccess(userId, moduleId);
        }
        if ("write".equals(action)) {
            return systemService.hasModuleAction(userId, moduleId, "add")
                    || systemService.hasModuleAction(userId, moduleId, "edit")
                    || systemService.hasModuleAction(userId, moduleId, "manage");
        }
        return systemService.hasModuleAction(userId, moduleId, action);
    }

    private PathRule businessRule(String path) {
        return BUSINESS_PATH_RULES.stream()
                .filter(rule -> path.equals(rule.prefix()) || path.startsWith(rule.prefix() + "/"))
                .findFirst()
                .orElse(null);
    }

    private String actionFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.equals("/project/business/stage/insert")) {
            return "track";
        }
        if (path.equals("/project/business/stage/transfer")) {
            return "transfer";
        }
        if (path.startsWith("/project/business/stage/")
                || path.startsWith("/project/business/user/")) {
            return "edit";
        }
        if (path.endsWith("/export")) {
            return "export";
        }
        if (path.contains("submitApply") || path.contains("reApply")) {
            return "write";
        }
        if (isReadMethod(request.getMethod())) {
            return "access";
        }
        return switch (request.getMethod().toUpperCase()) {
            case "POST" -> "add";
            case "PUT", "PATCH" -> "edit";
            case "DELETE" -> "remove";
            default -> "access";
        };
    }

    private boolean isReadMethod(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

    private record PathRule(String prefix, long moduleId) {
    }
}
