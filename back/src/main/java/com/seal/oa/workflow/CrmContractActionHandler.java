package com.seal.oa.workflow;

import com.seal.oa.common.ApiException;
import com.seal.oa.lowcode.ModuleFlowBindingService;
import com.seal.oa.oa.CrmContractService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class CrmContractActionHandler implements WorkflowActionHandler {
    private final CrmContractService contracts;
    private final ModuleFlowBindingService bindings;

    public CrmContractActionHandler(CrmContractService contracts, ModuleFlowBindingService bindings) {
        this.contracts = contracts;
        this.bindings = bindings;
    }

    @Override
    public Set<String> handlerKeys() { return Set.of("crm.contract.submit"); }

    @Override
    public boolean supportsAction(long moduleId, String actionCode) {
        return moduleId == 341201L && "submit".equals(actionCode);
    }

    @Override
    public void validate(WorkflowActionContext context) {
        if (context.moduleId() != 341201L || !"submit".equals(context.actionCode())) {
            throw new ApiException(400, "合同提交处理器仅用于合同列表的提交审批按钮 submit");
        }
        if (context.requestId() > 0L) {
            contracts.validateWorkflowRequest(context.requestId(), context.payload());
            return;
        }
        if (bindings.activeDesign(1L, 341201L, "submit").isEmpty()) {
            throw new ApiException(400, "请先到模块管理 → 合同列表 → 提交审批（submit）开启流程设计并发布审批流程");
        }
        contracts.validateWorkflowSubmission(id(context.payload()), context.payload());
    }

    @Override
    public void submitted(WorkflowActionContext context) {
        Map<String, Object> snapshot = contracts.prepareWorkflowSubmission(id(context.payload()), context.payload(), context.requestId());
        context.payload().putAll(snapshot);
        context.payload().put("actionName", "提交审批");
    }

    @Override
    public void workflowStateChanged(WorkflowActionContext context, String instanceId,
                                      String businessStatus, String taskId, String taskName) {
        contracts.syncWorkflowState(context.requestId(), context.payload(), instanceId, businessStatus, taskId, taskName);
    }

    @Override
    public void validateCompletion(WorkflowActionContext context, String instanceId,
                                    String taskId, boolean pass, String comment, Long operatorId) {
        contracts.validateWorkflowDecision(context.requestId(), context.payload(), instanceId, taskId, operatorId, pass, comment);
    }

    @Override
    public Object execute(WorkflowActionContext context) {
        if (context.requestId() <= 0L) throw new ApiException(400, "合同提交必须经过已发布的审批流程，不支持直接审批通过");
        Map<String, Object> workflow = contracts.workflow(id(context.payload()));
        contracts.syncWorkflowState(context.requestId(), context.payload(), String.valueOf(workflow.get("instanceId")),
                "审批通过", String.valueOf(workflow.get("taskId")), "");
        return Map.of("contractId", id(context.payload()), "versionNo", context.payload().get("versionNo"), "status", "approved");
    }

    private long id(Map<String, Object> payload) {
        try { return Long.parseLong(String.valueOf(payload.get("contractId"))); }
        catch (RuntimeException exception) { throw new ApiException(400, "合同编号无效"); }
    }
}
