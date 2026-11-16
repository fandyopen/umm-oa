package com.seal.oa.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.common.JdbcMaps;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Records actual actors without overwriting the task's original assignment. */
@Service
public class ApprovalDecisionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ApprovalDecisionService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void record(Map<String, Object> instance, Map<String, Object> task, Long operator,
                       boolean pass, String comment) {
        Map<String, Object> actor = member(operator);
        Map<String, Object> assignee = member(task.get("assignee_id"));
        jdbc.update("""
                INSERT INTO oa_approval_decision(task_id,tenant_id,instance_id,assignee_id,assignee_name,
                    assignee_dept_id,assignee_dept_name,operator_id,operator_name,operator_dept_name,
                    decision,comment,delegated)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, task.get("id"), instance.get("tenant_id"), instance.get("id"), task.get("assignee_id"),
                task.get("assignee_name"), assignee.get("deptId"), assignee.get("deptName"), operator,
                actor.get("nickName"), actor.get("deptName"), pass ? "approved" : "rejected", comment,
                String.valueOf(operator).equals(String.valueOf(task.get("assignee_id"))) ? 0 : 1);
    }

    public Map<String, Object> find(String taskId) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM oa_approval_decision WHERE task_id=?", taskId));
        if (!rows.isEmpty()) return rows.get(0);
        // Old comments already record the actual actor. Do not invent an actor for older tasks without evidence.
        var comments = jdbc.queryForList("""
                SELECT payload_json,create_by,create_time FROM biz_record
                WHERE module='lowcode:task-comment' AND business_key=? ORDER BY id DESC LIMIT 1
                """, taskId);
        if (comments.isEmpty()) return Map.of();
        var comment = comments.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            long operator = Long.parseLong(String.valueOf(comment.get("create_by")));
            result.put("operatorId", operator);
            result.put("operatorName", member(operator).get("nickName"));
            result.put("comment", mapper.readTree(String.valueOf(comment.get("payload_json"))).path("comment").asText());
            result.put("completeTime", comment.get("create_time"));
        } catch (Exception ignored) { /* Historical data may not contain an identifiable actor. */ }
        return result;
    }

    private Map<String, Object> member(Object userId) {
        var rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT u.nick_name,u.dept_id,d.dept_name FROM sys_user u
                LEFT JOIN sys_dept d ON d.dept_id=u.dept_id WHERE u.user_id=?
                """, userId));
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }
}
