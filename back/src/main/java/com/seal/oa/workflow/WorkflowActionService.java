package com.seal.oa.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.lowcode.LowCodeWorkflowService;
import com.seal.oa.lowcode.ModuleFlowBindingService;
import com.seal.oa.system.ModuleActionService;
import com.seal.oa.system.SystemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkflowActionService {

    private final SystemService systemService;
    private final ModuleActionService moduleActionService;
    private final ModuleFlowBindingService flowBindingService;
    private final LowCodeWorkflowService workflowService;
    private final WorkflowActionRequestService requestService;
    private final ObjectMapper objectMapper;

    public WorkflowActionService(
            SystemService systemService,
            ModuleActionService moduleActionService,
            ModuleFlowBindingService flowBindingService,
            LowCodeWorkflowService workflowService,
            WorkflowActionRequestService requestService,
            ObjectMapper objectMapper
    ) {
        this.systemService = systemService;
        this.moduleActionService = moduleActionService;
        this.flowBindingService = flowBindingService;
        this.workflowService = workflowService;
        this.requestService = requestService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> execute(Map<String, Object> body) {
        long moduleId = number(body.get("moduleId"), 0L);
        String actionCode = value(body.get("actionCode"), "").trim();
        if (moduleId <= 0L || actionCode.isBlank()) {
            throw new ApiException(400, "模块和动作不能为空");
        }
        Long userId = AuthContext.userId();
        if (userId == null) {
            throw new ApiException(401, "认证失败，无法访问系统资源");
        }
        if (!systemService.hasModuleAction(userId, moduleId, actionCode)) {
            throw new ApiException(403, "没有该模块的动作权限");
        }
        Map<String, Object> action = action(moduleId, actionCode);
        String actionName = value(action.get("actionName"), actionCode);
        String handlerKey = value(action.get("handlerKey"), "");
        Map<String, Object> config = object(action.get("configJson"));
        Map<String, Object> payload = object(body.get("payload"));
        String title = title(actionName, payload);
        payload.putIfAbsent("title", title);
        payload.putIfAbsent("actionName", actionName);

        Map<String, Object> binding = flowBindingService.binding(1L, moduleId, actionCode);
        boolean approvalEnabled = number(binding.get("approvalEnabled"), 0L) == 1L;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("moduleId", moduleId);
        result.put("actionCode", actionCode);
        result.put("actionName", actionName);
        if (!approvalEnabled) {
            Object execution = requestService.executeDirect(
                    moduleId,
                    actionCode,
                    handlerKey,
                    config,
                    payload,
                    userId,
                    AuthContext.username()
            );
            result.put("status", "completed");
            result.put("result", execution);
            return result;
        }

        Map<String, Object> design = flowBindingService.activeDesign(1L, moduleId, actionCode);
        if (design.isEmpty()) {
            throw new ApiException(400, "该动作已启用审批，但流程设计尚未发布");
        }
        long requestId = requestService.create(
                moduleId,
                actionCode,
                handlerKey,
                config,
                title,
                payload,
                userId,
                AuthContext.username()
        );
        Map<String, Object> workflow = workflowService.submitBusiness(
                moduleId,
                actionCode,
                "workflowAction",
                requestId,
                title,
                userId,
                AuthContext.username()
        );
        requestService.attachWorkflow(
                requestId,
                value(workflow.get("instanceId"), ""),
                value(workflow.get("taskId"), ""),
                value(workflow.get("taskName"), "")
        );
        result.put("status", "pendingApproval");
        result.put("requestId", requestId);
        result.put("title", title);
        result.putAll(workflow);
        return result;
    }

    public List<String> handlerKeys() {
        return requestService.handlerKeys();
    }

    private Map<String, Object> action(long moduleId, String actionCode) {
        Map<String, Object> action = moduleActionService.action(moduleId, actionCode);
        if (action.isEmpty()) {
            throw new ApiException(400, "该模块不存在动作 " + actionCode);
        }
        if (!"0".equals(value(action.get("status"), "0"))) {
            throw new ApiException(400, "该模块动作已停用");
        }
        return action;
    }

    private String title(String actionName, Map<String, Object> payload) {
        String target = value(payload.get("nickName"), "");
        if (target.isBlank()) {
            target = value(payload.get("deptName"), "");
        }
        if (target.isBlank()) {
            Object userIds = payload.get("userIds");
            if (userIds instanceof List<?> values) {
                target = values.size() + "名成员";
            }
        }
        return target.isBlank() ? actionName : actionName + "：" + target;
    }

    private Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        if (value == null || String.valueOf(value).isBlank() || "null".equals(String.valueOf(value))) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(
                    String.valueOf(value),
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    }
            );
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private long number(Object value, long defaultValue) {
        try {
            return value instanceof Number number
                    ? number.longValue()
                    : Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private String value(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }
}
