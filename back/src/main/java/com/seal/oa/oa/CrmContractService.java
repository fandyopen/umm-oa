package com.seal.oa.oa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.JdbcMaps;
import com.seal.oa.system.DataScopeService;
import com.seal.oa.system.SystemService;
import com.seal.oa.lowcode.ModuleFlowBindingService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CrmContractService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final DataScopeService scopes;
    private final SystemService system;
    private final ModuleFlowBindingService bindings;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String SELECT = """
            SELECT c.*, m.name AS customer_name, m.owner_user_id, m.owner_user_name,
                   m.public_pool, m.status AS customer_status, u.dept_id AS owner_dept_id,
                   approver.nick_name AS approval_user_name
            FROM oa_crm_contract c JOIN oa_merchant m ON m.id = c.customer_id
            LEFT JOIN sys_user u ON u.user_id = m.owner_user_id
            LEFT JOIN sys_user approver ON approver.user_id = c.approval_user_id
            """;

    public CrmContractService(JdbcTemplate jdbc, ObjectMapper mapper, DataScopeService scopes, SystemService system,
                              ModuleFlowBindingService bindings) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.scopes = scopes;
        this.system = system;
        this.bindings = bindings;
    }

    public List<Map<String, Object>> list(Map<String, String> query) {
        boolean approvalView = "approvals".equals(query.get("view"));
        return rows().stream().filter(row -> visible(row, approvalView))
                .filter(row -> blank(query.get("customerId")) || text(row.get("customerId")).equals(query.get("customerId")))
                .filter(row -> blank(query.get("status")) || text(row.get("status")).equals(query.get("status")))
                .filter(row -> blank(query.get("keyword")) || (text(row.get("name")) + text(row.get("code"))
                        + text(row.get("customerName"))).toLowerCase().contains(query.get("keyword").trim().toLowerCase()))
                .map(this::decorate).toList();
    }

    public Map<String, Object> detail(long id) {
        Map<String, Object> row = require(id, false);
        decorate(row);
        row.put("versions", versions(id));
        row.put("workflow", workflow(id));
        row.put("signingWorkflow", signingWorkflow(id));
        row.put("signApprovalEnabled", !bindings.activeDesign(1L, 341201L, "sign").isEmpty());
        row.put("reminders", reminders(true).stream().filter(item -> number(item.get("contractId")) == id).toList());
        row.put("receipts", JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM oa_crm_contract_receipt WHERE contract_id = ? ORDER BY receipt_date DESC, id DESC", id)));
        row.put("reminderLogs", JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM oa_crm_contract_reminder_log WHERE contract_id = ? ORDER BY id DESC", id)));
        return row;
    }

    public List<Map<String, Object>> versions(long id) {
        require(id, false);
        List<Map<String, Object>> result = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT v.*, submitter.nick_name AS submit_user_name, approver.nick_name AS approval_user_name
                FROM oa_crm_contract_version v
                LEFT JOIN sys_user submitter ON submitter.user_id = v.submit_user_id
                LEFT JOIN sys_user approver ON approver.user_id = v.approval_user_id
                WHERE v.contract_id = ? ORDER BY v.version_no DESC
                """, id));
        result.forEach(row -> {
            Map<String, Object> snapshot = readMap(row.remove("snapshotJson"));
            row.put("snapshot", snapshot);
            row.put("workflow", workflowInfo(snapshot, true));
            row.put("signingWorkflow", workflowInfo(snapshot, true, "signWorkflowRequestId", "sign"));
            row.put("signingHistory", signingRequestIds(snapshot).stream().map(requestId ->
                    workflowInfo(Map.of("signWorkflowRequestId", requestId), true, "signWorkflowRequestId", "sign")).toList());
        });
        return result;
    }

    public List<Map<String, Object>> approvers() {
        return JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT user_id, nick_name FROM sys_user
                WHERE status = '0' AND del_flag = '0' ORDER BY user_id
                """)).stream().filter(row -> number(row.get("userId")) != AuthContext.userId())
                .filter(row -> system.hasModuleAction(number(row.get("userId")), 341202L, "approve"))
                .toList();
    }

    public List<Map<String, Object>> customerOptions(String name) {
        return JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT m.id, m.name, m.owner_user_id, m.owner_user_name, u.dept_id AS owner_dept_id
                FROM oa_merchant m LEFT JOIN sys_user u ON u.user_id = m.owner_user_id
                WHERE m.status = '0' AND m.public_pool = '0' AND m.owner_user_id IS NOT NULL
                AND LOWER(m.name) LIKE ? ORDER BY m.name
                """, "%" + text(name).toLowerCase() + "%")).stream().filter(this::customerVisible).toList();
    }

    public Map<String, Object> summary(List<Map<String, Object>> rows) {
        BigDecimal amount = rows.stream().map(row -> money(row.get("effectiveAmount"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal received = rows.stream().map(row -> money(row.get("receivedAmount"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        return Map.of("effectiveAmount", amount, "receivedAmount", received, "outstandingAmount", amount.subtract(received));
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        Map<String, Object> customer = customer(number(body.get("customerId")));
        Map<String, Object> fields = validate(body);
        long parentId = number(body.get("parentContractId"));
        if (parentId > 0) {
            Map<String, Object> parent = require(parentId, false);
            if (number(parent.get("customerId")) != number(customer.get("id")) || number(parent.get("effectiveVersion")) == 0) {
                throw new ApiException(400, "续约必须关联同一客户的已生效合同");
            }
        }
        String code = text(body.get("code")).trim();
        if (blank(code)) code = "HT-" + LocalDate.now(ZONE).toString().replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        if (code.length() > 80 || jdbc.queryForObject("SELECT COUNT(*) FROM oa_crm_contract WHERE code = ?", Long.class, code) > 0) {
            throw new ApiException(409, "合同编号已存在或长度超过80字");
        }
        long opportunityId = number(body.get("opportunityId"));
        if (opportunityId > 0) validateOpportunity(opportunityId, number(customer.get("id")));
        final String finalCode = code;
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO oa_crm_contract (customer_id, opportunity_id, parent_contract_id, code, name,
                    service_type, amount, start_date, end_date, terms_json, attachment, remark, create_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[] {"id"});
            Object[] args = {customer.get("id"), opportunityId > 0 ? opportunityId : null, parentId > 0 ? parentId : null,
                    finalCode, fields.get("name"), fields.get("serviceType"), fields.get("amount"),
                    fields.get("startDate"), fields.get("endDate"), json(fields.get("feeTerms")),
                    fields.get("attachment"), fields.get("remark"), AuthContext.username()};
            for (int index = 0; index < args.length; index++) statement.setObject(index + 1, args[index]);
            return statement;
        }, keys);
        return detail(keys.getKey().longValue());
    }

    @Transactional
    public Map<String, Object> update(long id, Map<String, Object> body) {
        Map<String, Object> row = require(id, true);
        requireCustomerAccess(row);
        expectRevision(row, body);
        expectStatus(row, "draft", "rejected");
        if (number(body.get("customerId")) != number(row.get("customerId"))) throw new ApiException(400, "合同创建后不能更换客户");
        Map<String, Object> fields = validate(body);
        protectPaidTerms(id, fields);
        jdbc.update("""
                UPDATE oa_crm_contract SET name = ?, service_type = ?, amount = ?, start_date = ?, end_date = ?,
                terms_json = ?, attachment = ?, remark = ?, revision = revision + 1, update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, fields.get("name"), fields.get("serviceType"), fields.get("amount"), fields.get("startDate"),
                fields.get("endDate"), json(fields.get("feeTerms")), fields.get("attachment"), fields.get("remark"), id);
        return detail(id);
    }

    @Transactional
    public void delete(long id) {
        Map<String, Object> row = require(id, true);
        requireCustomerAccess(row);
        expectStatus(row, "draft");
        Long versions = jdbc.queryForObject("SELECT COUNT(*) FROM oa_crm_contract_version WHERE contract_id = ?", Long.class, id);
        if (versions > 0) throw new ApiException(409, "有提交版本的合同必须保留历史，不能删除");
        jdbc.update("DELETE FROM oa_crm_contract WHERE id = ?", id);
    }

    public void validateWorkflowSubmission(long id, Map<String, Object> body) {
        submission(id, body);
    }

    private Map<String, Object> submission(long id, Map<String, Object> body) {
        Map<String, Object> row = require(id, true);
        requireCustomerAccess(row);
        customer(number(row.get("customerId")));
        expectRevision(row, body);
        expectStatus(row, "draft", "rejected");
        List<Map<String, Object>> terms = readTerms(row.get("termsJson"));
        BigDecimal total = terms.stream().map(term -> money(term.get("amount"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (terms.isEmpty() || total.compareTo(money(row.get("amount"))) != 0) throw new ApiException(400, "提交前须填写收费条款，且收费合计必须等于合同金额");
        protectPaidTerms(id, Map.of("feeTerms", terms));
        return row;
    }

    @Transactional
    public Map<String, Object> prepareWorkflowSubmission(long id, Map<String, Object> body, long requestId) {
        if (requestId <= 0L) throw new ApiException(400, "合同审批必须通过已发布的审批流程发起");
        Map<String, Object> row = submission(id, body);
        List<Map<String, Object>> terms = readTerms(row.get("termsJson"));
        int version = jdbc.queryForObject("SELECT COALESCE(MAX(version_no), 0) + 1 FROM oa_crm_contract_version WHERE contract_id = ?", Integer.class, id);
        Map<String, Object> snapshot = new LinkedHashMap<>(row);
        snapshot.remove("termsJson");
        snapshot.put("feeTerms", terms);
        snapshot.put("versionNo", version);
        snapshot.put("contractId", id);
        snapshot.put("workflowRequestId", requestId);
        jdbc.update("""
                INSERT INTO oa_crm_contract_version (contract_id, version_no, snapshot_json, status, submit_user_id, approval_user_id)
                VALUES (?, ?, ?, 'pending', ?, 0)
                """, id, version, json(snapshot), AuthContext.userId());
        jdbc.update("""
                UPDATE oa_crm_contract SET status = 'pending', version_no = ?, approval_user_id = NULL,
                submit_user_id = ?, approval_comment = NULL, revision = revision + 1, update_time = CURRENT_TIMESTAMP WHERE id = ?
                """, version, AuthContext.userId(), id);
        return snapshot;
    }

    public Map<String, Object> workflow(long id) {
        List<String> snapshots = jdbc.queryForList("""
                SELECT v.snapshot_json FROM oa_crm_contract_version v
                JOIN oa_crm_contract c ON c.id = v.contract_id AND c.version_no = v.version_no
                WHERE c.id = ?
                """, String.class, id);
        return snapshots.isEmpty() ? Map.of() : workflowInfo(readMap(snapshots.get(0)), true);
    }

    private Map<String, Object> workflowInfo(Map<String, Object> snapshot, boolean includeHistory) {
        return workflowInfo(snapshot, includeHistory, "workflowRequestId", "submit");
    }

    public Map<String, Object> signingWorkflow(long id) {
        return signingWorkflow(id, true);
    }

    private Map<String, Object> signingWorkflow(long id, boolean includeHistory) {
        List<String> snapshots = jdbc.queryForList("SELECT v.snapshot_json FROM oa_crm_contract_version v JOIN oa_crm_contract c ON c.id = v.contract_id AND c.version_no = v.version_no WHERE c.id = ?", String.class, id);
        return snapshots.isEmpty() ? Map.of() : workflowInfo(readMap(snapshots.get(0)), includeHistory, "signWorkflowRequestId", "sign");
    }

    private List<Long> signingRequestIds(Map<String, Object> snapshot) {
        List<Long> requests = new ArrayList<>();
        if (snapshot.get("signWorkflowRequestIds") instanceof List<?> list) list.forEach(value -> requests.add(number(value)));
        long latest = number(snapshot.get("signWorkflowRequestId"));
        if (latest > 0 && !requests.contains(latest)) requests.add(latest);
        return requests;
    }

    private Map<String, Object> workflowInfo(Map<String, Object> snapshot, boolean includeHistory,
                                             String requestKey, String actionCode) {
        long requestId = number(snapshot.get(requestKey));
        if (requestId <= 0L) return Map.of();
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT id AS request_id, process_instance_id AS instance_id,
                       process_task_id AS task_id, process_task_name AS task_name, status
                FROM oa_workflow_request WHERE id = ? AND module_id = 341201 AND action_code = ?
                """, requestId, actionCode));
        if (rows.isEmpty()) return Map.of();
        Map<String, Object> result = rows.get(0);
        if (includeHistory) {
            List<Map<String, Object>> tasks = JdbcMaps.camelCase(jdbc.queryForList("""
                    SELECT id AS task_id, task_name, assignee_id, assignee_name, status, create_time, complete_time
                    FROM oa_process_task WHERE instance_id = ? ORDER BY create_time, id
                    """, result.get("instanceId")));
            tasks.forEach(task -> task.put("comment", workflowComment(String.valueOf(task.get("taskId")))));
            result.put("history", tasks);
        }
        return result;
    }

    private String workflowComment(String taskId) {
        List<String> comments = jdbc.queryForList("""
                SELECT payload_json FROM biz_record WHERE module = 'lowcode:task-comment' AND business_key = ?
                ORDER BY id DESC LIMIT 1
                """, String.class, taskId);
        return comments.isEmpty() ? "" : String.valueOf(readMap(comments.get(0)).getOrDefault("comment", ""));
    }

    public void validateWorkflowRequest(long requestId, Map<String, Object> payload) {
        workflowContract(requestId, payload);
    }

    private Map<String, Object> workflowContract(long requestId, Map<String, Object> payload) {
        long id = number(payload.get("contractId"));
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(SELECT + " WHERE c.id = ? FOR UPDATE", id));
        if (rows.isEmpty()) throw new ApiException(404, "合同不存在");
        Map<String, Object> row = rows.get(0);
        expectStatus(row, "pending");
        List<String> snapshots = jdbc.queryForList("SELECT snapshot_json FROM oa_crm_contract_version WHERE contract_id = ? AND version_no = ?",
                String.class, id, row.get("versionNo"));
        if (requestId <= 0L || snapshots.isEmpty()
                || number(payload.get("versionNo")) != number(row.get("versionNo"))
                || number(readMap(snapshots.get(0)).get("workflowRequestId")) != requestId) {
            throw new ApiException(409, "审批申请与当前合同版本不一致，请刷新后重试");
        }
        return row;
    }

    @Transactional
    public void syncWorkflowState(long requestId, Map<String, Object> payload, String instanceId,
                                  String businessStatus, String taskId, String taskName) {
        Map<String, Object> row = workflowContract(requestId, payload);
        long id = number(row.get("id"));
        List<Map<String, Object>> tasks = jdbc.queryForList("SELECT * FROM oa_process_task WHERE id = ? AND instance_id = ?", taskId, instanceId);
        if (tasks.isEmpty()) throw new ApiException(409, "合同审批任务不存在");
        long assignee = number(tasks.get(0).get("assignee_id"));
        Long activeApprover = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE user_id = ? AND status = '0' AND del_flag = '0'", Long.class, assignee);
        if (activeApprover == null || activeApprover == 0L) throw new ApiException(400, "流程节点审批人不是有效在职成员，请检查流程设计");
        if (assignee == number(row.get("submitUserId"))) throw new ApiException(400, "合同审批节点不能由提交人本人审批，请检查流程设计中的审批人配置");
        if (!system.hasModuleAction(assignee, 341202L, "approve")) throw new ApiException(400, "流程节点审批人没有合同审批权限，请在角色权限中授权");
        if ("等待审批".equals(businessStatus)) {
            jdbc.update("UPDATE oa_crm_contract SET revision = revision + ?, approval_user_id = ?, update_time = CURRENT_TIMESTAMP WHERE id = ?",
                    number(row.get("approvalUserId")) > 0L ? 1 : 0, assignee, id);
            jdbc.update("UPDATE oa_crm_contract_version SET approval_user_id = ? WHERE contract_id = ? AND version_no = ?", assignee, id, row.get("versionNo"));
            return;
        }
        String status = "审批通过".equals(businessStatus) ? "approved" : "rejected";
        String comment = limited(workflowComment(taskId), 2000, "审批意见");
        jdbc.update("UPDATE oa_crm_contract SET status = ?, approval_user_id = ?, approval_comment = ?, revision = revision + 1, update_time = CURRENT_TIMESTAMP WHERE id = ?",
                status, assignee, comment, id);
        jdbc.update("UPDATE oa_crm_contract_version SET status = ?, approval_user_id = ?, approval_comment = ?, approval_time = CURRENT_TIMESTAMP WHERE contract_id = ? AND version_no = ?",
                status, assignee, comment, id, row.get("versionNo"));
    }

    public void validateWorkflowDecision(long requestId, Map<String, Object> payload, String instanceId,
                                         String taskId, Long operatorId,
                                         boolean pass, String comment) {
        Map<String, Object> row = workflowContract(requestId, payload);
        List<Map<String, Object>> requests = jdbc.queryForList("SELECT * FROM oa_workflow_request WHERE id = ? FOR UPDATE", requestId);
        if (requests.isEmpty() || !"running".equals(requests.get(0).get("status"))
                || !instanceId.equals(requests.get(0).get("process_instance_id"))
                || !taskId.equals(requests.get(0).get("process_task_id"))) {
            throw new ApiException(409, "当前审批节点已变更，请刷新待办后重试");
        }
        if (operatorId == null || operatorId != number(row.get("approvalUserId"))
                || operatorId == number(row.get("submitUserId"))
                || !system.hasModuleAction(operatorId, 341202L, "approve")) {
            throw new ApiException(403, "仅当前节点的合同审批人可以办理审批，提交人不能审批自己的合同");
        }
        limited(comment, 2000, "审批意见");
        if (!pass && blank(comment)) throw new ApiException(400, "退回时请填写原因");
    }

    public Map<String, Object> approvalTask(long id, Map<String, Object> body) {
        Map<String, Object> row = require(id, true);
        expectRevision(row, body);
        expectStatus(row, "pending");
        return workflow(id);
    }

    @Transactional
    public Map<String, Object> approve(long id, Map<String, Object> body) {
        Map<String, Object> row = require(id, true);
        if (!workflow(id).isEmpty()) throw new ApiException(409, "该合同已接入审批流程，请办理当前流程任务");
        expectRevision(row, body);
        expectStatus(row, "pending");
        if (number(row.get("approvalUserId")) != AuthContext.userId() || number(row.get("submitUserId")) == AuthContext.userId()) {
            throw new ApiException(403, "仅指定审批人可以审批，提交人不能审批自己的合同");
        }
        String decision = text(body.get("decision"));
        String comment = limited(body.get("comment"), 2000, "审批意见");
        if (!Set.of("approved", "rejected").contains(decision)) throw new ApiException(400, "审批结果无效");
        if ("rejected".equals(decision) && blank(comment)) throw new ApiException(400, "退回时请填写原因");
        jdbc.update("UPDATE oa_crm_contract SET status = ?, approval_comment = ?, revision = revision + 1, update_time = CURRENT_TIMESTAMP WHERE id = ?", decision, comment, id);
        jdbc.update("UPDATE oa_crm_contract_version SET status = ?, approval_comment = ?, approval_time = CURRENT_TIMESTAMP WHERE contract_id = ? AND version_no = ?", decision, comment, id, row.get("versionNo"));
        jdbc.update("UPDATE oa_todo SET status = 'done' WHERE business_type = 'crmContractApproval' AND task_id = ?", approvalTask(id, number(row.get("versionNo"))));
        return detail(id);
    }

    @Transactional
    public Map<String, Object> sign(long id, Map<String, Object> body) {
        if (!bindings.activeDesign(1L, 341201L, "sign").isEmpty()) {
            throw new ApiException(409, "签署已启用审批，请通过签署申请完成审批后生效");
        }
        Map<String, Object> row = validateSigningSubmission(id, body);
        completeSigning(row, body);
        return detail(id);
    }

    public Map<String, Object> validateSigningSubmission(long id, Map<String, Object> body) {
        Map<String, Object> row = require(id, true);
        requireCustomerAccess(row);
        expectRevision(row, body);
        expectStatus(row, "approved");
        requireNoSigningApproval(id);
        LocalDate signed = date(body.get("signedDate"), "签署日期");
        if (signed.isAfter(LocalDate.now(ZONE))) throw new ApiException(400, "签署日期不能晚于今天");
        return row;
    }

    @Transactional
    public Map<String, Object> prepareSigningSubmission(long id, Map<String, Object> body, long requestId) {
        Map<String, Object> row = validateSigningSubmission(id, body);
        Map<String, Object> snapshot = readMap(jdbc.queryForObject("SELECT snapshot_json FROM oa_crm_contract_version WHERE contract_id = ? AND version_no = ?", String.class, id, row.get("versionNo")));
        List<Long> requests = signingRequestIds(snapshot);
        requests.add(requestId);
        snapshot.put("signWorkflowRequestIds", requests);
        snapshot.put("signWorkflowRequestId", requestId);
        jdbc.update("UPDATE oa_crm_contract_version SET snapshot_json = ? WHERE contract_id = ? AND version_no = ?", json(snapshot), id, row.get("versionNo"));
        jdbc.update("UPDATE oa_crm_contract SET revision = revision + 1, update_time = CURRENT_TIMESTAMP WHERE id = ?", id);
        Map<String, Object> frozen = new LinkedHashMap<>(row);
        frozen.remove("termsJson");
        frozen.put("feeTerms", readTerms(row.get("termsJson")));
        frozen.put("contractId", id);
        frozen.put("revision", number(row.get("revision")) + 1);
        frozen.put("signedDate", text(body.get("signedDate")));
        frozen.put("signSubmitUserId", AuthContext.userId());
        frozen.put("signWorkflowRequestId", requestId);
        return frozen;
    }

    public Map<String, Object> validateSigningRequest(long requestId, Map<String, Object> payload) {
        long id = number(payload.get("contractId"));
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList(SELECT + " WHERE c.id = ? FOR UPDATE", id));
        if (rows.isEmpty()) throw new ApiException(404, "合同不存在");
        Map<String, Object> row = rows.get(0);
        expectStatus(row, "approved");
        expectRevision(row, payload);
        Map<String, Object> workflow = signingWorkflow(id);
        if (requestId <= 0 || number(workflow.get("requestId")) != requestId
                || number(payload.get("versionNo")) != number(row.get("versionNo"))) {
            throw new ApiException(409, "签署申请与当前合同版本不一致，请刷新后重试");
        }
        return row;
    }

    public void validateSigningDecision(long requestId, Map<String, Object> payload, String instanceId,
                                         String taskId, Long operatorId, boolean pass, String comment) {
        validateSigningRequest(requestId, payload);
        Map<String, Object> workflow = signingWorkflow(number(payload.get("contractId")));
        if (!"running".equals(workflow.get("status")) || !instanceId.equals(workflow.get("instanceId"))
                || !taskId.equals(workflow.get("taskId"))) throw new ApiException(409, "签署审批节点已变更，请刷新后重试");
        validateSigningApprover(payload, instanceId, taskId, operatorId);
        limited(comment, 2000, "审批意见");
        if (!pass && blank(comment)) throw new ApiException(400, "退回时请填写原因");
    }

    private void validateSigningApprover(Map<String, Object> payload, String instanceId, String taskId, Long operatorId) {
        List<Long> users = jdbc.queryForList("SELECT t.assignee_id FROM oa_process_task t JOIN sys_user u ON u.user_id = t.assignee_id WHERE t.id = ? AND t.instance_id = ? AND u.status = '0' AND u.del_flag = '0'", Long.class, taskId, instanceId);
        if (users.isEmpty() || users.get(0) == number(payload.get("signSubmitUserId"))
                || !system.hasModuleAction(users.get(0), 341202L, "approve")) {
            throw new ApiException(400, "签署审批人须为具有合同审批权限的在职成员，且不能是签署申请人");
        }
        if (operatorId != null && !operatorId.equals(users.get(0))) throw new ApiException(403, "仅当前节点的签署审批人可以办理");
    }

    @Transactional
    public void syncSigningWorkflowState(long requestId, Map<String, Object> payload, String instanceId,
                                           String businessStatus, String taskId) {
        Map<String, Object> row = validateSigningRequest(requestId, payload);
        validateSigningApprover(payload, instanceId, taskId, null);
        if ("审批未通过".equals(businessStatus)) {
            jdbc.update("UPDATE oa_crm_contract SET revision = revision + 1, update_time = CURRENT_TIMESTAMP WHERE id = ?", row.get("id"));
        }
    }

    @Transactional
    public Object executeApprovedSigning(long requestId, Map<String, Object> payload) {
        Map<String, Object> row = validateSigningRequest(requestId, payload);
        completeSigning(row, payload);
        return Map.of("contractId", row.get("id"), "versionNo", row.get("versionNo"), "status", "active");
    }

    private void requireNoSigningApproval(long id) {
        if ("running".equals(signingWorkflow(id, false).get("status"))) throw new ApiException(409, "签署正在审批，请先完成当前申请");
    }

    private void completeSigning(Map<String, Object> row, Map<String, Object> body) {
        long id = number(row.get("id"));
        LocalDate signed = date(body.get("signedDate"), "签署日期");
        if (signed.isAfter(LocalDate.now(ZONE))) throw new ApiException(400, "签署日期不能晚于今天");
        protectPaidTerms(id, Map.of("feeTerms", readTerms(row.get("termsJson"))));
        jdbc.update("UPDATE oa_crm_contract SET status = 'active', effective_version = version_no, revision = revision + 1, update_time = CURRENT_TIMESTAMP WHERE id = ?", id);
        jdbc.update("UPDATE oa_crm_contract_version SET status = 'active', signed_date = ? WHERE contract_id = ? AND version_no = ?", Date.valueOf(signed), id, row.get("versionNo"));
        syncCustomer(number(row.get("customerId")));
        closeResolvedReminders(id);
        if (number(row.get("parentContractId")) > 0) closeResolvedReminders(number(row.get("parentContractId")));
    }

    @Transactional
    public Map<String, Object> revision(long id, Map<String, Object> body) {
        Map<String, Object> row = require(id, true);
        requireNoSigningApproval(id);
        requireCustomerAccess(row);
        expectRevision(row, body);
        expectStatus(row, "active", "approved");
        jdbc.update("UPDATE oa_crm_contract SET status = 'draft', version_no = version_no + 1, revision = revision + 1, update_time = CURRENT_TIMESTAMP WHERE id = ?", id);
        return detail(id);
    }

    @Transactional
    public Map<String, Object> finish(long id, Map<String, Object> body) {
        Map<String, Object> row = require(id, true);
        requireCustomerAccess(row);
        expectRevision(row, body);
        expectStatus(row, "active");
        if (feeRows(row).stream().anyMatch(term -> money(term.get("outstandingAmount")).signum() > 0)) throw new ApiException(409, "仍有未结清收费，请先完成回款");
        jdbc.update("UPDATE oa_crm_contract SET status = 'completed', revision = revision + 1, update_time = CURRENT_TIMESTAMP WHERE id = ?", id);
        syncCustomer(number(row.get("customerId")));
        closeResolvedReminders(id);
        return detail(id);
    }

    @Transactional
    public void receipt(long id, Map<String, Object> body) {
        Map<String, Object> row = require(id, true);
        requireCustomerAccess(row);
        if (number(row.get("effectiveVersion")) == 0 || "completed".equals(row.get("status"))) throw new ApiException(409, "只能为履约中的生效合同登记回款");
        String key = text(body.get("termKey"));
        Map<String, Object> term = feeRows(row).stream().filter(item -> key.equals(item.get("key"))).findFirst()
                .orElseThrow(() -> new ApiException(400, "请关联生效版本中的收费条款"));
        BigDecimal amount = money(body.get("amount"));
        if (amount.signum() <= 0 || amount.compareTo(money(term.get("outstandingAmount"))) > 0) throw new ApiException(400, "回款须大于0且不能超过该条款未收金额");
        LocalDate day = date(body.get("receiptDate"), "回款日期");
        if (day.isAfter(LocalDate.now(ZONE))) throw new ApiException(400, "回款日期不能晚于今天");
        jdbc.update("INSERT INTO oa_crm_contract_receipt (contract_id, term_key, amount, receipt_date, remark, create_by) VALUES (?, ?, ?, ?, ?, ?)",
                id, key, amount, Date.valueOf(day), limited(body.get("remark"), 1000, "回款说明"), AuthContext.username());
        syncCustomer(number(row.get("customerId")));
        if (amount.compareTo(money(term.get("outstandingAmount"))) == 0) {
            jdbc.update("UPDATE oa_todo SET status = 'done' WHERE business_type = 'crmRisk' AND task_id = ?", "crm-contract-" + id + "-fee-" + key);
        }
    }

    private void closeResolvedReminders(long contractId) {
        Set<String> active = new HashSet<>();
        reminders(false).stream().filter(row -> number(row.get("contractId")) == contractId)
                .forEach(row -> active.add(text(row.get("taskId"))));
        for (String task : jdbc.queryForList("SELECT task_id FROM oa_todo WHERE business_type = 'crmRisk' AND task_id LIKE ? AND status = 'todo'",
                String.class, "crm-contract-" + contractId + "-%")) {
            if (!active.contains(task)) jdbc.update("UPDATE oa_todo SET status = 'done' WHERE business_type = 'crmRisk' AND task_id = ?", task);
        }
    }

    public List<Map<String, Object>> fees(Map<String, String> query) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : list(query)) {
            if (number(row.get("effectiveVersion")) == 0) continue;
            result.addAll(feeRows(row));
        }
        return result;
    }

    public List<Map<String, Object>> reminders(boolean scoped) {
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now(ZONE);
        for (Map<String, Object> row : rows()) {
            if ((scoped && !customerVisible(row)) || number(row.get("effectiveVersion")) == 0
                    || "completed".equals(row.get("status")) || !"0".equals(row.get("customerStatus"))
                    || "1".equals(row.get("publicPool")) || number(row.get("ownerUserId")) == 0) continue;
            Map<String, Object> effective = effective(row);
            LocalDate end = date(effective.get("endDate"), "到期日期");
            long days = ChronoUnit.DAYS.between(today, end);
            if (days <= 30 && !hasSignedRenewal(number(row.get("id")), end)) {
                result.add(reminder(row, "expiry", "contract_expiry", days < 0 ? "合同已到期" : "合同即将到期",
                        days <= 7 ? "high" : "medium", days < 0 ? "已到期 " + (-days) + " 天，请办理续约或结案" : "将在 " + days + " 天后到期，请提前跟进续约", end));
            }
            for (Map<String, Object> term : feeRows(row)) {
                if (money(term.get("outstandingAmount")).signum() <= 0) continue;
                LocalDate due = date(term.get("dueDate"), "收费到期日");
                long dueDays = ChronoUnit.DAYS.between(today, due);
                if (dueDays <= 7) {
                    Map<String, Object> reminder = reminder(row, "fee-" + term.get("key"), dueDays < 0 ? "payment_overdue" : "payment_due",
                            dueDays < 0 ? "收费逾期" : "收费即将到期", dueDays < 0 ? "high" : "medium",
                            term.get("title") + "，待收 " + term.get("outstandingAmount") + " 元，" + (dueDays < 0 ? "逾期 " + (-dueDays) + " 天" : "距到期 " + dueDays + " 天"), due);
                    reminder.put("termKey", term.get("key"));
                    result.add(reminder);
                }
            }
        }
        return result;
    }

    @Transactional
    public void handleReminder(long id, Map<String, Object> body) {
        Map<String, Object> row = require(id, true);
        requireCustomerAccess(row);
        String taskId = text(body.get("taskId"));
        if (reminders(true).stream().noneMatch(item -> taskId.equals(item.get("taskId")) && number(item.get("contractId")) == id)) throw new ApiException(409, "提醒已消除或不存在，请刷新");
        String content = required(body.get("content"), 2000, "处理记录");
        jdbc.update("INSERT INTO oa_crm_contract_reminder_log (contract_id, task_id, content, create_by) VALUES (?, ?, ?, ?)", id, taskId, content, AuthContext.username());
    }

    public boolean managedCustomer(long customerId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM oa_crm_contract WHERE customer_id = ? AND effective_version > 0", Long.class, customerId) > 0;
    }

    @Transactional
    public void syncCustomer(long customerId) {
        jdbc.queryForList("SELECT id FROM oa_merchant WHERE id = ? FOR UPDATE", Long.class, customerId);
        if (!managedCustomer(customerId)) return;
        BigDecimal total = BigDecimal.ZERO;
        LocalDate expiry = null;
        for (Map<String, Object> row : rows()) {
            if (number(row.get("customerId")) != customerId || number(row.get("effectiveVersion")) == 0) continue;
            Map<String, Object> effective = effective(row);
            total = total.add(money(effective.get("amount")));
            LocalDate day = date(effective.get("endDate"), "到期日期");
            if (!"completed".equals(row.get("status")) && (expiry == null || day.isBefore(expiry))) expiry = day;
        }
        BigDecimal received = jdbc.queryForObject("""
                SELECT COALESCE(SUM(r.amount), 0) FROM oa_crm_contract_receipt r
                JOIN oa_crm_contract c ON c.id = r.contract_id WHERE c.customer_id = ?
                """, BigDecimal.class, customerId);
        Date latest = jdbc.queryForObject("""
                SELECT MAX(r.receipt_date) FROM oa_crm_contract_receipt r
                JOIN oa_crm_contract c ON c.id = r.contract_id WHERE c.customer_id = ?
                """, Date.class, customerId);
        jdbc.update("""
                UPDATE oa_merchant SET contract_amount = ?, received_amount = ?, contract_expire_date = ?,
                last_payment_date = ?, cooperation_status = CASE
                  WHEN cooperation_status IN ('潜在客户', '初步接洽') THEN '合作中' ELSE cooperation_status END
                WHERE id = ?
                """, total, received, expiry == null ? null : Date.valueOf(expiry), latest, customerId);
    }

    public void requireReleasable(long customerId) {
        if (jdbc.queryForObject("""
                SELECT COUNT(*) FROM oa_crm_contract WHERE customer_id = ?
                AND ((effective_version > 0 AND status <> 'completed') OR status IN ('pending', 'approved'))
                """, Long.class, customerId) > 0) throw new ApiException(409, "客户仍有待审批、待签署或履约中的合同，请交接负责人而非转入公海");
    }

    public void syncOwnership(long customerId) {
        jdbc.update("""
                UPDATE oa_todo SET user_id = (SELECT COALESCE(owner_user_id, 0) FROM oa_merchant WHERE id = ?)
                WHERE business_type = 'crmRisk' AND business_id = ? AND status = 'todo'
                """, customerId, customerId);
    }

    private List<Map<String, Object>> rows() {
        return JdbcMaps.camelCase(jdbc.queryForList(SELECT + " ORDER BY c.id DESC"));
    }

    private Map<String, Object> require(long id, boolean lock) {
        if (lock) {
            if (jdbc.queryForList("SELECT id FROM oa_crm_contract WHERE id = ? FOR UPDATE", Long.class, id).isEmpty()) throw new ApiException(404, "合同不存在");
        }
        List<Map<String, Object>> result = JdbcMaps.camelCase(jdbc.queryForList(SELECT + " WHERE c.id = ?", id));
        if (result.isEmpty()) throw new ApiException(404, "合同不存在");
        Map<String, Object> row = result.get(0);
        if (!visible(row, false)) throw new ApiException(403, "无权访问该客户的合同");
        return row;
    }

    private boolean visible(Map<String, Object> row, boolean approvals) {
        boolean assigned = number(row.get("approvalUserId")) == AuthContext.userId()
                && system.hasModuleAccess(AuthContext.userId(), 341202L);
        boolean customerRead = List.of(341201L, 341203L, 341204L, 341205L, 341101L).stream()
                .anyMatch(moduleId -> system.hasModuleAccess(AuthContext.userId(), moduleId));
        if (approvals) return assigned;
        if ((customerRead && customerVisible(row)) || assigned) return true;
        if (!system.hasModuleAccess(AuthContext.userId(), 341202L)) return false;
        return jdbc.queryForList("SELECT snapshot_json FROM oa_crm_contract_version WHERE contract_id = ?", String.class, row.get("id"))
                .stream().map(this::readMap).flatMap(snapshot -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(number(snapshot.get("workflowRequestId"))), signingRequestIds(snapshot).stream()))
                .filter(requestId -> requestId > 0L).anyMatch(requestId -> jdbc.queryForObject("""
                        SELECT COUNT(*) FROM oa_workflow_request r JOIN oa_process_task t ON t.instance_id = r.process_instance_id
                        WHERE r.id = ? AND t.assignee_id = ?
                        """, Long.class, requestId, AuthContext.userId()) > 0L);
    }

    private boolean customerVisible(Map<String, Object> row) {
        return scopes.canAccess(AuthContext.userId(), row.get("ownerDeptId"), row.get("ownerUserId"));
    }

    private void requireCustomerAccess(Map<String, Object> row) {
        if (!customerVisible(row)) throw new ApiException(403, "无权操作该客户的合同");
    }

    private Map<String, Object> customer(long id) {
        List<Map<String, Object>> result = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT m.*, u.dept_id AS owner_dept_id FROM oa_merchant m
                LEFT JOIN sys_user u ON u.user_id = m.owner_user_id WHERE m.id = ? AND m.status = '0'
                """, id));
        if (result.isEmpty()) throw new ApiException(400, "请选择有效客户");
        Map<String, Object> row = result.get(0);
        if (!customerVisible(row)) throw new ApiException(403, "无权访问该客户");
        if ("1".equals(row.get("publicPool")) || number(row.get("ownerUserId")) <= 0) throw new ApiException(400, "请先领取或分配客户负责人，再起草合同");
        return row;
    }

    private Map<String, Object> decorate(Map<String, Object> row) {
        row.put("feeTerms", readTerms(row.get("termsJson")));
        row.remove("termsJson");
        Map<String, Object> effective = effective(row);
        row.put("effectiveAmount", effective.isEmpty() ? BigDecimal.ZERO : money(effective.get("amount")));
        row.put("effectiveEndDate", effective.get("endDate"));
        row.put("effectiveFeeTerms", effective.isEmpty() ? List.of() : feeRows(row));
        row.put("receivedAmount", jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM oa_crm_contract_receipt WHERE contract_id = ?", BigDecimal.class, row.get("id")));
        row.put("outstandingAmount", money(row.get("effectiveAmount")).subtract(money(row.get("receivedAmount"))));
        row.put("canEditCustomer", customerVisible(row));
        row.put("signingPending", "running".equals(signingWorkflow(number(row.get("id")), false).get("status")));
        return row;
    }

    private Map<String, Object> effective(Map<String, Object> row) {
        if (number(row.get("effectiveVersion")) == 0) return Map.of();
        List<String> snapshots = jdbc.queryForList("SELECT snapshot_json FROM oa_crm_contract_version WHERE contract_id = ? AND version_no = ?",
                String.class, row.get("id"), row.get("effectiveVersion"));
        return snapshots.isEmpty() ? Map.of() : readMap(snapshots.get(0));
    }

    private List<Map<String, Object>> feeRows(Map<String, Object> row) {
        Map<String, Object> effective = effective(row);
        List<Map<String, Object>> terms = readTerms(effective.get("feeTerms"));
        for (Map<String, Object> term : terms) {
            term.put("contractId", row.get("id"));
            term.put("contractName", row.get("name"));
            term.put("customerId", row.get("customerId"));
            term.put("customerName", row.get("customerName"));
            term.put("ownerUserName", row.get("ownerUserName"));
            term.put("effectiveVersion", row.get("effectiveVersion"));
            term.put("contractStatus", row.get("status"));
            BigDecimal received = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM oa_crm_contract_receipt WHERE contract_id = ? AND term_key = ?", BigDecimal.class, row.get("id"), term.get("key"));
            term.put("receivedAmount", received);
            BigDecimal outstanding = money(term.get("amount")).subtract(received);
            term.put("outstandingAmount", outstanding);
            term.put("paymentStatus", outstanding.signum() <= 0 ? "paid" : date(term.get("dueDate"), "收费到期日").isBefore(LocalDate.now(ZONE)) ? "overdue" : "unpaid");
            term.put("rowKey", row.get("id") + "-" + term.get("key"));
        }
        return terms;
    }

    private Map<String, Object> validate(Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", required(body.get("name"), 200, "合同名称"));
        String service = required(body.get("serviceType"), 64, "服务类型");
        if (!Set.of("猎头招聘", "招聘流程外包", "人力外包", "灵活用工", "其他服务").contains(service)) throw new ApiException(400, "服务类型无效");
        result.put("serviceType", service);
        BigDecimal amount = money(body.get("amount"));
        if (amount.signum() <= 0) throw new ApiException(400, "合同金额必须大于0");
        result.put("amount", amount);
        LocalDate start = date(body.get("startDate"), "开始日期");
        LocalDate end = date(body.get("endDate"), "结束日期");
        if (end.isBefore(start)) throw new ApiException(400, "合同结束日期不能早于开始日期");
        result.put("startDate", Date.valueOf(start));
        result.put("endDate", Date.valueOf(end));
        result.put("attachment", limited(body.get("attachment"), 8000, "合同附件"));
        result.put("remark", limited(body.get("remark"), 2000, "合同备注"));
        List<Map<String, Object>> terms = readTerms(body.get("feeTerms"));
        if (terms.size() > 100) throw new ApiException(400, "收费条款不能超过100条");
        Set<String> keys = new HashSet<>();
        for (Map<String, Object> term : terms) {
            String key = text(term.get("key"));
            if (blank(key)) key = UUID.randomUUID().toString();
            if (!key.matches("[A-Za-z0-9_-]{1,64}") || !keys.add(key)) throw new ApiException(400, "收费条款标识重复或无效");
            term.put("key", key);
            term.put("title", required(term.get("title"), 200, "收费名称"));
            term.put("trigger", required(term.get("trigger"), 500, "收费触发条件"));
            term.put("remark", limited(term.get("remark"), 1000, "收费说明"));
            String mode = text(term.getOrDefault("chargeMode", "fixed"));
            if (!Set.of("fixed", "rate").contains(mode)) throw new ApiException(400, "计费方式无效");
            term.put("chargeMode", mode);
            BigDecimal fee = money(term.get("amount"));
            if ("rate".equals(mode)) {
                BigDecimal base = money(term.get("baseAmount"));
                BigDecimal rate = money(term.get("rate"));
                if (base.signum() <= 0 || rate.signum() <= 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0) throw new ApiException(400, "比例计费须填写有效基数和0至100之间的比例");
                fee = base.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                term.put("baseAmount", base);
                term.put("rate", rate);
            }
            if (fee.signum() <= 0) throw new ApiException(400, "收费金额必须大于0");
            term.put("amount", fee);
            term.put("dueDate", date(term.get("dueDate"), "收费到期日").toString());
            long guarantee = number(term.get("guaranteeDays"));
            if (guarantee < 0 || guarantee > 3650) throw new ApiException(400, "保证期须在0至3650天之间");
            term.put("guaranteeDays", guarantee);
        }
        result.put("feeTerms", terms);
        return result;
    }

    private void protectPaidTerms(long id, Map<String, Object> fields) {
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        readTerms(fields.get("feeTerms")).forEach(term -> amounts.put(text(term.get("key")), money(term.get("amount"))));
        for (Map<String, Object> paid : JdbcMaps.camelCase(jdbc.queryForList("SELECT term_key, SUM(amount) AS paid FROM oa_crm_contract_receipt WHERE contract_id = ? GROUP BY term_key", id))) {
            if (!amounts.containsKey(text(paid.get("termKey"))) || amounts.get(text(paid.get("termKey"))).compareTo(money(paid.get("paid"))) < 0) {
                throw new ApiException(409, "已收款条款不能删除，条款金额不能低于已收金额");
            }
        }
    }

    private boolean hasSignedRenewal(long id, LocalDate end) {
        for (Map<String, Object> child : JdbcMaps.camelCase(jdbc.queryForList(
                "SELECT * FROM oa_crm_contract WHERE parent_contract_id = ? AND effective_version > 0", id))) {
            Map<String, Object> current = effective(child);
            if (!date(current.get("startDate"), "开始日期").isAfter(end.plusDays(1)) && date(current.get("endDate"), "结束日期").isAfter(end)) return true;
        }
        return false;
    }

    private Map<String, Object> reminder(Map<String, Object> row, String key, String type, String title, String level, String reason, LocalDate due) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("taskId", "crm-contract-" + row.get("id") + "-" + key);
        item.put("contractId", row.get("id"));
        item.put("contractName", row.get("name"));
        item.put("customerId", row.get("customerId"));
        item.put("customerName", row.get("customerName"));
        item.put("ownerUserId", row.get("ownerUserId"));
        item.put("ownerUserName", row.get("ownerUserName"));
        item.put("riskType", type);
        item.put("title", title);
        item.put("level", level);
        item.put("reason", reason);
        item.put("dueDate", due.toString());
        item.put("status", "todo");
        item.put("source", "contract");
        return item;
    }

    private void validateOpportunity(long opportunityId, long customerId) {
        List<String> records = jdbc.queryForList("SELECT payload_json FROM biz_record WHERE id = ? AND module = '/project/business/list'", String.class, opportunityId);
        if (records.isEmpty() || number(readMap(records.get(0)).get("customerId")) != customerId) throw new ApiException(400, "商机必须归属于合同客户");
    }

    private void expectRevision(Map<String, Object> row, Map<String, Object> body) {
        if (!body.containsKey("revision") || number(body.get("revision")) != number(row.get("revision"))) throw new ApiException(409, "合同已更新，请刷新后重试");
    }

    private void expectStatus(Map<String, Object> row, String... statuses) {
        if (!Set.of(statuses).contains(text(row.get("status")))) throw new ApiException(409, "当前合同状态不允许该操作，请刷新");
    }

    private String approvalTask(long id, long version) { return "crm-contract-approval-" + id + "-v" + version; }
    private boolean blank(Object value) { return value == null || text(value).isBlank(); }
    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private long number(Object value) {
        if (blank(value)) return 0L;
        try { return Long.parseLong(text(value)); } catch (Exception e) { throw new ApiException(400, "整数参数无效"); }
    }
    private String required(Object value, int max, String label) {
        String result = limited(value, max, label).trim();
        if (result.isBlank()) throw new ApiException(400, label + "不能为空");
        return result;
    }
    private String limited(Object value, int max, String label) {
        String result = text(value);
        if (result.length() > max) throw new ApiException(400, label + "长度不能超过" + max + "字");
        return result;
    }
    private BigDecimal money(Object value) {
        try {
            BigDecimal amount = new BigDecimal(text(value)).setScale(2, RoundingMode.HALF_UP);
            if (amount.abs().compareTo(new BigDecimal("9999999999999999.99")) > 0) throw new IllegalArgumentException();
            return amount;
        } catch (Exception e) { throw new ApiException(400, "金额或比例格式无效"); }
    }
    private LocalDate date(Object value, String label) {
        try { return LocalDate.parse(text(value).substring(0, 10)); }
        catch (Exception e) { throw new ApiException(400, label + "格式无效"); }
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new ApiException(400, "合同数据格式无效"); }
    }
    private Map<String, Object> readMap(Object value) {
        try { return mapper.readValue(text(value), new TypeReference<LinkedHashMap<String, Object>>() {}); }
        catch (Exception e) { throw new ApiException(400, "合同快照格式无效"); }
    }
    private List<Map<String, Object>> readTerms(Object value) {
        if (value == null) return new ArrayList<>();
        try { return mapper.readValue(value instanceof String ? (String) value : json(value), new TypeReference<ArrayList<Map<String, Object>>>() {}); }
        catch (Exception e) { throw new ApiException(400, "收费条款格式无效"); }
    }
}
