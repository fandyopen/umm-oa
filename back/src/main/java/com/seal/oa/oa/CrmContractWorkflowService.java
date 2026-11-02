package com.seal.oa.oa;

import com.seal.oa.workflow.WorkflowActionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CrmContractWorkflowService {
    private final CrmContractService contracts;
    private final WorkflowActionService actions;

    public CrmContractWorkflowService(CrmContractService contracts, WorkflowActionService actions) {
        this.contracts = contracts;
        this.actions = actions;
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

}
