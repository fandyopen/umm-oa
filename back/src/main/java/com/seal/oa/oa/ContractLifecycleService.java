package com.seal.oa.oa;
import com.seal.oa.workflow.WorkflowApprovalContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.JdbcMaps;
import com.seal.oa.system.SystemService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Contract business retirement preserves financial facts and the original signed versions. */
@Service
public class ContractLifecycleService {
    private final JdbcTemplate jdbc;
    private final CrmContractService contracts;
    private final SystemService system;
    private final ObjectMapper json;
    public ContractLifecycleService(JdbcTemplate jdbc, CrmContractService contracts, SystemService system, ObjectMapper json) {
        this.jdbc=jdbc; this.contracts=contracts; this.system=system; this.json=json;
    }
    private long num(Object v) { try { return Long.parseLong(String.valueOf(v)); } catch(Exception e) { return 0; } }
    private String text(Object v) { return v==null?"":String.valueOf(v); }
    private String reason(Map<String,Object> body) {
        String value=text(body.get("reason")).trim();
        if(value.isEmpty()||value.length()>2000)throw new ApiException(400,"请填写操作原因，最多2000字");
        return value;
    }
    private Map<String,Object> row(long id) {
        var rows=JdbcMaps.camelCase(jdbc.queryForList("SELECT * FROM oa_crm_contract WHERE id=? FOR UPDATE",id));
        if(rows.isEmpty())throw new ApiException(404,"合同不存在");
        return rows.get(0);
    }
    private Map<String,Object> owned(long id,Map<String,Object> body) {
        var row=row(id);
        if(!Boolean.TRUE.equals(contracts.detail(id).get("canEditCustomer")))throw new ApiException(403,"无权操作该客户的合同");
        if(num(row.get("deleted"))!=0)throw new ApiException(409,"合同已删除，仅可查看历史");
        if(!body.containsKey("revision")||num(body.get("revision"))!=num(row.get("revision")))throw new ApiException(409,"合同已变更，请刷新后重试");
        return row;
    }
    private boolean running(long id) { return num(jdbc.queryForObject("SELECT COUNT(*) FROM oa_workflow_request WHERE id=? AND status='running'",Long.class,id))>0; }
    private void idle(Map<String,Object> row) {
        if("pending".equals(row.get("status"))||running(num(row.get("lifecycleRequestId")))
                ||"running".equals(contracts.signingWorkflow(num(row.get("id"))).get("status")))
            throw new ApiException(409,"合同仍在审批，请先撤回当前审批");
    }
    private void allowed(Map<String,Object> row,String action) {
        if(num(row.get("deleted"))!=0)throw new ApiException(409,"合同已删除，仅可查看历史");
        String state=text(row.get("status"));
        if("void".equals(action)) {
            if(num(row.get("effectiveVersion"))>0)throw new ApiException(409,"合同已有生效版本，请使用终止合同");
            if(!Set.of("draft","rejected","approved").contains(state))throw new ApiException(409,"当前状态不能作废合同");
            if(count("SELECT COUNT(*) FROM oa_crm_contract_receipt WHERE contract_id=?",row.get("id"))>0
                    ||count("SELECT COUNT(*) FROM oa_finance_record WHERE contract_id=? AND status NOT IN ('void','rejected','draft')",row.get("id"))>0
                    ||count("SELECT COUNT(*) FROM oa_recruit_job WHERE contract_id=? AND status NOT IN ('closed','deleted')",row.get("id"))>0)
                throw new ApiException(409,"合同仍有关联履约或财务业务，请先处理关联记录");
        } else if("terminate".equals(action)) {
            if(num(row.get("effectiveVersion"))==0||!Set.of("active","draft","rejected","approved").contains(state))
                throw new ApiException(409,"仅有生效版本且未结案的合同可终止");
            // No new delivery after termination. Existing guarantees must finish before final closure.
            if(count("SELECT COUNT(*) FROM oa_recruit_job WHERE contract_id=? AND status NOT IN ('closed','completed','deleted')",row.get("id"))>0)
                throw new ApiException(409,"请先关闭或完成关联职位，再终止合同");
        } else throw new ApiException(400,"无效合同操作");
    }
    private long count(String sql,Object id) { return jdbc.queryForObject(sql,Long.class,id); }
    public Map<String,Object> validate(long id,String action,Map<String,Object> body,long requestId) {
        Map<String,Object> row;
        if(requestId==0) { row=owned(id,body); idle(row); reason(body); }
        else {
            row=row(id);
            if(num(row.get("lifecycleRequestId"))!=requestId||num(row.get("revision"))!=num(body.get("revision"))||!running(requestId))
                throw new ApiException(409,"该合同审批已撤回或合同已变化");
        }
        allowed(row,action);
        return row;
    }
    @Transactional
    public Map<String,Object> submitted(long id,String action,Map<String,Object> body,long requestId) {
        var row=validate(id,action,body,0);
        jdbc.update("UPDATE oa_crm_contract SET lifecycle_request_id=?,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",requestId,id);
        var frozen=new LinkedHashMap<String,Object>(body);
        frozen.put("contractId",id);frozen.put("revision",num(row.get("revision"))+1);
        frozen.put("lifecycleSubmitUserId",AuthContext.userId());frozen.put("lifecycleSubmitUserName",AuthContext.username());
        event(row,action+"_submit",reason(body),AuthContext.username());
        return frozen;
    }
    public void validateDecision(long id,String action,Map<String,Object> body,long requestId,String instance,String task,Long operator,boolean pass,String comment) {
        validate(id,action,body,requestId);
        var requests=jdbc.queryForList("SELECT * FROM oa_workflow_request WHERE id=? AND status='running' AND process_instance_id=? AND process_task_id=?",requestId,instance,task);
        if(requests.isEmpty())throw new ApiException(409,"审批节点已变化");
        approver(body,instance,task,operator);
        if(!pass&&text(comment).trim().isEmpty())throw new ApiException(400,"退回时请填写原因");
    }
    private void approver(Map<String,Object> body,String instance,String task,Long operator) {
        var users=jdbc.queryForList("SELECT t.assignee_id FROM oa_process_task t JOIN sys_user u ON u.user_id=t.assignee_id WHERE t.instance_id=? AND t.id=? AND u.status='0' AND u.del_flag='0'",Long.class,instance,task);
        if(users.isEmpty()||users.get(0)==num(body.get("lifecycleSubmitUserId"))||!system.hasModuleAction(users.get(0),341202L,"approve"))
            throw new ApiException(400,"审批人须具有合同审批权限，且不能为申请人本人");
        if(operator!=null&&((operator==num(body.get("lifecycleSubmitUserId"))&&!WorkflowApprovalContext.allowsSelfApproval(operator))
                ||(!operator.equals(users.get(0))&&!WorkflowApprovalContext.permits(instance,task,operator,users.get(0)))))
            throw new ApiException(403,"仅当前节点审批人或授权代审批人可办理，不能审批本人申请");
    }
    @Transactional
    public void state(long id,String action,Map<String,Object> body,long requestId,String instance,String status,String task) {
        if("审批未通过".equals(status)) {
            var row=row(id);
            if(num(row.get("lifecycleRequestId"))!=requestId)throw new ApiException(409,"审批已撤回");
            jdbc.update("UPDATE oa_crm_contract SET lifecycle_request_id=NULL,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",id);
            event(row,action+"_rejected",reason(body),text(body.get("lifecycleSubmitUserName")));
        } else { validate(id,action,body,requestId); approver(body,instance,task,null); }
    }
    @Transactional
    public Map<String,Object> execute(long id,String action,Map<String,Object> body,long requestId) {
        var row=validate(id,action,body,requestId);
        String status="void".equals(action)?"void":"terminated";
        jdbc.update("UPDATE oa_crm_contract SET status=?,lifecycle_request_id=NULL,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",status,id);
        event(row,action,reason(body),requestId>0?text(body.get("lifecycleSubmitUserName")):AuthContext.username());
        contracts.refreshLifecycle(id,num(row.get("customerId")));
        return Map.of("contractId",id,"status",status);
    }
    @Transactional
    public Map<String,Object> withdraw(long id,Map<String,Object> body) {
        var row=owned(id,body); String why=reason(body);
        var signing=contracts.signingWorkflow(id); var submission=contracts.workflow(id);
        long request=num(row.get("lifecycleRequestId")); String kind="lifecycle";
        if(!running(request)) { request=num(signing.get("requestId"));kind="sign"; }
        if(!running(request)) { request=num(submission.get("requestId"));kind="submit"; }
        if(running(request)) {
            var req=jdbc.queryForMap("SELECT * FROM oa_workflow_request WHERE id=? FOR UPDATE",request);
            String instance=text(req.get("process_instance_id"));
            jdbc.update("UPDATE oa_process_instance SET status='cancelled',end_time=CURRENT_TIMESTAMP WHERE id=? AND status='running'",instance);
            jdbc.update("UPDATE oa_process_task SET status='cancelled',complete_time=CURRENT_TIMESTAMP WHERE instance_id=? AND status='todo'",instance);
            jdbc.update("UPDATE oa_todo SET status='cancelled' WHERE process_instance_id=? AND status='todo'",instance);
            jdbc.update("UPDATE oa_workflow_request SET status='withdrawn',complete_time=CURRENT_TIMESTAMP,update_time=CURRENT_TIMESTAMP WHERE id=?",request);
        } else if("pending".equals(row.get("status"))&&submission.isEmpty()) kind="submit";
        else throw new ApiException(409,"合同没有可撤回的审批");
        if("submit".equals(kind)) {
            jdbc.update("UPDATE oa_crm_contract_version SET status='withdrawn' WHERE contract_id=? AND version_no=?",id,row.get("versionNo"));
            jdbc.update("UPDATE oa_todo SET status='cancelled' WHERE business_type='crmContractApproval' AND business_id=? AND status='todo'",id);
            jdbc.update("UPDATE oa_crm_contract SET status='draft',version_no=version_no+1 WHERE id=?",id);
        }
        jdbc.update("UPDATE oa_crm_contract SET lifecycle_request_id=NULL,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",id);
        event(row,"withdraw",why,AuthContext.username());
        return contracts.detail(id);
    }
    @Transactional
    public Map<String,Object> delete(long id,Map<String,Object> body) {
        var row=owned(id,body); idle(row);
        if(!Set.of("draft","completed","void").contains(text(row.get("status"))))throw new ApiException(409,"仅草稿、已结案和已作废合同可删除");
        if(num(row.get("effectiveVersion"))>0&&!"completed".equals(row.get("status")))throw new ApiException(409,"合同仍有生效版本，请终止并结案后删除");
        if(count("SELECT COUNT(*) FROM oa_finance_record WHERE contract_id=? AND status IN ('approving','approved')",id)>0)
            throw new ApiException(409,"仍有在途财务记录，请完成或作废后删除");
        boolean physical="draft".equals(row.get("status"))
                &&num(row.get("effectiveVersion"))==0
                &&count("SELECT COUNT(*) FROM oa_crm_contract_version WHERE contract_id=?",id)==0
                &&count("SELECT COUNT(*) FROM oa_crm_contract_receipt WHERE contract_id=?",id)==0
                &&count("SELECT COUNT(*) FROM oa_crm_contract_reminder_log WHERE contract_id=?",id)==0
                &&count("SELECT COUNT(*) FROM oa_recruit_job WHERE contract_id=?",id)==0
                &&count("SELECT COUNT(*) FROM oa_finance_record WHERE contract_id=?",id)==0
                &&count("SELECT COUNT(*) FROM oa_finance_collection WHERE contract_id=?",id)==0
                &&count("SELECT COUNT(*) FROM oa_commission_attribution WHERE contract_id=?",id)==0
                &&count("SELECT COUNT(*) FROM oa_crm_contract WHERE parent_contract_id=?",id)==0
                &&count("SELECT COUNT(*) FROM oa_crm_contract_history WHERE contract_id=?",id)==0;
        event(row,physical?"delete":"archive","删除合同；"+(physical?"无业务历史，彻底删除":"保留合同及关联业务历史"),AuthContext.username());
        jdbc.update(physical?"DELETE FROM oa_crm_contract WHERE id=?":"UPDATE oa_crm_contract SET deleted=1,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",id);
        if(!physical)contracts.refreshLifecycle(id,num(row.get("customerId")));
        return Map.of("contractId",id,"physical",physical);
    }
    private void event(Map<String,Object> row,String action,String reason,String operator) {
        try { jdbc.update("INSERT INTO oa_crm_contract_history(customer_id,contract_id,action,reason,payload_json,operator_name) VALUES(?,?,?,?,?,?)",
                row.get("customerId"),row.get("id"),action,reason,json.writeValueAsString(row),operator); }
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new ApiException(500,"合同操作记录保存失败");}
    }
}
