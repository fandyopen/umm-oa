package com.seal.oa.oa;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.lowcode.LowCodeWorkflowService;
import com.seal.oa.workflow.WorkflowActionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class CrmContractWorkflowService {
    private final CrmContractService contracts;
    private final WorkflowActionService actions;
    private final LowCodeWorkflowService workflows;

    public CrmContractWorkflowService(CrmContractService contracts, WorkflowActionService actions, LowCodeWorkflowService workflows) {
        this.contracts = contracts;
        this.actions = actions;
        this.workflows = workflows;
    }

    @Transactional
    public Map<String, Object> submit(long id, Map<String, Object> body) {
        Map<String, Object> contract = contracts.detail(id);
        Map<String, Object> payload = new LinkedHashMap<>(body);
        payload.put("contractId", id);
        payload.put("title", "合同审批：" + contract.get("name") + " - " + contract.get("customerName"));
        actions.execute(Map.of("moduleId", 341201, "actionCode", "submit", "payload", payload));
        return contracts.detail(id);
    }

    @Transactional
    public Map<String, Object> sign(long id, Map<String, Object> body) {
        Map<String, Object> contract = contracts.detail(id);
        Map<String, Object> payload = new LinkedHashMap<>(body);
        payload.put("contractId", id);
        payload.put("title", "签署审批：" + contract.get("name") + " - " + contract.get("customerName"));
        actions.execute(Map.of("moduleId", 341201, "actionCode", "sign", "payload", payload));
        return contracts.detail(id);
    }

    @Transactional
    public Map<String, Object> approve(long id, Map<String, Object> body) {
        Map<String, Object> workflow = contracts.approvalTask(id, body);
        // 已提交的旧版单人审批保持可办结；新提交全部进入通用流程。
        if (workflow.isEmpty()) return contracts.approve(id, body);
        String decision = String.valueOf(body.get("decision"));
        if (!Set.of("approved", "rejected").contains(decision)) throw new ApiException(400, "审批结果无效");
        workflows.complete(Map.of("instanceId", workflow.get("instanceId"), "taskId", workflow.get("taskId"),
                "variables", Map.of("pass", "approved".equals(decision), "comment", String.valueOf(body.getOrDefault("comment", "")))), AuthContext.userId());
        return contracts.detail(id);
    }

}
