package com.seal.oa.recruitment;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.workflow.WorkflowActionContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.seal.oa.recruitment.RecruitmentService.*;

@Service
public class RecruitmentApprovalService {
    private final RecruitmentService s;
    public RecruitmentApprovalService(RecruitmentService s){this.s=s;}
    String type(WorkflowActionContext c){return c.moduleId()==JOB?"job":"talent";}
    Map<String,Object> entity(WorkflowActionContext c,boolean lock){long id=num(c.payload().get("entityId"));return c.moduleId()==JOB?s.job(id,lock):s.talent(id,lock);}

    @Transactional public void validate(WorkflowActionContext c){
        if(c.requestId()>0){pending(c);return;}
        c.payload().keySet().retainAll(Set.of("entityId","revision","reason","fields","ownerUserId","collaborators","targetId","disputeId","title","actionName","_workflowDefinition"));
        Map<String,Object>r=entity(c,true);s.revision(r,c.payload());s.unlocked(type(c),num(r.get("id")));
        c.payload().put("reason",required(c.payload().get("reason"),2000,"申请原因"));
        switch(c.actionCode()){
            case "demand"->{if(!Set.of("draft","rejected","withdrawn").contains(text(r.get("demandStatus"))))throw new ApiException(409,"当前需求不能重复提交");if("closed".equals(r.get("status")))throw new ApiException(409,"已关闭职位不能提交确认");}
            case "change"->{if(!"approved".equals(r.get("demandStatus")))throw new ApiException(409,"请先确认职位需求");Map<String,Object>b=new LinkedHashMap<>(r);b.put("collaborators",s.array(r.get("collaboratorsJson")));Map<String,Object>change=s.object(c.payload().get("fields"));change.keySet().retainAll(Set.of("title","salaryMin","salaryMax","headcount","requirements","feeTerms","experience","city","industry","targetDate","contractId"));b.putAll(change);Map<String,Object>f=s.jobFields(b);if(num(f.get("headcount"))<num(r.get("hired")))throw new ApiException(400,"招聘人数不能小于已入职人数");c.payload().put("fields",f);}
            case "handover"->{if(Set.of("merged","archived","disabled").contains(text(r.get("status"))))throw new ApiException(409,"请先激活候选人");long owner=num(c.payload().get("ownerUserId"));long disputeId=num(c.payload().get("disputeId"));long open=s.jdbc().queryForObject("SELECT COUNT(*) FROM oa_recruit_dispute WHERE talent_id=? AND status='open'",Long.class,r.get("id"));if(open>0&&disputeId==0)throw new ApiException(409,"请从归属争议记录发起转交");if(disputeId>0){s.permission(TALENT,"dispute");Map<String,Object>d=s.one("SELECT * FROM oa_recruit_dispute WHERE id=? AND talent_id=? FOR UPDATE",disputeId,r.get("id"));RecruitmentOwnershipService.requireHandler(d);if(num(d.get("proposedOwnerId"))!=owner)throw new ApiException(400,"转交目标须与争议拟归属人一致");}Map<String,Object>user=s.user(owner);if(owner==num(r.get("ownerUserId")))throw new ApiException(400,"目标负责人不能与原负责人相同");c.payload().put("ownerUserName",user.get("nickName"));c.payload().put("collaborators",s.collaborators(c.payload().get("collaborators")));}
            case "merge"->{long target=num(c.payload().get("targetId"));long source=num(r.get("id"));if(target==source)throw new ApiException(400,"不能合并到自身");Map<String,Object>t=s.talent(target,true);s.unlocked("talent",target);if(s.jdbc().queryForObject("SELECT COUNT(*) FROM oa_recruit_dispute WHERE talent_id IN (?,?) AND status='open'",Long.class,source,target)>0)throw new ApiException(409,"请先结案归属争议，再合并档案");if(Set.of("merged","deleted").contains(text(t.get("status")))||Set.of("merged","deleted").contains(text(r.get("status"))))throw new ApiException(409,"档案已合并或删除");
                boolean duplicate=!text(r.get("phone")).isBlank()&&text(r.get("phone")).equals(text(t.get("phone")))||!text(r.get("email")).isBlank()&&text(r.get("email")).equalsIgnoreCase(text(t.get("email")));
                if(!duplicate)throw new ApiException(400,"手机号或邮箱相同的重复档案才可合并");c.payload().put("targetName",t.get("name"));
                if(s.jdbc().queryForObject("SELECT COUNT(*) FROM oa_recruit_application a JOIN oa_recruit_application b ON a.job_id=b.job_id WHERE a.talent_id=? AND b.talent_id=?",Long.class,source,target)>0)throw new ApiException(409,"两个档案存在同职位招聘记录，请先核实，不能覆盖历史流程");c.payload().put("targetRevision",t.get("revision"));}
            default->throw new ApiException(400,"不支持的招聘审批动作");
        }
        c.payload().put("entityType",type(c));c.payload().put("entityName",r.get(c.moduleId()==JOB?"title":"name"));
        Map<String,Object>snapshot=new LinkedHashMap<>(r);snapshot.remove("phone");snapshot.remove("email");snapshot.remove("remark");snapshot.remove("collaboratorsJson");
        c.payload().put("snapshot",snapshot);c.payload().put("submitterId",AuthContext.userId());
    }
    @Transactional public void submitted(WorkflowActionContext c){
        Map<String,Object>r=entity(c,true);s.revision(r,c.payload());s.unlocked(type(c),num(r.get("id")));
        s.jdbc().update("INSERT INTO oa_recruit_approval (workflow_request_id,entity_type,entity_id,related_id,action_code,revision,payload_json,create_by) VALUES (?,?,?,?,?,?,?,?)",c.requestId(),type(c),r.get("id"),"merge".equals(c.actionCode())?num(c.payload().get("targetId")):null,c.actionCode(),r.get("revision"),s.json(c.payload()),AuthContext.userId());
        if("demand".equals(c.actionCode()))s.jdbc().update("UPDATE oa_recruit_job SET demand_status='pending' WHERE id=?",r.get("id"));
        s.event(type(c),num(r.get("id")),"submit",text(c.payload().get("reason")),Map.of("requestId",c.requestId(),"action",c.actionCode()));
    }
    Map<String,Object> pending(WorkflowActionContext c){Map<String,Object>a=s.one("SELECT * FROM oa_recruit_approval WHERE workflow_request_id=? FOR UPDATE",c.requestId());if(!"pending".equals(a.get("status")))throw new ApiException(409,"申请已结束或撤回");return a;}
    @Transactional public void decision(WorkflowActionContext c,boolean pass,String comment,Long operator){Map<String,Object>a=pending(c);if(num(a.get("createBy"))==num(operator))throw new ApiException(403,"不能审批本人提交的招聘申请");if(!pass)required(comment,2000,"退回原因");}
    @Transactional public void state(WorkflowActionContext c,String instance,String status){
        if(!"审批未通过".equals(status)){s.jdbc().update("UPDATE oa_recruit_approval SET instance_id=? WHERE workflow_request_id=?",instance,c.requestId());return;}
        Map<String,Object>a=pending(c);s.jdbc().update("UPDATE oa_recruit_approval SET status='rejected',instance_id=?,update_time=CURRENT_TIMESTAMP WHERE workflow_request_id=?",instance,c.requestId());
        if("demand".equals(c.actionCode()))s.jdbc().update("UPDATE oa_recruit_job SET demand_status='rejected' WHERE id=?",a.get("entityId"));
        s.event(type(c),num(a.get("entityId")),"rejected","审批退回，可修改后重新提交",Map.of("requestId",c.requestId()));
    }
    @Transactional public Object execute(WorkflowActionContext c){
        Map<String,Object>p=c.payload();Map<String,Object>a=c.requestId()>0?pending(c):Map.of();long id=num(p.get("entityId"));String type=type(c);
        // Authorization was checked at submission; the immutable request is executed as its original applicant.
        long actor=c.requestId()>0?num(a.get("createBy")):AuthContext.userId();Map<String,Object>r=s.one("SELECT * FROM "+(c.moduleId()==JOB?"oa_recruit_job":"oa_recruit_talent")+" WHERE id=? AND tenant_id=1 FOR UPDATE",id);
        if(num(r.get("revision"))!=num(p.get("revision")))throw new ApiException(409,"审批依据已变更，请撤回并重新申请");
        switch(c.actionCode()){
            case "demand"->{s.jdbc().update("UPDATE oa_recruit_job SET demand_status='approved',status='recruiting',revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",id);s.jdbc().update("UPDATE oa_recruit_job SET version_no=version_no+1 WHERE id=?",id);s.version(id,text(p.get("reason")),actor);}
            case "change"->{Map<String,Object>f=s.object(p.get("fields"));long hired=s.jdbc().queryForObject("SELECT COUNT(*) FROM oa_recruit_application WHERE job_id=? AND actual_entry_date IS NOT NULL AND stage IN ('entered','guarantee','completed')",Long.class,id);if(num(f.get("headcount"))<hired)throw new ApiException(409,"审批期间入职人数已变化，请重新申请");s.saveJobFields(id,f);s.jdbc().update("UPDATE oa_recruit_job SET version_no=version_no+1,status=CASE WHEN status='recruiting' AND headcount<=? THEN 'completed' WHEN status='completed' AND headcount>? THEN 'recruiting' ELSE status END WHERE id=?",hired,hired,id);s.version(id,text(p.get("reason")),actor);}
            case "handover"->{s.user(num(p.get("ownerUserId")));s.jdbc().update("UPDATE oa_recruit_talent SET owner_user_id=?,collaborators_json=?,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",num(p.get("ownerUserId")),s.json(p.get("collaborators")),id);long disputeId=num(p.get("disputeId"));if(disputeId>0){int closed=s.jdbc().update("UPDATE oa_recruit_dispute SET status='closed',result=?,close_time=CURRENT_TIMESTAMP WHERE id=? AND talent_id=? AND status='open'","转交生效："+text(p.get("reason")),disputeId,id);if(closed!=1)throw new ApiException(409,"归属争议状态已变更");}s.eventAs("talent",id,"ownership","负责人："+text(s.user(num(r.get("ownerUserId"))).get("nickName"))+" → "+text(p.get("ownerUserName")),Map.of("disputeId",disputeId),actor);}
            case "merge"->{long target=num(p.get("targetId"));Map<String,Object>t=s.one("SELECT * FROM oa_recruit_talent WHERE id=? AND tenant_id=1 FOR UPDATE",target);if(num(t.get("revision"))!=num(p.get("targetRevision")))throw new ApiException(409,"目标档案已变更，请重新申请");
                if(s.jdbc().queryForObject("SELECT COUNT(*) FROM oa_recruit_application a JOIN oa_recruit_application b ON a.job_id=b.job_id WHERE a.talent_id=? AND b.talent_id=?",Long.class,id,target)>0)throw new ApiException(409,"存在同职位流程，不能覆盖合并");
                s.jdbc().update("UPDATE oa_recruit_application SET talent_id=?,revision=revision+1 WHERE talent_id=?",target,id);s.jdbc().update("UPDATE oa_recruit_task SET talent_id=? WHERE talent_id=?",target,id);
                long version=s.jdbc().queryForObject("SELECT COALESCE(MAX(version_no),0) FROM oa_recruit_resume WHERE talent_id=?",Long.class,target);for(Map<String,Object>resume:s.rows("SELECT id FROM oa_recruit_resume WHERE talent_id=? ORDER BY version_no",id))s.jdbc().update("UPDATE oa_recruit_resume SET talent_id=?,version_no=? WHERE id=?",target,++version,resume.get("id"));
                LinkedHashSet<Object>tags=new LinkedHashSet<>(s.array(t.get("tagsJson")));tags.addAll(s.array(r.get("tagsJson")));if(tags.size()>30)throw new ApiException(400,"合并后的标签超过30个，请先整理标签");
                s.jdbc().update("UPDATE oa_recruit_talent SET tags_json=?,phone=CASE WHEN phone IS NULL OR phone='' THEN ? ELSE phone END,email=CASE WHEN email IS NULL OR email='' THEN ? ELSE email END,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",s.json(tags),r.get("phone"),r.get("email"),target);
                s.jdbc().update("UPDATE oa_recruit_talent SET status='merged',merged_into=?,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",target,id);s.eventAs("talent",target,"merge","合并档案 #"+id+"；来源档案及历史日志保留",Map.of("sourceId",id),actor);}
            default->throw new ApiException(400,"不支持的招聘业务动作");
        }
        if(c.requestId()>0)s.jdbc().update("UPDATE oa_recruit_approval SET status='approved',update_time=CURRENT_TIMESTAMP WHERE workflow_request_id=?",c.requestId());
        s.eventAs(type,id,c.actionCode(),text(p.get("reason")),Map.of("requestId",c.requestId(),"result","completed"),actor);return Map.of("id",id,"status","completed");
    }
    @Transactional public void withdraw(String type,long id,Map<String,Object>b){
        Map<String,Object>r="job".equals(type)?s.job(id,true):s.talent(id,true);s.revision(r,b);String reason=required(b.get("reason"),2000,"撤回原因");
        Map<String,Object>a=s.one("SELECT * FROM oa_recruit_approval WHERE entity_type=? AND entity_id=? AND status='pending' FOR UPDATE",type,id);
        if(num(a.get("createBy"))!=AuthContext.userId())throw new ApiException(403,"只有发起人可以撤回");
        Map<String,Object>w=s.one("SELECT * FROM oa_workflow_request WHERE id=? FOR UPDATE",a.get("workflowRequestId"));if(!"running".equals(w.get("status")))throw new ApiException(409,"流程已结束，不能撤回");
        String instance=text(w.get("processInstanceId"));s.jdbc().update("UPDATE oa_process_task SET status='cancelled',complete_time=CURRENT_TIMESTAMP WHERE instance_id=? AND status='todo'",instance);
        s.jdbc().update("UPDATE oa_todo SET status='cancelled' WHERE process_instance_id=? AND status='todo'",instance);
        s.jdbc().update("UPDATE oa_process_instance SET status='cancelled',end_time=CURRENT_TIMESTAMP WHERE id=? AND status='running'",instance);
        s.jdbc().update("UPDATE oa_workflow_request SET status='withdrawn',complete_time=CURRENT_TIMESTAMP,update_time=CURRENT_TIMESTAMP WHERE id=?",a.get("workflowRequestId"));
        s.jdbc().update("UPDATE oa_recruit_approval SET status='withdrawn',update_time=CURRENT_TIMESTAMP WHERE id=?",a.get("id"));
        if("demand".equals(a.get("actionCode")))s.jdbc().update("UPDATE oa_recruit_job SET demand_status='withdrawn' WHERE id=?",id);s.event(type,id,"withdraw",reason,Map.of("requestId",a.get("workflowRequestId")));
    }
}
