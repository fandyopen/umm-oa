package com.seal.oa.workflow;

import com.seal.oa.common.ApiException;
import com.seal.oa.oa.CustomerFinanceService;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class CustomerFinanceActionHandler implements WorkflowActionHandler {
    private final CustomerFinanceService finance;
    public CustomerFinanceActionHandler(CustomerFinanceService finance) { this.finance = finance; }
    @Override public Set<String> handlerKeys() { return Set.of("crm.finance.invoice_submit", "crm.finance.receipt_confirm", "crm.finance.refund_submit", "crm.finance.adjust_confirm", "crm.finance.cost_confirm"); }
    @Override public boolean supportsAction(long moduleId, String actionCode) { return moduleId == CustomerFinanceService.MODULE && handlerKeys().contains("crm.finance." + actionCode); }
    @Override public void validate(WorkflowActionContext context) {
        if (!supportsAction(context.moduleId(), context.actionCode()) || !handlerKeys().contains(context.handlerKey())) throw new ApiException(400, "财务确认执行器与按钮不匹配");
        finance.validateWorkflow(context);
    }
    @Override public void submitted(WorkflowActionContext context) { finance.submitted(context); }
    @Override public void workflowStateChanged(WorkflowActionContext context, String instance, String status, String task, String name) { finance.workflowRejected(context, status); }
    @Override public void validateCompletion(WorkflowActionContext context, String instance, String task, boolean pass, String comment, Long operator) { finance.validateDecision(context, pass, comment, operator); }
    @Override public Object execute(WorkflowActionContext context) { return finance.execute(context); }
}
