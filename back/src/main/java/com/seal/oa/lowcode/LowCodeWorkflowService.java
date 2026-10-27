package com.seal.oa.lowcode;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.common.ApiException;
import com.seal.oa.workflow.WorkflowActionRequestService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class LowCodeWorkflowService {

    private static final String PROCESS_PREFIX = "form:";
    private static final String BUSINESS_PREFIX = "business:";
    private static final String TASK_SEPARATOR = "::";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ModuleFlowBindingService flowBindingService;
    private final WorkflowActionRequestService actionRequestService;

    public LowCodeWorkflowService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            ModuleFlowBindingService flowBindingService,
            WorkflowActionRequestService actionRequestService
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.flowBindingService = flowBindingService;
        this.actionRequestService = actionRequestService;
    }

    @Transactional
    public Map<String, Object> submit(
            Map<String, Object> body,
            Long starterId,
            String starterUsername
    ) {
        String formKey = value(body.get("formId"), "");
        long dataId = number(body.get("dataId"), 0L);
        if (formKey.isBlank() || dataId <= 0L) {
            throw new ApiException(400, "表单或表单数据不能为空");
        }

        Map<String, Object> form = form(formKey);
        if (!"flow".equals(value(form.get("form_type"), ""))) {
            throw new ApiException(400, "该表单不是流程表单");
        }

        String processKey = PROCESS_PREFIX + formKey;
        List<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT * FROM oa_process_instance
                WHERE process_key = ? AND business_type = 'lowcode' AND business_id = ?
                ORDER BY start_time DESC LIMIT 1
                """, processKey, dataId);
        if (!existing.isEmpty()) {
            return existingResult(existing.get(0));
        }

        Map<String, Object> processData = object(value(form.get("process_data"), "{}"));
        List<Map<String, Object>> nodes = nodes(processData);
        Map<String, Object> firstNode = firstApproval(nodes, null);
        if (firstNode == null) {
            throw new ApiException(400, "请先在流程设计中添加审批节点");
        }

        String instanceId = "lc-" + UUID.randomUUID().toString().replace("-", "");
        String taskId = taskId(instanceId, value(firstNode.get("id"), "approval"));
        Assignment assignee = resolveAssignee(value(firstNode.get("approver"), ""), starterId);
        String starterName = starterName(starterId, starterUsername);
        String flowName = value(
                object(value(form.get("basic_setting"), "{}")).get("flowName"),
                value(form.get("flow_name"), "流程表单")
        );
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        publishDefinition(processKey, flowName, processData);
        jdbc.update("""
                INSERT INTO oa_process_instance (
                    id, tenant_id, process_key, business_type, business_id, starter_id,
                    starter_name, status, start_time
                ) VALUES (?, 1, ?, 'lowcode', ?, ?, ?, 'running', ?)
                """, instanceId, processKey, dataId, starterId, starterName, now);
        insertTaskAndTodo(
                instanceId,
                taskId,
                firstNode,
                assignee,
                flowName,
                "审批：" + flowName,
                dataId,
                now
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instanceId", instanceId);
        result.put("taskId", taskId);
        result.put("taskName", value(firstNode.get("name"), "审批"));
        result.put("assigneeName", assignee.name());
        return result;
    }

    @Transactional
    public Map<String, Object> submitBusiness(
            long menuId,
            String businessType,
            long businessId,
            String businessTitle,
            Long starterId,
            String starterUsername
    ) {
        return submitBusiness(
                menuId,
                "page",
                businessType,
                businessId,
                businessTitle,
                starterId,
                starterUsername
        );
    }

    @Transactional
    public Map<String, Object> submitBusiness(
            long menuId,
            String actionCode,
            String businessType,
            long businessId,
            String businessTitle,
            Long starterId,
            String starterUsername
    ) {
        if (menuId <= 0L || businessId <= 0L || businessType == null || businessType.isBlank()) {
            throw new ApiException(400, "菜单、业务类型和业务数据不能为空");
        }
        String normalizedAction = actionCode == null || actionCode.isBlank() ? "page" : actionCode;
        Map<String, Object> design = publishedDesign(menuId, normalizedAction);
        if (design.isEmpty()) {
            throw new ApiException(400, "该页面尚未发布流程设计");
        }

        String processKey = businessProcessKey(menuId, normalizedAction);
        List<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT * FROM oa_process_instance
                WHERE process_key = ? AND business_type = ? AND business_id = ?
                ORDER BY start_time DESC LIMIT 1
                """, processKey, businessType, businessId);
        if (!existing.isEmpty()) {
            return existingResult(existing.get(0));
        }

        Map<String, Object> processData = object(value(design.get("process_data"), "{}"));
        List<Map<String, Object>> nodes = nodes(processData);
        Map<String, Object> firstNode = firstApproval(nodes, null);
        if (firstNode == null) {
            throw new ApiException(400, "请先在流程设计中添加审批节点");
        }

        String menuName = value(design.get("menu_name"), "业务审批");
        String flowName = menuName + "审批";
        String instanceId = "biz-" + UUID.randomUUID().toString().replace("-", "");
        String taskId = taskId(instanceId, value(firstNode.get("id"), "approval"));
        Assignment assignee = resolveAssignee(value(firstNode.get("approver"), ""), starterId);
        String starterName = starterName(starterId, starterUsername);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        publishDefinition(processKey, flowName, processData);
        jdbc.update("""
                INSERT INTO oa_process_instance (
                    id, tenant_id, process_key, business_type, business_id, starter_id,
                    starter_name, status, start_time
                ) VALUES (?, 1, ?, ?, ?, ?, ?, 'running', ?)
                """, instanceId, processKey, businessType, businessId, starterId, starterName, now);

        String todoTitle = "审批：" + flowName
                + (businessTitle == null || businessTitle.isBlank() ? "" : " - " + businessTitle);
        insertTaskAndTodo(
                instanceId,
                taskId,
                firstNode,
                assignee,
                flowName,
                todoTitle,
                businessId,
                now
        );
        updateBusinessState(instanceId, "等待审批", taskId, value(firstNode.get("name"), "审批"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instanceId", instanceId);
        result.put("taskId", taskId);
        result.put("taskName", value(firstNode.get("name"), "审批"));
        result.put("assigneeName", assignee.name());
        return result;
    }

    public long waitingCount(Long userId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM oa_todo td
                JOIN oa_process_instance i ON i.id = td.process_instance_id
                WHERE td.user_id = ? AND td.status = 'todo'
                  AND (i.process_key LIKE 'form:%' OR i.process_key LIKE 'business:%')
                """, Long.class, userId);
        return count == null ? 0L : count;
    }

    public List<Map<String, Object>> tasks(String kind, Long userId) {
        if ("start".equals(kind)) {
            List<Map<String, Object>> instances = jdbc.queryForList("""
                    SELECT * FROM oa_process_instance
                    WHERE starter_id = ?
                      AND (process_key LIKE 'form:%' OR process_key LIKE 'business:%')
                    ORDER BY start_time DESC
                    """, userId);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> instance : instances) {
                rows.add(view(instance, latestTask(value(instance.get("id"), "")), "started"));
            }
            return rows;
        }
        if ("done".equals(kind)) {
            List<Map<String, Object>> tasks = jdbc.queryForList("""
                    SELECT t.*, i.process_key, i.business_id, i.starter_id, i.starter_name,
                           i.status AS instance_status, i.start_time, i.end_time
                    FROM oa_process_task t
                    JOIN oa_process_instance i ON i.id = t.instance_id
                    WHERE t.assignee_id = ? AND t.status = 'done'
                      AND (i.process_key LIKE 'form:%' OR i.process_key LIKE 'business:%')
                    ORDER BY t.complete_time DESC
                    """, userId);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> task : tasks) {
                rows.add(view(instance(task), task, "done"));
            }
            return rows;
        }
        if ("copy".equals(kind)) {
            return List.of();
        }

        List<Map<String, Object>> tasks = jdbc.queryForList("""
                SELECT t.*, i.process_key, i.business_id, i.starter_id, i.starter_name,
                       i.status AS instance_status, i.start_time, i.end_time
                FROM oa_todo td
                JOIN oa_process_task t ON t.id = td.task_id
                JOIN oa_process_instance i ON i.id = td.process_instance_id
                WHERE td.user_id = ? AND td.status = 'todo'
                  AND (i.process_key LIKE 'form:%' OR i.process_key LIKE 'business:%')
                ORDER BY td.create_time DESC
                """, userId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> task : tasks) {
            rows.add(view(instance(task), task, "todo"));
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> complete(Map<String, Object> body, Long operatorId) {
        String taskId = value(body.get("taskId"), "");
        String instanceId = value(body.get("instanceId"), "");
        if (taskId.isBlank() || instanceId.isBlank()) {
            throw new ApiException(400, "任务或流程实例不能为空");
        }

        Map<String, Object> task = row("""
                SELECT * FROM oa_process_task WHERE id = ? AND instance_id = ?
                """, taskId, instanceId);
        if (task.isEmpty()) {
            throw new ApiException(404, "审批任务不存在");
        }
        if ("done".equals(value(task.get("status"), ""))) {
            return existingResult(row("SELECT * FROM oa_process_instance WHERE id = ?", instanceId));
        }
        if (number(task.get("assignee_id"), 0L) != operatorId) {
            throw new ApiException(403, "当前账号不是该任务审批人");
        }

        Map<String, Object> variables = object(body.get("variables"));
        String comment = value(variables.get("comment"), value(body.get("comment"), "同意"));
        boolean pass = !"false".equalsIgnoreCase(value(variables.get("pass"), "true"));
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        jdbc.update("""
                UPDATE oa_process_task SET status = 'done', complete_time = ? WHERE id = ?
                """, now, taskId);
        jdbc.update("""
                UPDATE oa_todo SET status = 'done' WHERE task_id = ?
                """, taskId);
        saveComment(taskId, comment, operatorId, now);

        Map<String, Object> instance = row(
                "SELECT * FROM oa_process_instance WHERE id = ?",
                instanceId
        );
        if (!pass) {
            jdbc.update("""
                    UPDATE oa_process_instance SET status = 'rejected', end_time = ? WHERE id = ?
                    """, now, instanceId);
            updateBusinessState(instanceId, "审批未通过", taskId, "");
            return existingResult(instance);
        }

        Map<String, Object> definition = definition(instance);
        List<Map<String, Object>> nodes = nodes(object(value(definition.get("process_data"), "{}")));
        String currentNodeId = nodeIdFromTask(taskId);
        Map<String, Object> nextNode = firstApproval(nodes, currentNodeId);
        if (nextNode == null) {
            jdbc.update("""
                    UPDATE oa_process_instance SET status = 'approved', end_time = ? WHERE id = ?
                    """, now, instanceId);
            updateBusinessState(instanceId, "审批通过", taskId, "");
            return existingResult(instance);
        }

        String nextTaskId = taskId(instanceId, value(nextNode.get("id"), "approval"));
        Assignment assignee = resolveAssignee(
                value(nextNode.get("approver"), ""),
                number(instance.get("starter_id"), operatorId)
        );
        String flowName = flowName(definition);
        insertTaskAndTodo(
                instanceId,
                nextTaskId,
                nextNode,
                assignee,
                flowName,
                "审批：" + flowName,
                number(instance.get("business_id"), 0L),
                now
        );
        updateBusinessState(instanceId, "等待审批", nextTaskId, value(nextNode.get("name"), "审批"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instanceId", instanceId);
        result.put("taskId", nextTaskId);
        result.put("taskName", value(nextNode.get("name"), "审批"));
        result.put("assigneeName", assignee.name());
        return result;
    }

    public List<Map<String, Object>> history(String instanceId) {
        Map<String, Object> instance = row("""
                SELECT * FROM oa_process_instance
                WHERE id = ?
                  AND (process_key LIKE 'form:%' OR process_key LIKE 'business:%')
                """, instanceId);
        if (instance.isEmpty()) {
            return List.of();
        }
        definition(instance);
        List<Map<String, Object>> history = new ArrayList<>();
        Timestamp startTime = (Timestamp) instance.get("start_time");
        String starterName = value(instance.get("starter_name"), "");
        long starterId = number(instance.get("starter_id"), 0L);
        history.add(historyRow(
                instanceId,
                "startEvent",
                "开始",
                "startEvent",
                starterId,
                starterName,
                "提交申请",
                startTime,
                startTime
        ));
        history.add(historyRow(
                instanceId,
                "start",
                "发起人",
                "userTask",
                starterId,
                starterName,
                "发起申请",
                startTime,
                startTime
        ));

        List<Map<String, Object>> completed = jdbc.queryForList("""
                SELECT * FROM oa_process_task
                WHERE instance_id = ? AND status = 'done'
                ORDER BY complete_time, create_time
                """, instanceId);
        for (Map<String, Object> task : completed) {
            history.add(historyRow(
                    instanceId,
                    nodeIdFromTask(value(task.get("id"), "")),
                    value(task.get("task_name"), "审批"),
                    "userTask",
                    number(task.get("assignee_id"), 0L),
                    value(task.get("assignee_name"), ""),
                    comment(value(task.get("id"), "")),
                    (Timestamp) task.get("create_time"),
                    (Timestamp) task.get("complete_time")
            ));
        }

        String status = value(instance.get("status"), "");
        if (List.of("approved", "rejected").contains(status)) {
            Timestamp endTime = (Timestamp) instance.get("end_time");
            history.add(historyRow(
                    instanceId,
                    "end",
                    "结束",
                    "endEvent",
                    starterId,
                    starterName,
                    "approved".equals(status) ? "流程结束" : "流程终止",
                    endTime,
                    endTime
            ));
        }
        return history;
    }

    public List<Map<String, Object>> elements(String instanceId) {
        Map<String, Object> instance = row("""
                SELECT * FROM oa_process_instance
                WHERE id = ?
                  AND (process_key LIKE 'form:%' OR process_key LIKE 'business:%')
                """, instanceId);
        if (instance.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> nodes = nodes(object(value(
                definition(instance).get("process_data"),
                "{}"
        )));
        String currentNodeId = activeNodeId(instanceId);
        List<Map<String, Object>> elements = new ArrayList<>();
        elements.add(element("startEvent", "开始", "start", false));
        for (Map<String, Object> node : nodes) {
            String type = value(node.get("type"), "");
            elements.add(element(
                    value(node.get("id"), ""),
                    value(node.get("name"), ""),
                    "end".equals(type) ? "end" : "node",
                    value(node.get("id"), "").equals(currentNodeId)
            ));
        }
        return elements;
    }

    public Map<String, Object> flowIds(String instanceId) {
        Map<String, Object> instance = row("""
                SELECT * FROM oa_process_instance
                WHERE id = ?
                  AND (process_key LIKE 'form:%' OR process_key LIKE 'business:%')
                """, instanceId);
        if (instance.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> nodes = nodes(object(value(
                definition(instance).get("process_data"),
                "{}"
        )));
        String activeNodeId = activeNodeId(instanceId);
        List<String> nodeIds = new ArrayList<>();
        nodeIds.add("startEvent");
        for (Map<String, Object> node : nodes) {
            String id = value(node.get("id"), "");
            nodeIds.add(id);
            if (id.equals(activeNodeId)) {
                break;
            }
        }
        List<String> edges = new ArrayList<>();
        for (int index = 0; index < nodeIds.size() - 1; index++) {
            edges.add(nodeIds.get(index) + "->" + nodeIds.get(index + 1));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodeIds);
        result.put("edges", edges);
        result.put("currIds", List.of("", activeNodeId == null ? "" : activeNodeId));
        return result;
    }

    private void insertTaskAndTodo(
            String instanceId,
            String taskId,
            Map<String, Object> node,
            Assignment assignee,
            String flowName,
            String todoTitle,
            long dataId,
            Timestamp now
    ) {
        jdbc.update("""
                INSERT INTO oa_process_task (
                    id, instance_id, task_name, assignee_id, assignee_name, status, create_time
                ) VALUES (?, ?, ?, ?, ?, 'todo', ?)
                """,
                taskId,
                instanceId,
                value(node.get("name"), "审批"),
                assignee.id(),
                assignee.name(),
                now
        );
        jdbc.update("""
                INSERT INTO oa_todo (
                    tenant_id, user_id, title, business_type, business_id,
                    process_instance_id, task_id, status, create_time
                ) VALUES (1, ?, ?, 'lowcode', ?, ?, ?, 'todo', ?)
                """,
                assignee.id(),
                todoTitle,
                dataId,
                instanceId,
                taskId,
                now
        );
    }

    private void publishDefinition(
            String processKey,
            String flowName,
            Map<String, Object> processData
    ) {
        int updated = jdbc.update("""
                UPDATE oa_process_definition SET process_name = ?, category = 'lowcode',
                    definition_json = ?, status = 'published'
                WHERE process_key = ?
                """, flowName, json(processData), processKey);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO oa_process_definition (
                        tenant_id, process_key, process_name, category, version,
                        definition_json, status
                    ) VALUES (1, ?, ?, 'lowcode', 1, ?, 'published')
                    """, processKey, flowName, json(processData));
        }
    }

    private Map<String, Object> view(
            Map<String, Object> instance,
            Map<String, Object> task,
            String type
    ) {
        if (instance == null || instance.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> definition = definition(instance);
        Map<String, Object> basicSetting = object(value(definition.get("basic_setting"), "{}"));
        Map<String, Object> formData = object(value(definition.get("form_data"), "{}"));
        Map<String, Object> processData = object(value(definition.get("process_data"), "{}"));
        long dataId = number(instance.get("business_id"), 0L);
        Map<String, Object> data = businessData(instance);
        data.put("status", flowStatus(value(instance.get("status"), "running")));
        data.put("id", dataId);
        Map<String, Object> form = new LinkedHashMap<>(data);
        form.put("state", formState(value(instance.get("status"), "running")));
        form.put("title", value(basicSetting.get("flowName"), "自定义审批"));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("instanceId", value(instance.get("id"), ""));
        row.put("taskId", task == null ? "" : value(task.get("id"), ""));
        row.put("taskName", task == null ? "" : value(task.get("task_name"), ""));
        row.put("assigneeName", task == null ? "" : value(task.get("assignee_name"), ""));
        row.put("applyUserId", number(instance.get("starter_id"), 0L));
        row.put("applyUserName", value(instance.get("starter_name"), ""));
        row.put("createTime", task == null ? instance.get("start_time") : task.get("create_time"));
        row.put("type", type);
        row.put("basicSetting", basicSetting);
        row.put("formData", formData);
        row.put("processData", processData);
        row.put("fields", fields(formData));
        row.put("data", data);
        row.put("form", form);
        row.put("formId", dataId);
        row.put("formKey", value(instance.get("process_key"), ""));
        row.put(
                "businessType",
                value(instance.get("process_key"), "").startsWith(BUSINESS_PREFIX)
                        ? value(instance.get("business_type"), "business")
                        : "lowcode"
        );
        row.put("businessId", dataId);
        row.put("processName", flowName(definition));
        row.put("workflowId", task == null ? "" : Math.abs(value(task.get("id"), "").hashCode()));
        row.put("readUsers", null);
        Map<String, Object> lastActivity = new LinkedHashMap<>();
        lastActivity.put("comment", "发起申请");
        lastActivity.put("assigneeName", value(instance.get("starter_name"), ""));
        row.put("lastHistoricActivity", lastActivity);
        return row;
    }

    private Map<String, Object> existingResult(Map<String, Object> instance) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> task = latestTask(value(instance.get("id"), ""));
        result.put("instanceId", value(instance.get("id"), ""));
        result.put("taskId", task == null ? "" : value(task.get("id"), ""));
        result.put("taskName", task == null ? "" : value(task.get("task_name"), ""));
        result.put("assigneeName", task == null ? "" : value(task.get("assignee_name"), ""));
        return result;
    }

    private Map<String, Object> latestTask(String instanceId) {
        if (instanceId.isBlank()) {
            return null;
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT * FROM oa_process_task
                WHERE instance_id = ? ORDER BY create_time DESC, id DESC LIMIT 1
                """, instanceId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> instance(Map<String, Object> task) {
        return row("SELECT * FROM oa_process_instance WHERE id = ?", value(task.get("instance_id"), ""));
    }

    private String activeNodeId(String instanceId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id FROM oa_process_task
                WHERE instance_id = ? AND status = 'todo'
                ORDER BY create_time DESC LIMIT 1
                """, instanceId);
        return rows.isEmpty() ? "" : nodeIdFromTask(value(rows.get(0).get("id"), ""));
    }

    private Assignment resolveAssignee(String approver, long starterId) {
        String roleOrUser = approver == null ? "" : approver.trim();
        Map<String, Object> starter = row("""
                SELECT user_id, user_name, nick_name, dept_id FROM sys_user WHERE user_id = ?
                """, starterId);

        if (roleOrUser.isBlank()
                || List.of("部门负责人", "直接主管", "负责人").contains(roleOrUser)) {
            Map<String, Object> leader = row("""
                    SELECT u.user_id, u.user_name, u.nick_name
                    FROM sys_user starter
                    JOIN sys_dept d ON d.dept_id = starter.dept_id
                    JOIN sys_user u ON (u.user_name = d.leader OR u.nick_name = d.leader)
                    WHERE starter.user_id = ? AND u.del_flag = '0'
                    ORDER BY u.status, u.user_id LIMIT 1
                    """, starterId);
            if (!leader.isEmpty()) {
                return assignment(leader);
            }
        } else {
            Map<String, Object> user = row("""
                    SELECT user_id, user_name, nick_name FROM sys_user
                    WHERE del_flag = '0'
                      AND (user_name = ? OR phonenumber = ? OR nick_name = ?)
                    ORDER BY status, user_id LIMIT 1
                    """, roleOrUser, roleOrUser, roleOrUser);
            if (!user.isEmpty()) {
                return assignment(user);
            }
            Map<String, Object> roleUser = row("""
                    SELECT u.user_id, u.user_name, u.nick_name
                    FROM sys_user u
                    JOIN sys_user_role ur ON ur.user_id = u.user_id
                    JOIN sys_role r ON r.role_id = ur.role_id
                    WHERE u.del_flag = '0' AND r.status = '0'
                      AND (r.role_name = ? OR r.role_key = ?)
                    ORDER BY u.status, u.user_id LIMIT 1
                    """, roleOrUser, roleOrUser);
            if (!roleUser.isEmpty()) {
                return assignment(roleUser);
            }
        }
        return assignment(starter);
    }

    private Assignment assignment(Map<String, Object> user) {
        return new Assignment(
                number(user.get("user_id"), 1L),
                value(user.get("nick_name"), value(user.get("user_name"), "审批人"))
        );
    }

    private String starterName(Long starterId, String fallback) {
        Map<String, Object> user = row(
                "SELECT nick_name FROM sys_user WHERE user_id = ?",
                starterId
        );
        return value(user.get("nick_name"), fallback);
    }

    private Map<String, Object> form(String formKey) {
        Map<String, Object> form = row("""
                SELECT * FROM oa_form_definition WHERE form_key = ?
                """, formKey);
        if (form.isEmpty()) {
            throw new ApiException(404, "流程表单不存在");
        }
        return form;
    }

    private Map<String, Object> publishedDesign(long menuId) {
        return row("""
                SELECT id, tenant_app_id, menu_id, menu_name, process_data, update_time
                FROM oa_process_design
                WHERE tenant_app_id = 1 AND menu_id = ?
                ORDER BY update_time DESC LIMIT 1
                """, menuId);
    }

    private Map<String, Object> publishedDesign(long menuId, String actionCode) {
        Map<String, Object> design = flowBindingService.activeDesign(1L, menuId, actionCode);
        if (!design.isEmpty()) {
            return design;
        }
        if ("page".equals(actionCode)) {
            return publishedDesign(menuId);
        }
        return Map.of();
    }

    private Map<String, Object> definition(Map<String, Object> instance) {
        String key = value(instance.get("process_key"), "");
        if (key.startsWith(BUSINESS_PREFIX)) {
            BusinessProcessKey businessKey = businessProcessKey(key);
            Map<String, Object> design = flowBindingService.boundDesign(
                    1L,
                    businessKey.moduleId(),
                    businessKey.actionCode()
            );
            if (design.isEmpty() && "page".equals(businessKey.actionCode())) {
                design = publishedDesign(businessKey.moduleId());
            }
            if (design.isEmpty()) {
                throw new ApiException(404, "业务页面流程设计不存在");
            }
            String flowName = value(design.get("menu_name"), "业务审批") + "审批";
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("form_key", key);
            result.put("flow_name", flowName);
            result.put("basic_setting", json(Map.of("flowName", flowName)));
            result.put("form_data", "{}");
            result.put("process_data", design.get("process_data"));
            return result;
        }
        return form(processKey(instance));
    }

    private String flowName(Map<String, Object> definition) {
        return value(
                object(value(definition.get("basic_setting"), "{}")).get("flowName"),
                value(definition.get("flow_name"), "审批流程")
        );
    }

    private Map<String, Object> businessData(Map<String, Object> instance) {
        String key = value(instance.get("process_key"), "");
        if (!key.startsWith(BUSINESS_PREFIX)) {
            return submission(number(instance.get("business_id"), 0L));
        }
        BusinessProcessKey businessKey = businessProcessKey(key);
        if (!"page".equals(businessKey.actionCode())) {
            return actionRequestService.payload(number(instance.get("business_id"), 0L));
        }
        String module = businessModule(value(instance.get("business_type"), ""));
        if (module.isBlank()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> record = row("""
                SELECT payload_json FROM biz_record WHERE module = ? AND id = ?
                """, module, number(instance.get("business_id"), 0L));
        return object(value(record.get("payload_json"), "{}"));
    }

    private String businessModule(String businessType) {
        return "";
    }

    private void updateBusinessState(
            String instanceId,
            String businessStatus,
            String taskId,
            String taskName
    ) {
        Map<String, Object> instance = row(
                "SELECT * FROM oa_process_instance WHERE id = ?",
                instanceId
        );
        if (instance.isEmpty()) {
            return;
        }
        String processKey = value(instance.get("process_key"), "");
        if (processKey.startsWith(BUSINESS_PREFIX)) {
            BusinessProcessKey businessKey = businessProcessKey(processKey);
            if (!"page".equals(businessKey.actionCode())) {
                actionRequestService.updateWorkflowState(
                        instanceId,
                        number(instance.get("business_id"), 0L),
                        businessStatus,
                        taskId,
                        taskName
                );
                return;
            }
        }
        String module = businessModule(value(instance.get("business_type"), ""));
        long businessId = number(instance.get("business_id"), 0L);
        if (module.isBlank() || businessId <= 0L) {
            return;
        }
        Map<String, Object> record = row("""
                SELECT payload_json FROM biz_record WHERE module = ? AND id = ?
                """, module, businessId);
        if (record.isEmpty()) {
            return;
        }
        Map<String, Object> payload = object(value(record.get("payload_json"), "{}"));
        payload.put("status", businessStatus);
        payload.put("workflowInstanceId", instanceId);
        payload.put("workflowTaskId", taskId == null ? "" : taskId);
        payload.put("workflowTaskName", taskName == null ? "" : taskName);
        String storedStatus = switch (businessStatus) {
            case "审批通过" -> "approved";
            case "审批未通过" -> "rejected";
            default -> "running";
        };
        jdbc.update("""
                UPDATE biz_record SET payload_json = ?, status = ?, update_time = CURRENT_TIMESTAMP
                WHERE module = ? AND id = ?
                """, json(payload), storedStatus, module, businessId);
    }

    public boolean isManagedTask(String taskId, String instanceId) {
        if (taskId == null || taskId.isBlank() || instanceId == null || instanceId.isBlank()) {
            return false;
        }
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM oa_process_task WHERE id = ? AND instance_id = ?
                """, Long.class, taskId, instanceId);
        return count != null && count > 0L;
    }

    private Map<String, Object> submission(long dataId) {
        Map<String, Object> row = row("""
                SELECT data_json FROM oa_form_submission WHERE id = ?
                """, dataId);
        return object(value(row.get("data_json"), "{}"));
    }

    private void saveComment(String taskId, String comment, Long operatorId, Timestamp now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        payload.put("comment", comment);
        jdbc.update("""
                INSERT INTO biz_record (
                    tenant_id, module, business_key, payload_json, status, create_by, create_time
                ) VALUES (1, 'lowcode:task-comment', ?, ?, 'done', ?, ?)
                """, taskId, json(payload), String.valueOf(operatorId), now);
    }

    private String comment(String taskId) {
        Map<String, Object> row = row("""
                SELECT payload_json FROM biz_record
                WHERE module = 'lowcode:task-comment' AND business_key = ?
                ORDER BY id DESC LIMIT 1
                """, taskId);
        return value(object(value(row.get("payload_json"), "{}")).get("comment"), "同意");
    }

    private int flowStatus(String status) {
        return switch (status) {
            case "approved" -> 0;
            case "running" -> 1;
            default -> 2;
        };
    }

    private int formState(String status) {
        return switch (status) {
            case "approved" -> 0;
            case "running" -> 2;
            case "rejected" -> 3;
            default -> 4;
        };
    }

    private List<Map<String, Object>> fields(Map<String, Object> formData) {
        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, Object>> fields = new ArrayList<>();
        collectFields(formData.get("fields"), seen, fields);
        return fields;
    }

    private void collectFields(
            Object raw,
            Set<String> seen,
            List<Map<String, Object>> fields
    ) {
        if (!(raw instanceof List<?> items)) {
            return;
        }
        for (Object item : items) {
            if (item instanceof List<?> nested) {
                collectFields(nested, seen, fields);
            } else if (item instanceof Map<?, ?> field) {
                String model = value(field.get("vModel"), value(field.get("prop"), ""));
                String label = value(field.get("label"), model);
                if (!model.isBlank() && seen.add(model)) {
                    Map<String, Object> fieldInfo = new LinkedHashMap<>();
                    fieldInfo.put("label", label);
                    fieldInfo.put("vModel", model);
                    fieldInfo.put("formId", field.get("formId"));
                    fields.add(fieldInfo);
                }
            }
        }
    }

    private Map<String, Object> historyRow(
            String instanceId,
            String activityId,
            String activityName,
            String activityType,
            long assignee,
            String assigneeName,
            String comment,
            Timestamp startTime,
            Timestamp endTime
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", instanceId + "-" + activityId + "-" + startTime.getTime());
        row.put("processInstanceId", instanceId);
        row.put("activityId", activityId);
        row.put("activityName", activityName);
        row.put("activityType", activityType);
        row.put("assignee", String.valueOf(assignee));
        row.put("assigneeName", assigneeName);
        row.put("comment", comment);
        row.put("startTime", startTime);
        row.put("endTime", endTime);
        row.put("durationInMillis", Math.max(0L, endTime.getTime() - startTime.getTime()));
        return row;
    }

    private Map<String, Object> element(
            String id,
            String name,
            String type,
            boolean current
    ) {
        Map<String, Object> element = new LinkedHashMap<>();
        element.put("id", id);
        element.put("name", name);
        element.put("type", type);
        element.put("startId", null);
        element.put("endId", null);
        element.put("pass", true);
        element.put("current", current ? true : null);
        return element;
    }

    private List<Map<String, Object>> nodes(Map<String, Object> processData) {
        if (!(processData.get("nodes") instanceof List<?> rawNodes)) {
            return List.of();
        }
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Object item : rawNodes) {
            if (item instanceof Map<?, ?> node) {
                Map<String, Object> copy = new LinkedHashMap<>();
                node.forEach((key, value) -> copy.put(String.valueOf(key), value));
                nodes.add(copy);
            }
        }
        return nodes;
    }

    private Map<String, Object> firstApproval(
            List<Map<String, Object>> nodes,
            String afterNodeId
    ) {
        boolean afterFound = afterNodeId == null;
        for (Map<String, Object> node : nodes) {
            String id = value(node.get("id"), "");
            if (!afterFound) {
                if (id.equals(afterNodeId)) {
                    afterFound = true;
                }
                continue;
            }
            if ("approval".equals(value(node.get("type"), ""))) {
                return node;
            }
        }
        return null;
    }

    private String processKey(Map<String, Object> instance) {
        String key = value(instance.get("process_key"), "");
        return key.startsWith(PROCESS_PREFIX) ? key.substring(PROCESS_PREFIX.length()) : "";
    }

    private String businessProcessKey(long moduleId, String actionCode) {
        return "page".equals(actionCode)
                ? BUSINESS_PREFIX + moduleId
                : BUSINESS_PREFIX + moduleId + ":" + actionCode;
    }

    private BusinessProcessKey businessProcessKey(String processKey) {
        String value = processKey.startsWith(BUSINESS_PREFIX)
                ? processKey.substring(BUSINESS_PREFIX.length())
                : processKey;
        String[] parts = value.split(":", 2);
        long moduleId = number(parts.length > 0 ? parts[0] : "", 0L);
        String actionCode = parts.length > 1 && !parts[1].isBlank() ? parts[1] : "page";
        return new BusinessProcessKey(moduleId, actionCode);
    }

    private String taskId(String instanceId, String nodeId) {
        return "task" + TASK_SEPARATOR + instanceId + TASK_SEPARATOR
                + nodeId + TASK_SEPARATOR
                + UUID.randomUUID().toString().replace("-", "");
    }

    private String nodeIdFromTask(String taskId) {
        String[] parts = taskId.split(TASK_SEPARATOR);
        return parts.length >= 3 ? parts[2] : "";
    }

    private Map<String, Object> row(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
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
            throw new ApiException(500, "流程数据序列化失败");
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

    private record Assignment(long id, String name) {
    }

    private record BusinessProcessKey(long moduleId, String actionCode) {
    }
}
