package com.seal.oa.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.common.ApiException;
import com.seal.oa.system.ModuleActionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class WorkflowActionRequestService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ModuleActionService moduleActionService;
    private final List<WorkflowActionHandler> handlers;

    public WorkflowActionRequestService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            ModuleActionService moduleActionService,
            List<WorkflowActionHandler> handlers
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.moduleActionService = moduleActionService;
        this.handlers = handlers;
    }

    @Transactional
    public long create(
            long moduleId,
            String actionCode,
            String handlerKey,
            Map<String, Object> config,
            String title,
            Map<String, Object> payload,
            Long operatorId,
            String operatorName
    ) {
        WorkflowActionContext context = new WorkflowActionContext(
                0L,
                moduleId,
                actionCode,
                handlerKey,
                config,
                payload,
                operatorId,
                operatorName
        );
        handler(handlerKey).validate(context);
        Map<String, Object> storedPayload = new LinkedHashMap<>(payload);
        storedPayload.putIfAbsent("title", title);
        storedPayload.putIfAbsent("actionCode", actionCode);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO oa_workflow_request (
                        tenant_id, module_id, action_code, handler_key, config_json,
                        title, payload_json, status, create_by, create_time, update_time
                    ) VALUES (1, ?, ?, ?, ?, ?, ?, 'running', ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, moduleId);
            statement.setString(2, actionCode);
            statement.setString(3, handlerKey);
            statement.setString(4, json(config));
            statement.setString(5, title);
            statement.setString(6, json(storedPayload));
            statement.setString(7, operatorName);
            statement.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            statement.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
            return statement;
        }, keyHolder);
        Map<String, Object> keys = Objects.requireNonNull(keyHolder.getKeys());
        long id = number(keys.get("id"), 0L);
        if (id <= 0L) {
            throw new ApiException(500, "审批申请创建失败");
        }
        return id;
    }

    @Transactional
    public Object executeDirect(
            long moduleId,
            String actionCode,
            String handlerKey,
            Map<String, Object> config,
            Map<String, Object> payload,
            Long operatorId,
            String operatorName
    ) {
        WorkflowActionContext context = new WorkflowActionContext(
                0L,
                moduleId,
                actionCode,
                handlerKey,
                config,
                payload,
                operatorId,
                operatorName
        );
        WorkflowActionHandler handler = handler(handlerKey);
        handler.validate(context);
        return handler.execute(context);
    }

    public WorkflowActionHandler handler(String handlerKey) {
        if (handlerKey == null || handlerKey.isBlank()) {
            throw new ApiException(400, "动作尚未配置 handlerKey");
        }
        return handlers.stream()
                .filter(candidate -> candidate.supports(handlerKey))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        400,
                        "执行器 " + handlerKey + " 尚未注册"
                ));
    }

    public List<String> handlerKeys() {
        return handlers.stream()
                .flatMap(handler -> handler.handlerKeys().stream())
                .distinct()
                .sorted()
                .toList();
    }

    @Transactional
    public void attachWorkflow(long requestId, String instanceId, String taskId, String taskName) {
        jdbc.update("""
                UPDATE oa_workflow_request
                SET process_instance_id = ?, process_task_id = ?, process_task_name = ?,
                    status = 'running', update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, instanceId, taskId, taskName, requestId);
    }

    @Transactional
    public void updateWorkflowState(
            String instanceId,
            long requestId,
            String businessStatus,
            String taskId,
            String taskName
    ) {
        Map<String, Object> request = request(requestId);
        if (request.isEmpty()) {
            return;
        }
        if ("审批通过".equals(businessStatus)) {
            executeApproved(request, instanceId, taskId);
            return;
        }
        if ("审批未通过".equals(businessStatus)) {
            jdbc.update("""
                    UPDATE oa_workflow_request
                    SET status = 'rejected', process_instance_id = ?, process_task_id = ?,
                        process_task_name = ?, update_time = CURRENT_TIMESTAMP,
                        complete_time = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, instanceId, taskId, taskName, requestId);
            return;
        }
        jdbc.update("""
                UPDATE oa_workflow_request
                SET status = 'running', process_instance_id = ?, process_task_id = ?,
                    process_task_name = ?, update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, instanceId, taskId, taskName, requestId);
    }

    public Map<String, Object> payload(long requestId) {
        Map<String, Object> request = request(requestId);
        return object(value(request.get("payload_json"), "{}"));
    }

    private void executeApproved(Map<String, Object> request, String instanceId, String taskId) {
        long requestId = number(request.get("id"), 0L);
        if (List.of("approved", "completed").contains(value(request.get("status"), ""))) {
            return;
        }
        long moduleId = number(request.get("module_id"), 0L);
        String actionCode = value(request.get("action_code"), "");
        String handlerKey = value(request.get("handler_key"), "");
        Map<String, Object> config = object(value(request.get("config_json"), "{}"));
        if (handlerKey.isBlank()) {
            Map<String, Object> action = moduleActionService.action(moduleId, actionCode);
            handlerKey = value(action.get("handlerKey"), "");
            config = object(action.get("configJson"));
        }
        Map<String, Object> payload = object(value(request.get("payload_json"), "{}"));
        WorkflowActionContext context = new WorkflowActionContext(
                requestId,
                moduleId,
                actionCode,
                handlerKey,
                config,
                payload,
                null,
                value(request.get("create_by"), "")
        );
        WorkflowActionHandler handler = handler(handlerKey);
        handler.validate(context);
        Object executionResult = handler.execute(context);
        Timestamp completedAt = Timestamp.valueOf(LocalDateTime.now());
        jdbc.update("""
                UPDATE oa_workflow_request
                SET status = 'approved', process_instance_id = ?, process_task_id = ?,
                    result_json = ?, update_time = ?, complete_time = ?
                WHERE id = ?
                """,
                instanceId,
                taskId,
                json(executionResult),
                completedAt,
                completedAt,
                requestId);
    }

    private Map<String, Object> request(long requestId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT * FROM oa_workflow_request WHERE id = ?
                """, requestId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
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

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new ApiException(500, "审批请求序列化失败");
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
