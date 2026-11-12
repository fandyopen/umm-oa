package com.seal.oa.commission;

import com.seal.oa.workflow.WorkflowActionHandler;
import com.seal.oa.workflow.WorkflowActionContext;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class CommissionWorkflowHandler implements WorkflowActionHandler {
    private final CommissionService service;
    public CommissionWorkflowHandler(CommissionService service){this.service=service;}
    public Set<String> handlerKeys(){return Set.of("commission.batch.submit");}
    public boolean supportsAction(long moduleId,String action){return moduleId==CommissionService.MODULE && "submit".equals(action);}
    public void validate(WorkflowActionContext context){service.validateWorkflow(context);}
    public void submitted(WorkflowActionContext context){service.submitted(context);}
    public void validateCompletion(WorkflowActionContext context,String instance,String task,boolean pass,String comment,Long operator){service.decision(context,pass,comment,operator);}
    public Object execute(WorkflowActionContext context){return service.approve(context);}
    public void workflowStateChanged(WorkflowActionContext context,String instance,String status,String task,String name){service.rejected(context,status);}
}
