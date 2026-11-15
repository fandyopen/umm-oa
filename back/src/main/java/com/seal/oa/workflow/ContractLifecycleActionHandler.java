package com.seal.oa.workflow;

import com.seal.oa.common.ApiException;
import com.seal.oa.oa.ContractLifecycleService;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class ContractLifecycleActionHandler implements WorkflowActionHandler {
    private final ContractLifecycleService contracts;
    public ContractLifecycleActionHandler(ContractLifecycleService contracts) { this.contracts=contracts; }
    public Set<String> handlerKeys() { return Set.of("crm.contract.void","crm.contract.terminate"); }
    public boolean supportsAction(long moduleId,String code) { return moduleId==341201&&Set.of("void","terminate").contains(code); }
    private long id(WorkflowActionContext c) {
        if(!supportsAction(c.moduleId(),c.actionCode())||!c.handlerKey().equals("crm.contract."+c.actionCode()))throw new ApiException(400,"合同执行器不适用于该按钮");
        try { return Long.parseLong(String.valueOf(c.payload().get("contractId"))); }catch(Exception e){throw new ApiException(400,"无效合同编号");}
    }
    public void validate(WorkflowActionContext c) { contracts.validate(id(c),c.actionCode(),c.payload(),c.requestId()); }
    public void submitted(WorkflowActionContext c) { c.payload().putAll(contracts.submitted(id(c),c.actionCode(),c.payload(),c.requestId())); }
    public void workflowStateChanged(WorkflowActionContext c,String instance,String status,String task,String taskName) { contracts.state(id(c),c.actionCode(),c.payload(),c.requestId(),instance,status,task); }
    public void validateCompletion(WorkflowActionContext c,String instance,String task,boolean pass,String comment,Long operator) { contracts.validateDecision(id(c),c.actionCode(),c.payload(),c.requestId(),instance,task,operator,pass,comment); }
    public Object execute(WorkflowActionContext c) { return contracts.execute(id(c),c.actionCode(),c.payload(),c.requestId()); }
}
