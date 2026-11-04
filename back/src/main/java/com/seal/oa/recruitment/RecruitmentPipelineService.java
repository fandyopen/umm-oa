package com.seal.oa.recruitment;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import static com.seal.oa.recruitment.RecruitmentService.*;

@Service
public class RecruitmentPipelineService {
    final RecruitmentService s;
    static final String SELECT="""
            SELECT a.*,j.title AS job_title,j.customer_id,j.owner_user_id,j.collaborators_json,
              j.contract_id,j.status AS job_status,j.demand_status,m.name AS customer_name,
              t.name AS talent_name,u.nick_name AS consultant_name,owner.dept_id AS owner_dept_id,
              latest_interview.status AS interview_status,latest_interview.result AS interview_result,
              active_interview.id AS active_interview_id,active_interview.interview_time AS active_interview_time,
              t.status AS talent_status,j.headcount AS job_headcount,
              (SELECT COUNT(*) FROM oa_recruit_application a WHERE a.talent_id=t.id AND a.tenant_id=1 AND """ + TalentAvailabilityRules.BLOCKING_CONDITION + """
              ) AS talent_blocking_count,
              (SELECT COUNT(*) FROM oa_recruit_approval p WHERE p.entity_type='talent' AND p.status='pending' AND (p.entity_id=t.id OR p.related_id=t.id)) AS talent_pending_count,
              (SELECT COUNT(*) FROM oa_recruit_application h WHERE h.job_id=j.id AND h.actual_entry_date IS NOT NULL AND (h.stage IN ('entered','guarantee','completed') OR (h.stage='exited' AND h.guarantee_status='completed'))) AS job_hired,
              COALESCE(interview_stats.interview_count,0) AS interview_count,COALESCE(interview_stats.scheduled_count,0) AS scheduled_count,COALESCE(interview_stats.pending_result_count,0) AS pending_result_count,
              COALESCE(replacement_stats.replacement_count,0) AS replacement_count,COALESCE(replacement_stats.blocking_count,0) AS blocking_replacement_count,
              COALESCE(exception_stats.open_replacement_count,0) AS open_replacement_count,COALESCE(exception_stats.open_followup_count,0) AS open_followup_count
            FROM oa_recruit_application a JOIN oa_recruit_job j ON j.id=a.job_id
            JOIN oa_merchant m ON m.id=j.customer_id JOIN oa_recruit_talent t ON t.id=a.talent_id
            LEFT JOIN sys_user u ON u.user_id=a.consultant_id LEFT JOIN sys_user owner ON owner.user_id=j.owner_user_id
            LEFT JOIN oa_recruit_interview latest_interview ON latest_interview.id=(
              SELECT i.id FROM oa_recruit_interview i WHERE i.application_id=a.id
              ORDER BY CASE WHEN i.status='cancelled' THEN 1 ELSE 0 END,i.round_no DESC,i.id DESC LIMIT 1)
            LEFT JOIN oa_recruit_interview active_interview ON active_interview.id=(
              SELECT i.id FROM oa_recruit_interview i WHERE i.application_id=a.id AND (i.status='scheduled' OR (i.status='completed' AND i.result='pending'))
              ORDER BY i.round_no,i.id LIMIT 1)
            LEFT JOIN (SELECT application_id,COUNT(*) AS interview_count,SUM(CASE WHEN status='scheduled' THEN 1 ELSE 0 END) AS scheduled_count,SUM(CASE WHEN status='completed' AND result='pending' THEN 1 ELSE 0 END) AS pending_result_count
              FROM oa_recruit_interview GROUP BY application_id) interview_stats ON interview_stats.application_id=a.id
            LEFT JOIN (SELECT x.original_application_id,COUNT(*) AS replacement_count,
              SUM(CASE WHEN x.status='completed' OR replacement_app.stage NOT IN ('rejected','withdrawn','exited','ended') THEN 1 ELSE 0 END) AS blocking_count
              FROM oa_recruit_replacement x JOIN oa_recruit_application replacement_app ON replacement_app.id=x.replacement_application_id
              GROUP BY x.original_application_id) replacement_stats ON replacement_stats.original_application_id=a.id
            LEFT JOIN (SELECT application_id,SUM(CASE WHEN status='open' AND exception_type='guarantee_replacement' THEN 1 ELSE 0 END) AS open_replacement_count,
              SUM(CASE WHEN status='open' AND exception_type<>'guarantee_replacement' THEN 1 ELSE 0 END) AS open_followup_count
              FROM oa_recruit_exception GROUP BY application_id) exception_stats ON exception_stats.application_id=a.id
            """;
    public RecruitmentPipelineService(RecruitmentService s){this.s=s;}
    boolean visible(Map<String,Object>r){return s.visible(r)||num(r.get("consultantId"))==AuthContext.userId()||s.jdbc().queryForObject("SELECT COUNT(*) FROM oa_recruit_exception WHERE application_id=? AND owner_user_id=?",Long.class,r.get("id"),AuthContext.userId())>0;}
    public Map<String,Object> application(long id,boolean lock){if(lock)s.one("SELECT id FROM oa_recruit_application WHERE id=? AND tenant_id=1 FOR UPDATE",id);Map<String,Object>r=s.one(SELECT+" WHERE a.id=? AND a.tenant_id=1",id);if(!visible(r))throw new ApiException(403,"无权访问该招聘流程");return r;}
    public List<Map<String,Object>> list(Map<String,String>q){return s.rows(SELECT+" WHERE a.tenant_id=1 ORDER BY a.id DESC").stream().filter(this::visible)
            .filter(r->contains(r.get("talentName"),q.get("keyword"))||contains(r.get("jobTitle"),q.get("keyword"))||contains(r.get("customerName"),q.get("keyword")))
            .filter(r->filter(r,q,"jobId")&&filter(r,q,"talentId")&&filter(r,q,"customerId")&&filter(r,q,"consultantId")&&filter(r,q,"stage")&&s.dates(r,q)).map(r->{r.remove("guaranteeSnapshotJson");return decorate(r);}).toList();}
    Map<String,Object> decorate(Map<String,Object>r){List<String>warnings=new ArrayList<>();if(r.get("feedbackDueDate")!=null&&Set.of("recommended","feedback").contains(text(r.get("stage")))&&today().isAfter(date(r.get("feedbackDueDate"),"反馈期限"))&&Set.of("pending","need_info").contains(text(r.get("feedbackStatus"))))warnings.add("客户反馈停滞");if(r.get("guaranteeEnd")!=null&&"active".equals(r.get("guaranteeStatus"))){long days=java.time.temporal.ChronoUnit.DAYS.between(today(),date(r.get("guaranteeEnd"),"保证期"));if(days<=7)warnings.add(days<0?"保证期待确认结束":"保证期即将到期");}if("entry".equals(r.get("stage"))&&r.get("expectedEntryDate")!=null&&today().isAfter(date(r.get("expectedEntryDate"),"预计入职")))warnings.add("入职延期");r.put("warnings",warnings);r.put("availableOperations",RecruitmentPipelineRules.operations(r));r.remove("collaboratorsJson");return r;}
    public Map<String,Object> detail(long id){Map<String,Object>r=decorate(application(id,false));r.put("events",s.events("application",id));r.put("guaranteeHandlings",s.rows("SELECT h.*,u.nick_name AS operator_name FROM oa_recruit_guarantee_handling h LEFT JOIN sys_user u ON u.user_id=h.operator_id WHERE h.application_id=? ORDER BY h.id DESC",id));r.put("replacements",s.rows("SELECT x.*,a.stage,t.name AS talent_name FROM oa_recruit_replacement x JOIN oa_recruit_application a ON a.id=x.replacement_application_id JOIN oa_recruit_talent t ON t.id=a.talent_id WHERE x.original_application_id=? ORDER BY x.id DESC",id));r.put("replacementFor",s.rows("SELECT original_application_id FROM oa_recruit_replacement WHERE replacement_application_id=?",id).stream().findFirst().orElse(Map.of()).get("originalApplicationId"));r.put("interviews",interviews(r));r.put("exceptions",s.rows("SELECT e.*,u.nick_name AS owner_name FROM oa_recruit_exception e LEFT JOIN sys_user u ON u.user_id=e.owner_user_id WHERE application_id=? ORDER BY e.id DESC",id));r.put("attachments",s.rows("SELECT id,original_name,content_type,file_size,create_time FROM oa_recruit_attachment WHERE application_id=? ORDER BY id DESC",id));Map<String,Object>frozen=s.object(r.remove("guaranteeSnapshotJson"));List<Map<String,Object>>terms=frozen.isEmpty()?contractTerms(num(r.get("contractId"))):List.of(s.object(frozen.get("term")));r.put("guaranteeTerms",terms.stream().map(t->Map.of("key",text(t.get("key")),"title",text(t.get("title")),"guaranteeDays",num(t.get("guaranteeDays")))).toList());return r;}
    public List<Map<String,Object>> contractTerms(long contract){if(contract==0)return List.of();List<Map<String,Object>>versions=s.rows("SELECT v.snapshot_json FROM oa_crm_contract c JOIN oa_crm_contract_version v ON v.contract_id=c.id AND v.version_no=c.effective_version WHERE c.id=? AND c.effective_version>0",contract);if(versions.isEmpty())return List.of();Map<String,Object>snapshot=s.object(versions.get(0).get("snapshotJson"));return s.array(snapshot.get("feeTerms")).stream().map(s::object).toList();}
    @Transactional public Map<String,Object> create(Map<String,Object>b){long job=num(b.get("jobId")),talent=num(b.get("talentId"));Map<String,Object>j=s.job(job,true),t=s.talent(talent,true);s.unlocked("talent",talent);openJob(j);TalentAvailabilityRules.requireRecommendable(t);if(s.jdbc().queryForObject("SELECT COUNT(*) FROM oa_recruit_application WHERE job_id=? AND talent_id=?",Long.class,job,talent)>0)throw new ApiException(409,"该候选人已关联此职位，请在原记录继续办理");long consultant=num(b.getOrDefault("consultantId",AuthContext.userId()));s.user(consultant);
        LocalDate submitted=today();long id=s.insert("INSERT INTO oa_recruit_application (job_id,talent_id,consultant_id,recommendation,stage,submitted_date,feedback_due_date,feedback_status) VALUES (?,?,?,?,'recommended',?,?,'pending')",job,talent,consultant,required(b.get("recommendation"),2000,"推荐说明"),submitted,submitted.plusDays(num(j.get("feedbackDays"))));s.event("application",id,"create","创建并提交客户推荐",Map.of());syncJob(job);return detail(id);}
    @Transactional public Map<String,Object> replacement(long id,Map<String,Object>b){
        Map<String,Object>original=application(id,true);s.revision(original,b);requireOperation(original,"replacement");stage(original,"exited");if(!"replacement".equals(original.get("guaranteeStatus")))throw new ApiException(409,"请先登记保证期替换事项");
        if(s.jdbc().queryForObject("SELECT COUNT(*) FROM oa_recruit_exception WHERE application_id=? AND exception_type='guarantee_replacement' AND status='open'",Long.class,id)==0)throw new ApiException(409,"该替换事项已结束");
        if(s.jdbc().queryForObject("SELECT COUNT(*) FROM oa_recruit_replacement x JOIN oa_recruit_application a ON a.id=x.replacement_application_id WHERE x.original_application_id=? AND (x.status='completed' OR a.stage NOT IN ('rejected','withdrawn','exited','ended'))",Long.class,id)>0)throw new ApiException(409,"已有进行中或已完成的替换，请先处理原替换流程");
        Map<String,Object>fields=new LinkedHashMap<>(b);fields.put("jobId",original.get("jobId"));Map<String,Object>created=create(fields);long replacement=num(created.get("id"));
        s.insert("INSERT INTO oa_recruit_replacement (original_application_id,replacement_application_id,create_by) VALUES (?,?,?)",id,replacement,AuthContext.userId());
        s.jdbc().update("UPDATE oa_recruit_application SET revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",id);s.event("application",id,"replacement","关联替换招聘 #"+replacement,Map.of("replacementApplicationId",replacement));s.event("application",replacement,"replacement","替换原入职记录 #"+id,Map.of("originalApplicationId",id));return detail(replacement);
    }
    void openJob(Map<String,Object>j){if(!"approved".equals(j.get("demandStatus"))||!"recruiting".equals(j.get("status")))throw new ApiException(409,"职位须完成需求确认且处于招聘中");if(num(j.get("hired"))>=num(j.get("headcount")))throw new ApiException(409,"职位名额已满");}
    void stage(Map<String,Object>r,String...values){if(!Set.of(values).contains(text(r.get("stage"))))throw new ApiException(409,"当前招聘阶段不允许此操作，请刷新");}
    void requireOperation(Map<String,Object>r,String operation){if(!RecruitmentPipelineRules.operations(r).contains(operation))throw new ApiException(409,"当前阶段或业务条件不允许此操作，请刷新后重试");}
    void attachment(long app,Object value){long id=num(value);if(id>0&&s.jdbc().queryForObject("SELECT COUNT(*) FROM oa_recruit_attachment WHERE id=? AND application_id=?",Long.class,id,app)==0)throw new ApiException(400,"附件不属于当前招聘流程");}
    @Transactional public Map<String,Object> action(long id,String action,Map<String,Object>b){Map<String,Object>r=application(id,true);s.revision(r,b);s.one("SELECT id FROM oa_recruit_job WHERE id=? FOR UPDATE",r.get("jobId"));Map<String,Object>j=s.one(JOB_SELECT+" WHERE j.id=? AND j.tenant_id=1",r.get("jobId"));String reason=required(b.get("reason"),2000,"操作说明或原因");
        switch(action){
            case "recommend"->{String operation=text(b.getOrDefault("operation","submit"));if(!Set.of("submit","withdraw").contains(operation))throw new ApiException(400,"推荐操作无效");if("withdraw".equals(operation)){requireOperation(r,"withdrawRecommendation");stage(r,"recommended","feedback");if(s.jdbc().queryForObject("SELECT COUNT(*) FROM oa_recruit_interview WHERE application_id=?",Long.class,id)>0)throw new ApiException(409,"已安排面试，请通过异常处理登记撤回");s.jdbc().update("UPDATE oa_recruit_application SET stage='withdrawn' WHERE id=?",id);}else{requireOperation(r,"recommend");stage(r,"withdrawn");openJob(j);Map<String,Object>t=s.talent(num(r.get("talentId")),true);s.unlocked("talent",num(t.get("id")));TalentAvailabilityRules.requireRecommendable(t);s.jdbc().update("UPDATE oa_recruit_application SET stage='recommended',submitted_date=?,feedback_due_date=?,feedback_status='pending',recommendation=? WHERE id=?",today(),today().plusDays(num(j.get("feedbackDays"))),reason,id);}}
            case "feedback"->{requireOperation(r,"feedback");stage(r,"recommended","feedback","interview");String status=required(b.get("status"),32,"反馈结果");if(!Set.of("pending","interested","rejected","need_info").contains(status))throw new ApiException(400,"反馈状态无效");LocalDate due=text(b.get("dueDate")).isBlank()?today().plusDays(num(j.get("feedbackDays"))):date(b.get("dueDate"),"反馈期限");if(due.isBefore(today()))throw new ApiException(400,"反馈期限不能早于今天");if("rejected".equals(status))cancelInterviews(id);String next="interview".equals(r.get("stage"))&&"interested".equals(status)?"interview":"feedback";s.jdbc().update("UPDATE oa_recruit_application SET stage=?,feedback_status=?,feedback_due_date=? WHERE id=?",next,status,due,id);}
            case "offer"->offer(id,r,b,reason);
            case "entry"->entry(id,r,j,b,reason);
            case "guarantee"->guarantee(id,r,b,reason);
            case "exception"->exception(id,r,b,reason);
            default->throw new ApiException(400,"不支持的招聘操作");
        }
        s.jdbc().update("UPDATE oa_recruit_application SET revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",id);
        s.event("application",id,action,action+" / "+text(b.getOrDefault("operation",b.getOrDefault("status","登记")))+"："+reason,safeEvent(b));syncJob(num(r.get("jobId")));return detail(id);
    }
    Map<String,Object>safeEvent(Map<String,Object>b){Map<String,Object>p=new LinkedHashMap<>(b);p.remove("phone");p.remove("email");return p;}
    void offer(long id,Map<String,Object>r,Map<String,Object>b,String reason){String op=text(b.getOrDefault("operation","save"));
        String operation=switch(op){case "save"->"offer";case "issue"->"issueOffer";case "accept"->"acceptOffer";case "reject"->"rejectOffer";case "withdraw"->"withdrawOffer";default->throw new ApiException(400,"Offer操作无效");};requireOperation(r,operation);
        if("save".equals(op)){stage(r,"interview","offer");if(r.get("offerStatus")!=null&&!"draft".equals(r.get("offerStatus")))throw new ApiException(409,"已发放Offer不能直接改写，请撤回后登记");List<Map<String,Object>> completed=s.rows("SELECT result FROM oa_recruit_interview WHERE application_id=? AND status='completed' ORDER BY round_no DESC,id DESC",id);if(completed.isEmpty()||!"pass".equals(completed.get(0).get("result"))||s.jdbc().queryForObject("SELECT COUNT(*) FROM oa_recruit_interview WHERE application_id=? AND status='scheduled'",Long.class,id)>0)throw new ApiException(409,"请完成面试并录入通过结果");java.math.BigDecimal salary=money(b.get("salary"));if(salary.signum()<=0)throw new ApiException(400,"Offer薪资必须大于0");attachment(id,b.get("attachmentId"));s.jdbc().update("UPDATE oa_recruit_application SET stage='offer',offer_status='draft',offer_salary=?,offer_attachment=? WHERE id=?",salary,text(b.get("attachmentId")),id);return;}
        stage(r,"offer");String status=text(r.get("offerStatus"));switch(op){
            case "issue"->{if(!"draft".equals(status))throw new ApiException(409,"仅草稿Offer可登记发放");s.jdbc().update("UPDATE oa_recruit_application SET offer_status='issued',offer_date=? WHERE id=?",today(),id);}
            case "accept"->{if(!"issued".equals(status))throw new ApiException(409,"仅已发放Offer可接受");LocalDate expected=date(b.get("expectedEntryDate"),"预计入职日期");if(expected.isBefore(today()))throw new ApiException(400,"预计入职日期不能早于今天");s.jdbc().update("UPDATE oa_recruit_application SET stage='entry',offer_status='accepted',expected_entry_date=? WHERE id=?",expected,id);}
            case "reject","withdraw"->{if(!Set.of("draft","issued").contains(status))throw new ApiException(409,"当前Offer不能拒绝或撤回");s.jdbc().update("UPDATE oa_recruit_application SET stage=?,offer_status=? WHERE id=?","reject".equals(op)?"rejected":"interview","reject".equals(op)?"rejected":null,id);if(!"draft".equals(status))followup(id,"offer_"+op,b,reason,num(r.get("consultantId")),false);}
            default->throw new ApiException(400,"Offer操作无效");}
    }
    void entry(long id,Map<String,Object>r,Map<String,Object>j,Map<String,Object>b,String reason){String op=text(b.getOrDefault("operation","confirm"));if(!Set.of("expected","correct","confirm").contains(op))throw new ApiException(400,"入职操作无效");requireOperation(r,"expected".equals(op)?"expectedEntry":"correct".equals(op)?"correctEntry":"entry");
        if("expected".equals(op)){stage(r,"entry");LocalDate expected=date(b.get("expectedEntryDate"),"预计入职日期");if(expected.isBefore(today()))throw new ApiException(400,"预计入职日期不能早于今天");s.jdbc().update("UPDATE oa_recruit_application SET expected_entry_date=? WHERE id=?",expected,id);return;}
        if("correct".equals(op)){stage(r,"guarantee","completed");if(!"active".equals(r.get("guaranteeStatus")))throw new ApiException(409,"保证期已关闭不能直接纠正，请登记异常");}
        else{stage(r,"entry");openJob(j);}
        LocalDate actual=date(b.get("actualEntryDate"),"实际入职日期");if(actual.isAfter(today())||r.get("offerDate")!=null&&actual.isBefore(date(r.get("offerDate"),"Offer日期")))throw new ApiException(400,"实际入职日期不能晚于今天或早于Offer发放日期");
        Map<String,Object>talent=s.talent(num(r.get("talentId")),true);s.unlocked("talent",num(talent.get("id")));if("merged".equals(talent.get("status")))throw new ApiException(409,"候选人已合并，请刷新流程");
        if(s.jdbc().queryForObject("SELECT COUNT(*) FROM oa_recruit_application WHERE talent_id=? AND id<>? AND actual_entry_date IS NOT NULL AND stage IN ('entered','guarantee','completed')",Long.class,r.get("talentId"),id)>0)throw new ApiException(409,"该候选人已有有效入职记录，请先核实原入职状态");
        attachment(id,b.get("attachmentId"));if(num(b.get("attachmentId"))==0)throw new ApiException(400,"请上传并选择入职凭证");
        Map<String,Object>frozen=s.object(r.get("guaranteeSnapshotJson"));List<Map<String,Object>>terms="correct".equals(op)&&!frozen.isEmpty()?List.of(s.object(frozen.get("term"))):contractTerms(num(j.get("contractId")));if(terms.isEmpty())throw new ApiException(400,"职位须关联有效签署合同及收费条款，才能按合同生成保证期");String key=text(b.get("termKey"));Map<String,Object>term=terms.stream().filter(t->key.equals(text(t.get("key")))).findFirst().orElseThrow(()->new ApiException(400,"请选择合同保证期对应条款"));int days=integer(term.get("guaranteeDays"),0,3650,"合同保证期");
        if(frozen.isEmpty()){Map<String,Object>contract=s.one("SELECT id,effective_version FROM oa_crm_contract WHERE id=?",j.get("contractId"));frozen=Map.of("contractId",contract.get("id"),"versionNo",contract.get("effectiveVersion"),"term",term);}
        LocalDate end=actual.plusDays(days);s.jdbc().update("UPDATE oa_recruit_application SET actual_entry_date=?,entry_attachment=?,guarantee_days=?,guarantee_term_key=?,guarantee_snapshot_json=?,guarantee_start=?,guarantee_end=?,guarantee_status='active',stage='guarantee' WHERE id=?",actual,text(b.get("attachmentId")),days,key,s.json(frozen),actual,end,id);
        for(Map<String,Object>link:s.rows("SELECT * FROM oa_recruit_replacement WHERE replacement_application_id=? AND status='pending' FOR UPDATE",id)){s.jdbc().update("UPDATE oa_recruit_replacement SET status='completed',complete_time=CURRENT_TIMESTAMP WHERE id=?",link.get("id"));s.jdbc().update("UPDATE oa_recruit_exception SET status='closed',result=?,close_time=CURRENT_TIMESTAMP WHERE application_id=? AND exception_type='guarantee_replacement' AND status='open'","替换候选人已确认入职，招聘记录 #"+id,link.get("originalApplicationId"));s.event("application",num(link.get("originalApplicationId")),"replacement_completed","替换候选人已入职，原替换异常自动结案",Map.of("replacementApplicationId",id));}
        s.event("job",num(j.get("id")),"entry",("correct".equals(op)?"纠正":"确认")+"候选人入职："+r.get("talentName"),Map.of("applicationId",id,"guaranteeDays",days));
    }
    void guarantee(long id,Map<String,Object>r,Map<String,Object>b,String reason){if("completed".equals(r.get("stage"))){departureAfterGuarantee(id,r,b,reason);return;}stage(r,"guarantee");if(!"active".equals(r.get("guaranteeStatus")))throw new ApiException(409,"保证期已关闭");String op=text(b.get("operation"));String operation=switch(op){case "complete"->"completeGuarantee";case "exit"->"guaranteeExit";case "replacement"->"guaranteeReplacement";case "refund"->"guaranteeRefund";default->throw new ApiException(400,"保证期操作无效");};requireOperation(r,operation);if("complete".equals(op)){if(today().isBefore(date(r.get("guaranteeEnd"),"保证期结束")))throw new ApiException(409,"保证期尚未结束");s.jdbc().update("UPDATE oa_recruit_application SET stage='completed',guarantee_status='completed' WHERE id=?",id);}else if(Set.of("exit","refund","replacement").contains(op)){LocalDate exit=date(b.get("exitDate"),"退出日期");if(exit.isBefore(date(r.get("actualEntryDate"),"入职日期"))||exit.isAfter(today()))throw new ApiException(400,"退出日期须在实际入职至今天之间");java.math.BigDecimal amount=money(b.get("amount"));if("refund".equals(op)){if(amount.signum()<=0||num(b.get("attachmentId"))==0)throw new ApiException(400,"退费登记须填写正数金额并选择凭证");attachment(id,b.get("attachmentId"));}s.insert("INSERT INTO oa_recruit_guarantee_handling (application_id,operation,event_date,amount,attachment_id,reason,operator_id) VALUES (?,?,?,?,?,?,?)",id,op,exit,"refund".equals(op)?amount:java.math.BigDecimal.ZERO,"refund".equals(op)?num(b.get("attachmentId")):null,reason,AuthContext.userId());s.jdbc().update("UPDATE oa_recruit_application SET stage='exited',guarantee_status=? WHERE id=?",op,id);followup(id,"guarantee_"+op,b,reason,num(r.get("consultantId")),"replacement".equals(op));}else throw new ApiException(400,"保证期操作无效");}
    void departureAfterGuarantee(long id,Map<String,Object>r,Map<String,Object>b,String reason){
        if(!"exit".equals(b.get("operation")))throw new ApiException(409,"保证期已结案，仅可登记后续离职");
        requireOperation(r,"postGuaranteeExit");
        LocalDate exit=date(b.get("exitDate"),"离职日期");
        if(exit.isBefore(date(r.get("actualEntryDate"),"入职日期"))||exit.isBefore(date(r.get("guaranteeEnd"),"保证期结束"))||exit.isAfter(today()))throw new ApiException(400,"结案后离职日期须在保证期结束至今天之间");
        s.insert("INSERT INTO oa_recruit_guarantee_handling (application_id,operation,event_date,amount,reason,operator_id) VALUES (?,'exit',?,0,?,?)",id,exit,reason,AuthContext.userId());
        // 保留已履约的保证期结案状态和招聘成果，仅释放候选人的在职占用。
        s.jdbc().update("UPDATE oa_recruit_application SET stage='exited' WHERE id=?",id);
        s.event("application",id,"departure","保证期结案后登记离职："+reason,Map.of("exitDate",exit.toString()));
    }

    void newException(long id,String type,String reason,long owner){
        List<Map<String,Object>> existing=s.rows("SELECT id FROM oa_recruit_exception WHERE application_id=? AND exception_type=? AND status='open' FOR UPDATE",id,type);
        if(existing.isEmpty())s.insert("INSERT INTO oa_recruit_exception (application_id,exception_type,content,owner_user_id) VALUES (?,?,?,?)",id,type,reason,owner);
        else s.jdbc().update("UPDATE oa_recruit_exception SET content=?,owner_user_id=? WHERE id=?",reason,owner,existing.get(0).get("id"));
    }
    void followup(long id,String type,Map<String,Object>b,String reason,long defaultOwner,boolean mandatory){
        Object requested=b.getOrDefault("followupRequired",false);
        if(!(requested instanceof Boolean))throw new ApiException(400,"是否需要后续跟进参数无效");
        if(!mandatory&&!Boolean.TRUE.equals(requested))return;
        long owner=num(b.getOrDefault("ownerUserId",defaultOwner));s.user(owner);newException(id,type,reason,owner);
    }
    void exception(long id,Map<String,Object>r,Map<String,Object>b,String reason){
        String op=text(b.getOrDefault("operation","create"));
        if("resolve".equals(op)){
            Map<String,Object>e=s.one("SELECT * FROM oa_recruit_exception WHERE id=? AND application_id=? FOR UPDATE",num(b.get("exceptionId")),id);
            if(!"open".equals(e.get("status")))throw new ApiException(409,"跟进事项已关闭");
            if("guarantee_replacement".equals(e.get("exceptionType")))throw new ApiException(409,"替换事项将在关联候选人实际入职后自动结案");
            s.jdbc().update("UPDATE oa_recruit_exception SET status='closed',result=?,close_time=CURRENT_TIMESTAMP WHERE id=?",reason,e.get("id"));return;
        }
        if("finish".equals(op)){
            requireOperation(r,"finishRecommendation");
            cancelInterviews(id);s.jdbc().update("UPDATE oa_recruit_application SET stage='ended' WHERE id=?",id);return;
        }
        if(!"create".equals(op))throw new ApiException(400,"不支持的跟进操作");
        String type=required(b.get("type"),64,"业务事项");
        String operation=switch(type){case "candidate_withdraw"->"candidateWithdraw";case "customer_cancel"->"customerCancel";case "entry_delay"->"entryDelay";default->throw new ApiException(400,"请使用当前阶段对应的具体业务入口");};
        requireOperation(r,operation);
        if("entry_delay".equals(type)){
            if(!text(b.get("expectedEntryDate")).isBlank()){
                LocalDate expected=date(b.get("expectedEntryDate"),"调整后预计入职日期");
                if(expected.isBefore(today()))throw new ApiException(400,"预计入职日期不能早于今天");
                s.jdbc().update("UPDATE oa_recruit_application SET expected_entry_date=? WHERE id=?",expected,id);
            }
            Map<String,Object>fields=new LinkedHashMap<>(b);fields.putIfAbsent("followupRequired",true);
            followup(id,type,fields,reason,num(r.get("consultantId")),false);
        }else{
            if(r.get("actualEntryDate")!=null)throw new ApiException(409,"入职后退出请在保证期处理登记");
            cancelInterviews(id);
            s.jdbc().update("UPDATE oa_recruit_application SET stage='withdrawn',offer_status=CASE WHEN offer_status IS NULL THEN NULL ELSE 'withdrawn' END WHERE id=?",id);
            followup(id,type,b,reason,num(r.get("consultantId")),false);
        }
    }

    void cancelInterviews(long id){s.jdbc().update("UPDATE oa_recruit_interview SET status='cancelled',update_time=CURRENT_TIMESTAMP WHERE application_id=? AND status='scheduled'",id);}
    Set<String> interviewOperations(Map<String,Object>r,Map<String,Object>i){
        Set<String>out=new LinkedHashSet<>();
        if(!Set.of("feedback","interview").contains(text(r.get("stage"))))return out;
        String status=text(i.get("status"));
        boolean interested="interested".equals(r.get("feedbackStatus"));
        boolean future=LocalDateTime.parse(text(i.get("interviewTime")).replace(' ','T')).isAfter(LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")));
        if("scheduled".equals(status)){
            out.add("cancel");
            if(interested){out.add("result");if(future){out.add("save");if(num(i.get("notified"))==0)out.add("notify");}}
        }else if(interested&&"completed".equals(status)&&"pending".equals(i.get("result")))out.add("result");
        return out;
    }
    List<Map<String,Object>> interviews(Map<String,Object>r){
        return s.rows("SELECT * FROM oa_recruit_interview WHERE application_id=? ORDER BY round_no,id",r.get("id")).stream().map(i->{i.put("availableOperations",interviewOperations(r,i));return i;}).toList();
    }
    @Transactional public Map<String,Object> interview(long id,long interviewId,Map<String,Object>b){
        Map<String,Object>r=application(id,true);s.revision(r,b);stage(r,"feedback","interview");
        String op=text(b.getOrDefault("operation","save"));
        if(!Set.of("save","notify","cancel","result").contains(op))throw new ApiException(400,"面试操作无效");
        String reason=required(b.get("reason"),2000,"面试说明");
        Map<String,Object>existing=null;
        if(interviewId==0){if(!"save".equals(op))throw new ApiException(400,"请先安排面试");requireOperation(r,"interview");}
        else{existing=s.one("SELECT * FROM oa_recruit_interview WHERE id=? AND application_id=? FOR UPDATE",interviewId,id);if(!interviewOperations(r,existing).contains(op))throw new ApiException(409,"当前阶段、面试状态或时间不允许此操作，请刷新后重试");}
        if("save".equals(op)){
            LocalDateTime time;try{time=LocalDateTime.parse(text(b.get("interviewTime")).replace(' ','T'));}catch(Exception e){throw new ApiException(400,"面试时间格式无效");}
            if(time.isBefore(LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))))throw new ApiException(400,"面试时间不能早于当前时间");
            int round=integer(b.get("roundNo"),1,100,"面试轮次");
            String location=required(b.get("location"),500,"面试地点或会议地址"),person=required(b.get("interviewer"),200,"面试官");
            if(s.jdbc().queryForObject("SELECT COUNT(*) FROM oa_recruit_interview WHERE application_id=? AND round_no=? AND status<>'cancelled' AND id<>?",Long.class,id,round,interviewId)>0)throw new ApiException(409,"该轮面试已存在，请编辑或改期");
            if(interviewId==0)s.insert("INSERT INTO oa_recruit_interview (application_id,round_no,interview_time,location,interviewer) VALUES (?,?,?,?,?)",id,round,time,location,person);
            else s.jdbc().update("UPDATE oa_recruit_interview SET round_no=?,interview_time=?,location=?,interviewer=?,notified=0,update_time=CURRENT_TIMESTAMP WHERE id=?",round,time,location,person,interviewId);
        }else switch(op){
            case "notify"->s.jdbc().update("UPDATE oa_recruit_interview SET notified=1,update_time=CURRENT_TIMESTAMP WHERE id=?",interviewId);
            case "cancel"->s.jdbc().update("UPDATE oa_recruit_interview SET status='cancelled',evaluation=?,update_time=CURRENT_TIMESTAMP WHERE id=?",reason,interviewId);
            case "result"->{
                String result=required(b.get("result"),32,"面试结果");
                if(!Set.of("pass","fail","pending").contains(result))throw new ApiException(400,"面试结果无效");
                s.jdbc().update("UPDATE oa_recruit_interview SET status='completed',result=?,evaluation=?,update_time=CURRENT_TIMESTAMP WHERE id=?",result,reason,interviewId);
                if("fail".equals(result)){cancelInterviews(id);s.jdbc().update("UPDATE oa_recruit_application SET stage='rejected' WHERE id=?",id);}
            }
            default->throw new ApiException(400,"面试操作无效");
        }
        String next="cancel".equals(op)?text(r.get("stage")):"interview";
        s.jdbc().update("UPDATE oa_recruit_application SET stage=CASE WHEN stage='rejected' THEN stage ELSE ? END,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",next,id);
        s.event("application",id,"interview_"+op,reason,b);return detail(id);
    }

    void syncJob(long id){Map<String,Object>j=s.one("SELECT * FROM oa_recruit_job WHERE id=? FOR UPDATE",id);long hired=s.jdbc().queryForObject("SELECT COUNT(*) FROM oa_recruit_application WHERE job_id=? AND actual_entry_date IS NOT NULL AND (stage IN ('entered','guarantee','completed') OR (stage='exited' AND guarantee_status='completed'))",Long.class,id);String previous=text(j.get("status"));if("recruiting".equals(previous)&&hired>=num(j.get("headcount")))s.jdbc().update("UPDATE oa_recruit_job SET status='completed',revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",id);else if("completed".equals(previous)&&hired<num(j.get("headcount")))s.jdbc().update("UPDATE oa_recruit_job SET status='recruiting',revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",id);s.syncCustomer(num(j.get("customerId")));}
    public List<Map<String,Object>> warnings(){List<Map<String,Object>>out=new ArrayList<>();if(s.system().hasModuleAccess(AuthContext.userId(),JOB))for(Map<String,Object>j:s.jobs(Map.of()))for(Object warning:s.array(j.get("warnings")))out.add(Map.of("key","job-"+j.get("id")+"-"+warning,"entityType","job","entityId",j.get("id"),"name",j.get("title"),"customerName",j.get("customerName"),"warning",warning));if(s.system().hasModuleAccess(AuthContext.userId(),PIPELINE))for(Map<String,Object>a:list(Map.of()))for(Object warning:s.array(a.get("warnings")))out.add(Map.of("key","application-"+a.get("id")+"-"+warning,"entityType","application","entityId",a.get("id"),"name",a.get("talentName")+" · "+a.get("jobTitle"),"customerName",a.get("customerName"),"warning",warning));return out;}
}
