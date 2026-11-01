package com.seal.oa.workflow;

import com.seal.oa.common.ApiException;
import com.seal.oa.oa.CrmContractService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class CrmContractSignActionHandler implements WorkflowActionHandler {
    private final CrmContractService contracts;

    public CrmContractSignActionHandler(CrmContractService contracts) { this.contracts = contracts; }

    @Override
    public Set<String> handlerKeys() { return Set.of("crm.contract.sign"); }

    @Override
    public boolean supportsAction(long moduleId, String actionCode) {
        return moduleId == 341201L && "sign".equals(actionCode);
    }

    @Override
    public void validate(WorkflowActionContext context) {
        if (!supportsAction(context.moduleId(), context.actionCode())) throw new ApiException(400, "合同签署执行器仅用于签署生效按钮");
        if (context.requestId() > 0) contracts.validateSigningRequest(context.requestId(), context.payload());
        else contracts.validateSigningSubmission(id(context.payload()), context.payload());
    }

    @Override
    public void submitted(WorkflowActionContext context) {
        Map<String, Object> frozen = contracts.prepareSigningSubmission(id(context.payload()), context.payload(), context.requestId());
        context.payload().putAll(frozen);
        context.payload().put("actionName", "签署生效");
    }

    @Override
    public void workflowStateChanged(WorkflowActionContext context, String instanceId, String businessStatus, String taskId, String taskName) {
        contracts.syncSigningWorkflowState(context.requestId(), context.payload(), instanceId, businessStatus, taskId);
    }

    @Override
    public void validateCompletion(WorkflowActionContext context, String instanceId, String taskId, boolean pass, String comment, Long operatorId) {
        contracts.validateSigningDecision(context.requestId(), context.payload(), instanceId, taskId, operatorId, pass, comment);
    }

    @Override
    public Object execute(WorkflowActionContext context) {
        if (context.requestId() > 0) return contracts.executeApprovedSigning(context.requestId(), context.payload());
        return contracts.sign(id(context.payload()), context.payload());
    }

    private long id(Map<String, Object> payload) {
        try { return Long.parseLong(String.valueOf(payload.get("contractId"))); }
        catch (RuntimeException exception) { throw new ApiException(400, "合同编号无效"); }
    }
}
