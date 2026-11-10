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
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public WorkflowActionService(
            SystemService systemService,
            ModuleActionService moduleActionService,
            ModuleFlowBindingService flowBindingService,
            LowCodeWorkflowService workflowService,
            WorkflowActionRequestService requestService,
            ObjectMapper objectMapper,
            org.springframework.jdbc.core.JdbcTemplate jdbc
    ) {
        this.systemService = systemService;
        this.moduleActionService = moduleActionService;
        this.flowBindingService = flowBindingService;
        this.workflowService = workflowService;
        this.requestService = requestService;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    @Transactional
    public Map<String, Object> execute(Map<String, Object> body) {
        String key = value(body.get("submissionKey"), "");
        if (key.isBlank()) return executeAction(body); // Existing callers retain their contract.
        if (!key.matches("[A-Za-z0-9_-]{8,100}")) throw new ApiException(400, "提交标识格式错误");
        Long userId = AuthContext.userId();
        if (jdbc.queryForList("SELECT user_id FROM sys_user WHERE user_id=? AND status='0' AND del_flag='0' FOR UPDATE", Long.class, userId).isEmpty())
            throw new ApiException(401, "申请人不存在或已停用");
        String fingerprint;
        try {
            byte[] canonical = objectMapper.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsBytes(Map.of("moduleId", number(body.get("moduleId"), 0), "actionCode", value(body.get("actionCode"), "").trim(), "payload", object(body.get("payload"))));
            fingerprint = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (Exception exception) { throw new ApiException(400, "提交数据无法序列化"); }
        List<Map<String, Object>> previous = jdbc.queryForList("SELECT fingerprint,response_json FROM oa_workflow_action_submission WHERE user_id=? AND submission_key=?", userId, key);
        if (!previous.isEmpty()) {
            if (!fingerprint.equals(previous.get(0).get("fingerprint"))) throw new ApiException(409, "该提交标识已用于其他内容，请重新提交");
            return object(previous.get(0).get("response_json"));
        }
        Map<String, Object> result = executeAction(body);
        try {
            jdbc.update("INSERT INTO oa_workflow_action_submission(user_id,submission_key,fingerprint,response_json) VALUES (?,?,?,?)",
                    userId, key, fingerprint, objectMapper.writeValueAsString(result));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw new ApiException(500, "提交结果无法保存"); }
        return result;
    }

    private Map<String, Object> executeAction(Map<String, Object> body) {
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
        moduleActionService.requireWorkflowExecution(moduleId, actionCode);
        String actionName = value(action.get("actionName"), actionCode);
        String handlerKey = value(action.get("handlerKey"), "");
        Map<String, Object> config = object(action.get("configJson"));
        Map<String, Object> payload = object(body.get("payload"));
        payload.keySet().removeIf(key -> key.startsWith("_workflow"));
        String title = title(actionName, payload);
        if (WorkflowApiRegistry.HANDLER.equals(handlerKey)) {
            payload.put("_workflowApi", true);
            String template = value(config.get("titleTemplate"), "");
            if (!template.isBlank()) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{([A-Za-z][A-Za-z0-9_.]*)}").matcher(template);
                StringBuffer rendered = new StringBuffer();
                while (matcher.find()) matcher.appendReplacement(rendered, java.util.regex.Matcher.quoteReplacement(value(WorkflowApiRegistry.read(payload, matcher.group(1)), "")));
                matcher.appendTail(rendered);
                title = rendered.toString();
            }
            if (config.get("displayFields") instanceof List<?> fields) {
                List<Map<String, Object>> display = new java.util.ArrayList<>();
                for (Object raw : fields) {
                    Map<String, Object> field = WorkflowApiRegistry.map(raw);
                    display.add(Map.of("label", value(field.get("label"), ""), "value", java.util.Objects.requireNonNullElse(WorkflowApiRegistry.read(payload, value(field.get("field"), "")), "")));
                }
                payload.put("_workflowDisplay", display);
            }
            payload.put("title", title);
        }
        if (title.length() > 200) title = title.substring(0, 200);
        payload.putIfAbsent("title", title);
        payload.putIfAbsent("actionName", actionName);

        boolean designEnabled = ModuleActionService.enabled(action.get("flowDesignEnabled"));
        Map<String, Object> design = flowBindingService.activeDesign(1L, moduleId, actionCode);
        boolean approvalEnabled = designEnabled && !design.isEmpty();
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

        payload.put("_workflowDefinition", Map.of(
                "menuName", value(design.get("menu_name"), actionName),
                "processData", object(design.get("process_data"))
        ));
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
