package com.seal.oa.workflow;

import com.seal.oa.common.ApiException;
import com.seal.oa.recruitment.RecruitmentApprovalService;
import com.seal.oa.recruitment.RecruitmentService;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class RecruitmentActionHandler implements WorkflowActionHandler {
    private final RecruitmentApprovalService service;
    public RecruitmentActionHandler(RecruitmentApprovalService service){this.service=service;}
    @Override public Set<String> handlerKeys(){return Set.of("recruit.job.demand","recruit.job.change","recruit.talent.merge","recruit.talent.handover");}
    @Override public boolean supportsAction(long moduleId,String action){return moduleId==RecruitmentService.JOB&&Set.of("demand","change").contains(action)||moduleId==RecruitmentService.TALENT&&Set.of("merge","handover").contains(action);}
    @Override public void validate(WorkflowActionContext c){if(!supportsAction(c.moduleId(),c.actionCode())||!c.handlerKey().equals("recruit."+(c.moduleId()==RecruitmentService.JOB?"job.":"talent.")+c.actionCode()))throw new ApiException(400,"招聘执行器与按钮不匹配");service.validate(c);}
    @Override public void submitted(WorkflowActionContext c){service.submitted(c);}
    @Override public void workflowStateChanged(WorkflowActionContext c,String instance,String status,String taskId,String taskName){service.state(c,instance,status);}
    @Override public void validateCompletion(WorkflowActionContext c,String instance,String taskId,boolean pass,String comment,Long operator){service.decision(c,pass,comment,operator);}
    @Override public Object execute(WorkflowActionContext c){return service.execute(c);}
}
