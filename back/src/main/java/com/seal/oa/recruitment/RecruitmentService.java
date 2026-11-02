package com.seal.oa.recruitment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import com.seal.oa.common.JdbcMaps;
import com.seal.oa.system.DataScopeService;
import com.seal.oa.system.SystemService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class RecruitmentService {
    public static final long JOB = 341301L, TALENT = 341302L, PIPELINE = 341303L;
    final JdbcTemplate jdbc;
    final ObjectMapper mapper;
    final DataScopeService scopes;
    final SystemService system;
    static final String JOB_SELECT = """
            SELECT j.*, m.name AS customer_name, u.nick_name AS owner_name, u.dept_id AS owner_dept_id,
                COALESCE(s.recommendations,0) AS recommendations, COALESCE(s.interviews,0) AS interviews,
                COALESCE(s.offers,0) AS offers, COALESCE(s.hired,0) AS hired
            FROM oa_recruit_job j JOIN oa_merchant m ON m.id=j.customer_id
            LEFT JOIN sys_user u ON u.user_id=j.owner_user_id
            LEFT JOIN (SELECT job_id,
                SUM(CASE WHEN submitted_date IS NOT NULL AND stage <> 'withdrawn' THEN 1 ELSE 0 END) AS recommendations,
                SUM(CASE WHEN stage<>'withdrawn' AND EXISTS (SELECT 1 FROM oa_recruit_interview i WHERE i.application_id=a.id AND i.status<>'cancelled') THEN 1 ELSE 0 END) AS interviews,
                SUM(CASE WHEN stage<>'withdrawn' AND offer_date IS NOT NULL THEN 1 ELSE 0 END) AS offers,
                SUM(CASE WHEN actual_entry_date IS NOT NULL AND stage IN ('entered','guarantee','completed') THEN 1 ELSE 0 END) AS hired
                FROM oa_recruit_application a GROUP BY job_id) s ON s.job_id=j.id
            """;
    static final String TALENT_SELECT = """
            SELECT t.*, u.nick_name AS owner_name, u.dept_id AS owner_dept_id
            FROM oa_recruit_talent t LEFT JOIN sys_user u ON u.user_id=t.owner_user_id
            """;

    public RecruitmentService(JdbcTemplate jdbc, ObjectMapper mapper, DataScopeService scopes, SystemService system) {
        this.jdbc=jdbc; this.mapper=mapper; this.scopes=scopes; this.system=system;
    }

    public JdbcTemplate jdbc(){return jdbc;}
    public SystemService system(){return system;}

    public List<Map<String,Object>> rows(String sql, Object... args) { return JdbcMaps.camelCase(jdbc.queryForList(sql,args)); }
    public Map<String,Object> one(String sql, Object... args) {
        List<Map<String,Object>> rows=rows(sql,args);
        if(rows.isEmpty()) throw new ApiException(404,"记录不存在或已删除");
        return rows.get(0);
    }
    public long insert(String sql, Object... args) {
        GeneratedKeyHolder keys=new GeneratedKeyHolder();
        jdbc.update(c->{PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<args.length;i++) p.setObject(i+1,args[i]); return p;},keys);
        Map<String,Object> generated=keys.getKeys();
        Object id=generated==null?null:generated.entrySet().stream().filter(e->"id".equalsIgnoreCase(e.getKey())).map(Map.Entry::getValue).findFirst().orElse(null);
        if(!(id instanceof Number number)) throw new ApiException(500,"保存失败：未返回业务编号");
        return number.longValue();
    }
    public String json(Object value) { try{return mapper.writeValueAsString(value);}catch(Exception e){throw new ApiException(400,"数据格式无效");} }
    public Map<String,Object> object(Object value) {
        if(value instanceof Map<?,?> m){Map<String,Object> r=new LinkedHashMap<>();m.forEach((k,v)->r.put(String.valueOf(k),v));return r;}
        if(value==null || text(value).isBlank()) return new LinkedHashMap<>();
        try{return mapper.readValue(text(value),new TypeReference<>(){});}catch(Exception e){throw new ApiException(400,"对象格式无效");}
    }
    public List<Object> array(Object value) {
        if(value instanceof List<?> v) return new ArrayList<>(v);
        if(value==null || text(value).isBlank())return new ArrayList<>();
        try{return mapper.readValue(text(value),new TypeReference<>(){});}catch(Exception e){throw new ApiException(400,"列表格式无效");}
    }
    public static String text(Object v){return v==null?"":String.valueOf(v).trim();}
    public static long num(Object v){try{return v==null || text(v).isBlank()?0:Long.parseLong(text(v));}catch(Exception e){throw new ApiException(400,"编号或整数参数无效");}}
    public static String limited(Object v,int max,String label){String s=text(v);if(s.length()>max)throw new ApiException(400,label+"不能超过"+max+"字");return s;}
    public static String required(Object v,int max,String label){String s=limited(v,max,label);if(s.isBlank())throw new ApiException(400,"请填写"+label);return s;}
    public static int integer(Object v,int min,int max,String label){long n=num(v);if(n<min||n>max)throw new ApiException(400,label+"范围为"+min+"至"+max);return (int)n;}
    public static BigDecimal money(Object v){try{BigDecimal n=new BigDecimal(text(v).isBlank()?"0":text(v));if(n.signum()<0 || n.precision()>16 || n.scale()>2)throw new Exception();return n;}catch(Exception e){throw new ApiException(400,"金额须为非负数，最多两位小数");}}
    public static LocalDate today(){return LocalDate.now(ZoneId.of("Asia/Shanghai"));}
    public static LocalDate date(Object v,String label){try{return LocalDate.parse(text(v));}catch(Exception e){throw new ApiException(400,label+"格式须为 YYYY-MM-DD");}}
    public void permission(long module,String action){if(!system.hasModuleAction(AuthContext.userId(),module,action))throw new ApiException(403,"没有该操作权限");}
    public boolean allowed(long module,String action){return system.hasModuleAction(AuthContext.userId(),module,action);}
    public void revision(Map<String,Object> row,Map<String,Object> body){if(!body.containsKey("revision")||num(body.get("revision"))!=num(row.get("revision")))throw new ApiException(409,"数据已更新，请刷新后重试");}
    public void event(String type,long id,String action,String content,Object payload){eventAs(type,id,action,content,payload,AuthContext.userId());}
    public void eventAs(String type,long id,String action,String content,Object payload,long actor){
        jdbc.update("INSERT INTO oa_recruit_event (entity_type,entity_id,event_type,content,payload_json,operator_id) VALUES (?,?,?,?,?,?)",type,id,action,limited(content,4000,"日志"),json(payload),actor);
    }
    public List<Map<String,Object>> events(String type,long id){return rows("""
            SELECT e.id,e.event_type,e.content,e.operator_id,u.nick_name AS operator_name,e.create_time
            FROM oa_recruit_event e LEFT JOIN sys_user u ON u.user_id=e.operator_id
            WHERE e.entity_type=? AND e.entity_id=? ORDER BY e.id DESC
            """,type,id);}
    boolean visible(Map<String,Object> r){return scopes.canAccess(AuthContext.userId(),r.get("ownerDeptId"),r.get("ownerUserId"))||array(r.get("collaboratorsJson")).stream().anyMatch(v->num(v)==AuthContext.userId());}
    Map<String,Object> accessible(Map<String,Object> r){if(!visible(r))throw new ApiException(403,"无权访问该记录");return r;}
    public Map<String,Object> user(long id){return one("SELECT user_id,nick_name,dept_id FROM sys_user WHERE user_id=? AND tenant_id=1 AND status='0' AND del_flag='0'",id);}
    Map<String,Object> customer(long id){Map<String,Object> r=one("""
            SELECT m.*,u.dept_id AS owner_dept_id FROM oa_merchant m LEFT JOIN sys_user u ON u.user_id=m.owner_user_id
            WHERE m.id=? AND m.tenant_id=1 AND m.status='0'
            """,id);if(!scopes.canAccess(AuthContext.userId(),r.get("ownerDeptId"),r.get("ownerUserId")))throw new ApiException(403,"无权访问该客户");
        if(num(r.get("ownerUserId"))==0||"1".equals(text(r.get("publicPool"))))throw new ApiException(400,"请先领取或分配客户");return r;}
    public Map<String,Object> job(long id,boolean lock){
        if(lock)one("SELECT id FROM oa_recruit_job WHERE id=? AND tenant_id=1 FOR UPDATE",id);
        return accessible(one(JOB_SELECT+" WHERE j.id=? AND j.tenant_id=1",id));
    }
    public Map<String,Object> talent(long id,boolean lock){
        if(lock)one("SELECT id FROM oa_recruit_talent WHERE id=? AND tenant_id=1 FOR UPDATE",id);
        Map<String,Object> r=accessible(one(TALENT_SELECT+" WHERE t.id=? AND t.tenant_id=1",id));
        if("deleted".equals(r.get("status")))throw new ApiException(404,"候选人已删除");return r;
    }
    public void unlocked(String type,long id){if(jdbc.queryForObject("SELECT COUNT(*) FROM oa_recruit_approval WHERE status='pending' AND entity_type=? AND (entity_id=? OR related_id=?)",Long.class,type,id,id)>0)throw new ApiException(409,"存在待审批申请，请审批完成或撤回后修改");}
    public List<Long> collaborators(Object v){LinkedHashSet<Long> ids=new LinkedHashSet<>();for(Object x:array(v)){long id=num(x);user(id);ids.add(id);}if(ids.size()>50)throw new ApiException(400,"协作人最多50名");return new ArrayList<>(ids);}

    public Map<String,Object> page(List<Map<String,Object>> data,Map<String,String> q){int page=positive(q.get("pageNum"),1),size=Math.min(positive(q.get("pageSize"),20),200);long from=(long)(page-1)*size;
        return ApiResult.page(data.subList((int)Math.min(from,data.size()),(int)Math.min(from+size,data.size())),data.size());}
    static int positive(String v,int fallback){try{return Math.max(1,Integer.parseInt(v));}catch(Exception e){return fallback;}}
    static boolean contains(Object a,String b){return text(a).toLowerCase(Locale.ROOT).contains(text(b).toLowerCase(Locale.ROOT));}
    static boolean filter(Map<String,Object> r,Map<String,String> q,String key){return text(q.get(key)).isBlank()||text(r.get(key)).equals(text(q.get(key)));}
    boolean dates(Map<String,Object> r,Map<String,String> q){String created=text(r.get("createTime"));return (text(q.get("startDate")).isBlank()||created.compareTo(q.get("startDate"))>=0)&&(text(q.get("endDate")).isBlank()||created.substring(0,10).compareTo(q.get("endDate"))<=0);}

    public List<Map<String,Object>> jobs(Map<String,String> q){return rows(JOB_SELECT+" WHERE j.tenant_id=1 ORDER BY j.id DESC").stream().filter(this::visible)
            .filter(r->contains(r.get("title"),q.get("keyword"))||contains(r.get("code"),q.get("keyword"))||contains(r.get("customerName"),q.get("keyword")))
            .filter(r->filter(r,q,"customerId")&&filter(r,q,"ownerUserId")&&filter(r,q,"status")&&dates(r,q)).map(this::decorateJob).toList();}
    Map<String,Object> decorateJob(Map<String,Object> r){r.put("collaborators",array(r.remove("collaboratorsJson")));long hired=num(r.get("hired"));r.put("vacancies",Math.max(0,num(r.get("headcount"))-hired));
        r.put("completionRate",num(r.get("headcount"))==0?0:Math.min(100,hired*100/num(r.get("headcount"))));
        long days=Math.max(0,ChronoUnit.DAYS.between(date(text(r.get("createTime")).substring(0,10),"创建日期"),today()));r.put("recruitingDays",days);
        List<String>warnings=new ArrayList<>();if("recruiting".equals(r.get("status"))){if(days>num(r.get("cycleDays"))||r.get("targetDate")!=null&&today().isAfter(date(r.get("targetDate"),"目标日期")))warnings.add("招聘超期");if(num(r.get("recommendations"))<num(r.get("minRecommendations")))warnings.add("推荐不足");}
        r.put("warnings",warnings);long recommended=num(r.get("recommendations"));r.put("interviewRate",recommended==0?0:num(r.get("interviews"))*100/recommended);r.put("offerRate",recommended==0?0:num(r.get("offers"))*100/recommended);r.put("hireRate",recommended==0?0:num(r.get("hired"))*100/recommended);return r;}
    public Map<String,Object> jobDetail(long id){Map<String,Object>r=decorateJob(job(id,false));r.put("events",events("job",id));r.put("versions",rows("SELECT id,version_no,snapshot_json,reason,create_time FROM oa_recruit_version WHERE job_id=? ORDER BY version_no DESC",id));r.put("approvals",approvals("job",id));return r;}
    List<Map<String,Object>> approvals(String type,long id){return rows("SELECT a.id,a.workflow_request_id,a.action_code,a.status,a.instance_id,a.create_time,r.process_task_name FROM oa_recruit_approval a LEFT JOIN oa_workflow_request r ON r.id=a.workflow_request_id WHERE a.entity_type=? AND (a.entity_id=? OR a.related_id=?) ORDER BY a.id DESC",type,id,id);}
    public Map<String,Object> jobFields(Map<String,Object>b){Map<String,Object>r=new LinkedHashMap<>();long customerId=num(b.get("customerId"));customer(customerId);long owner=num(b.getOrDefault("ownerUserId",AuthContext.userId()));user(owner);
        r.put("title",required(b.get("title"),200,"职位名称"));r.put("customerId",customerId);r.put("ownerUserId",owner);
        long contract=num(b.get("contractId"));if(contract>0){Map<String,Object>c=one("SELECT customer_id,effective_version,status FROM oa_crm_contract WHERE id=? AND tenant_id=1",contract);if(num(c.get("customerId"))!=customerId||num(c.get("effectiveVersion"))==0)throw new ApiException(400,"请选择该客户已生效的合同");}r.put("contractId",contract==0?null:contract);
        r.put("city",limited(b.get("city"),100,"地点"));r.put("industry",limited(b.get("industry"),100,"行业"));r.put("experience",limited(b.get("experience"),100,"经验要求"));
        r.put("salaryMin",money(b.get("salaryMin")));r.put("salaryMax",money(b.get("salaryMax")));if(money(r.get("salaryMax")).compareTo(money(r.get("salaryMin")))<0)throw new ApiException(400,"薪资上限不能低于下限");
        r.put("headcount",integer(b.getOrDefault("headcount",1),1,100000,"招聘人数"));r.put("requirements",required(b.get("requirements"),16000,"岗位要求"));r.put("feeTerms",limited(b.get("feeTerms"),2000,"收费条件"));
        r.put("collaborators",collaborators(b.get("collaborators")));r.put("targetDate",text(b.get("targetDate")).isBlank()?null:date(b.get("targetDate"),"目标日期").toString());
        r.put("cycleDays",integer(b.getOrDefault("cycleDays",30),1,3650,"招聘周期"));r.put("minRecommendations",integer(b.getOrDefault("minRecommendations",3),0,10000,"推荐阈值"));r.put("feedbackDays",integer(b.getOrDefault("feedbackDays",3),1,365,"反馈期限"));return r;}
    @Transactional public Map<String,Object> createJob(Map<String,Object>b){return createJobInternal(b,"create");}
    Map<String,Object> createJobInternal(Map<String,Object>b,String action){Map<String,Object>r=jobFields(b);long id=insert("""
            INSERT INTO oa_recruit_job (customer_id,contract_id,owner_user_id,title,code,city,industry,experience,salary_min,salary_max,headcount,requirements,fee_terms,collaborators_json,target_date,cycle_days,min_recommendations,feedback_days,create_by)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,r.get("customerId"),r.get("contractId"),r.get("ownerUserId"),r.get("title"),"JOB-"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT),r.get("city"),r.get("industry"),r.get("experience"),r.get("salaryMin"),r.get("salaryMax"),r.get("headcount"),r.get("requirements"),r.get("feeTerms"),json(r.get("collaborators")),r.get("targetDate"),r.get("cycleDays"),r.get("minRecommendations"),r.get("feedbackDays"),AuthContext.userId());
        version(id,"新建职位",AuthContext.userId());event("job",id,action,"新建职位",Map.of());syncCustomer(num(r.get("customerId")));return jobDetail(id);}
    @Transactional public Map<String,Object> updateJob(long id,Map<String,Object>b){Map<String,Object>r=job(id,true);revision(r,b);unlocked("job",id);if(!Set.of("draft","rejected","withdrawn").contains(text(r.get("demandStatus"))))throw new ApiException(409,"已确认职位请通过“职位变更”修改需求");Map<String,Object>fields=jobFields(b);if(num(fields.get("customerId"))!=num(r.get("customerId")))throw new ApiException(400,"请新建职位变更客户");saveJobFields(id,fields);event("job",id,"edit","修改职位草稿",Map.of());return jobDetail(id);}
    void saveJobFields(long id,Map<String,Object>f){jdbc.update("UPDATE oa_recruit_job SET contract_id=?,owner_user_id=?,title=?,city=?,industry=?,experience=?,salary_min=?,salary_max=?,headcount=?,requirements=?,fee_terms=?,collaborators_json=?,target_date=?,cycle_days=?,min_recommendations=?,feedback_days=?,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",
            f.get("contractId"),f.get("ownerUserId"),f.get("title"),f.get("city"),f.get("industry"),f.get("experience"),f.get("salaryMin"),f.get("salaryMax"),f.get("headcount"),f.get("requirements"),f.get("feeTerms"),json(f.get("collaborators")),f.get("targetDate"),f.get("cycleDays"),f.get("minRecommendations"),f.get("feedbackDays"),id);}
    void version(long id,String reason,long actor){Map<String,Object>r=one("SELECT * FROM oa_recruit_job WHERE id=?",id);jdbc.update("INSERT INTO oa_recruit_version (job_id,version_no,snapshot_json,reason,create_by) VALUES (?,?,?,?,?)",id,r.get("versionNo"),json(r),reason,actor);}
    @Transactional public Map<String,Object> copyJob(long id){Map<String,Object>r=job(id,false);r.put("collaborators",array(r.get("collaboratorsJson")));String title=text(r.get("title"));r.put("title",title.substring(0,Math.min(title.length(),196))+"（副本）");r.put("ownerUserId",AuthContext.userId());return createJobInternal(r,"copy");}
    @Transactional public void deleteJob(long id){Map<String,Object>r=job(id,true);unlocked("job",id);if(!"draft".equals(r.get("demandStatus"))||jdbc.queryForObject("SELECT COUNT(*) FROM oa_recruit_application WHERE job_id=?",Long.class,id)>0)throw new ApiException(409,"仅未提交且未被推荐引用的职位草稿可删除");jdbc.update("DELETE FROM oa_recruit_version WHERE job_id=?",id);jdbc.update("DELETE FROM oa_recruit_job WHERE id=?",id);event("job",id,"delete","删除职位草稿",Map.of());syncCustomer(num(r.get("customerId")));}
    @Transactional public Map<String,Object> jobAction(long id,String action,Map<String,Object>b){Map<String,Object>r=job(id,true);revision(r,b);unlocked("job",id);String reason=required(b.get("reason"),2000,"原因");
        switch(action){case "status"->{String status=required(b.get("status"),32,"状态");if(!Set.of("draft","recruiting","paused","completed","closed").contains(status))throw new ApiException(400,"无效职位状态");if(!"approved".equals(r.get("demandStatus"))&&!Set.of("draft","closed").contains(status))throw new ApiException(409,"请先完成需求确认");if("recruiting".equals(status)&&num(r.get("hired"))>=num(r.get("headcount")))throw new ApiException(409,"职位名额已满，请先变更人数或核实入职状态");if("completed".equals(status)&&num(r.get("hired"))<num(r.get("headcount")))throw new ApiException(409,"入职人数尚未达到需求，请关闭职位或提交人数变更");if("draft".equals(status)&&!"draft".equals(r.get("demandStatus")))throw new ApiException(409,"已确认职位不能退为草稿");jdbc.update("UPDATE oa_recruit_job SET status=?,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",status,id);}
            case "assign"->{long owner=num(b.get("ownerUserId"));user(owner);jdbc.update("UPDATE oa_recruit_job SET owner_user_id=?,collaborators_json=?,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",owner,json(collaborators(b.get("collaborators"))),id);}
            case "warning"->jdbc.update("UPDATE oa_recruit_job SET cycle_days=?,min_recommendations=?,feedback_days=?,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",integer(b.get("cycleDays"),1,3650,"招聘周期"),integer(b.get("minRecommendations"),0,10000,"推荐阈值"),integer(b.get("feedbackDays"),1,365,"反馈期限"),id);
            default->throw new ApiException(400,"不支持的职位操作");}
        String summary="assign".equals(action)?"负责人："+text(r.get("ownerName"))+" → "+text(user(num(b.get("ownerUserId"))).get("nickName")):"status".equals(action)?"职位状态："+text(r.get("status"))+" → "+text(b.get("status")):"预警配置";event("job",id,action,summary+"；"+reason,b);return jobDetail(id);}
    public boolean managedCustomer(long id){return jdbc.queryForObject("SELECT COUNT(*) FROM oa_recruit_job WHERE customer_id=?",Long.class,id)>0;}
    public void syncCustomer(long id){jdbc.update("""
            UPDATE oa_merchant SET cooperation_positions=(SELECT COUNT(*) FROM oa_recruit_job WHERE customer_id=?),
            referral_count=(SELECT COUNT(*) FROM oa_recruit_application a JOIN oa_recruit_job j ON j.id=a.job_id WHERE j.customer_id=? AND a.submitted_date IS NOT NULL AND a.stage<>'withdrawn'),
            hire_count=(SELECT COUNT(*) FROM oa_recruit_application a JOIN oa_recruit_job j ON j.id=a.job_id WHERE j.customer_id=? AND a.actual_entry_date IS NOT NULL AND a.stage IN ('entered','guarantee','completed'))
            WHERE id=?
            """,id,id,id,id);}

    Map<String,Object> publicTalent(Map<String,Object>r){r.put("tags",array(r.remove("tagsJson")));r.put("collaborators",array(r.remove("collaboratorsJson")));boolean contact=allowed(TALENT,"contact");r.put("contactVisible",contact);if(!contact){r.put("phone",mask(text(r.get("phone"))));r.put("email",mask(text(r.get("email"))));r.remove("remark");}return r;}
    String mask(String v){return v.length()<5?(v.isBlank()?"":"***"):v.substring(0,2)+"****"+v.substring(v.length()-2);}
    public List<Map<String,Object>> talents(Map<String,String>q){return rows(TALENT_SELECT+" WHERE t.tenant_id=1 AND t.status NOT IN ('deleted','merged') ORDER BY t.id DESC").stream().filter(this::visible)
            .filter(r->contains(r.get("name"),q.get("keyword"))||contains(r.get("skills"),q.get("keyword")))
            .filter(r->filter(r,q,"status")&&filter(r,q,"ownerUserId")&&contains(r.get("city"),q.get("city"))&&contains(r.get("industry"),q.get("industry"))&&contains(r.get("skills"),q.get("skills"))&&dates(r,q))
            .filter(r->text(q.get("experienceMin")).isBlank()||num(r.get("experienceYears"))>=num(q.get("experienceMin")))
            .filter(r->text(q.get("tag")).isBlank()||array(r.get("tagsJson")).stream().anyMatch(t->text(t).equals(q.get("tag"))))
            .map(this::publicTalent).toList();}
    public Map<String,Object> talentDetail(long id){Map<String,Object>r=publicTalent(talent(id,false));r.put("events",events("talent",id));r.put("approvals",approvals("talent",id));r.put("disputes",rows("SELECT d.*,p.nick_name AS proposed_owner_name,h.nick_name AS handler_name FROM oa_recruit_dispute d LEFT JOIN sys_user p ON p.user_id=d.proposed_owner_id LEFT JOIN sys_user h ON h.user_id=d.handler_user_id WHERE d.talent_id=? ORDER BY d.id DESC",id));r.put("resumes",rows("SELECT id,version_no,original_name,content_type,file_size,create_time FROM oa_recruit_resume WHERE talent_id=? ORDER BY version_no DESC",id));r.put("tasks",rows("SELECT t.*,u.nick_name AS owner_name FROM oa_recruit_task t LEFT JOIN sys_user u ON u.user_id=t.owner_user_id WHERE talent_id=? ORDER BY t.id DESC",id));event("talent",id,"view",allowed(TALENT,"contact")?"查看候选人及联系方式":"查看候选人（联系方式脱敏）",Map.of());return r;}
    Map<String,Object> talentFields(Map<String,Object>b){return talentFields(b,Set.of());}
    Map<String,Object> talentFields(Map<String,Object>b,Set<String> retainedTags){Map<String,Object>r=new LinkedHashMap<>();r.put("name",required(b.get("name"),100,"候选人姓名"));String phone=limited(b.get("phone"),40,"手机号").replaceAll("[\\s()+-]","");if(!phone.isBlank()&&!phone.matches("[0-9]{6,20}"))throw new ApiException(400,"手机号须为6至20位数字");String email=limited(b.get("email"),200,"邮箱").toLowerCase(Locale.ROOT);if(!email.isBlank()&&!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new ApiException(400,"邮箱格式无效");if(phone.isBlank()&&email.isBlank())throw new ApiException(400,"手机号和邮箱至少填写一项");r.put("phone",phone);r.put("email",email);
        long owner=num(b.getOrDefault("ownerUserId",AuthContext.userId()));user(owner);r.put("ownerUserId",owner);for(String key:List.of("city","industry","source"))r.put(key,limited(b.get(key),100,key));r.put("skills",limited(b.get("skills"),1000,"技能"));r.put("remark",limited(b.get("remark"),2000,"备注"));r.put("experienceYears",integer(b.getOrDefault("experienceYears",0),0,80,"经验年限"));r.put("expectedSalary",money(b.get("expectedSalary")));r.put("tags",validTags(b.get("tags"),retainedTags));r.put("collaborators",collaborators(b.get("collaborators")));return r;}
    List<String> validTags(Object value){return validTags(value,Set.of());}
    List<String> validTags(Object value,Set<String> retained){List<String>tags=array(value).stream().map(RecruitmentService::text).filter(v->!v.isBlank()).distinct().toList();if(tags.size()>30)throw new ApiException(400,"最多30个标签");for(String t:tags)if(!retained.contains(t)&&jdbc.queryForObject("SELECT COUNT(*) FROM oa_recruit_tag WHERE name=? AND status='active'",Long.class,t)==0)throw new ApiException(400,"标签不存在或已停用："+t);return tags;}
    @Transactional public Map<String,Object> createTalent(Map<String,Object>b){Map<String,Object>r=talentFields(b);long id=insert("INSERT INTO oa_recruit_talent (owner_user_id,name,phone,email,city,industry,skills,experience_years,expected_salary,source,remark,tags_json,collaborators_json,create_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",r.get("ownerUserId"),r.get("name"),r.get("phone"),r.get("email"),r.get("city"),r.get("industry"),r.get("skills"),r.get("experienceYears"),r.get("expectedSalary"),r.get("source"),r.get("remark"),json(r.get("tags")),json(r.get("collaborators")),AuthContext.userId());event("talent",id,"create","新建候选人",Map.of());return talentDetail(id);}
    @Transactional public Map<String,Object> updateTalent(long id,Map<String,Object>b){Map<String,Object>r=talent(id,true);revision(r,b);unlocked("talent",id);if(Set.of("merged","archived","disabled").contains(text(r.get("status"))))throw new ApiException(409,"请先重新激活候选人");if(!allowed(TALENT,"contact"))throw new ApiException(403,"修改档案需要联系方式权限");Map<String,Object>f=talentFields(b,new HashSet<>(array(r.get("tagsJson")).stream().map(RecruitmentService::text).toList()));if(num(f.get("ownerUserId"))!=num(r.get("ownerUserId")))throw new ApiException(400,"负责人变更请提交转交申请");jdbc.update("UPDATE oa_recruit_talent SET name=?,phone=?,email=?,city=?,industry=?,skills=?,experience_years=?,expected_salary=?,source=?,remark=?,tags_json=?,collaborators_json=?,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",f.get("name"),f.get("phone"),f.get("email"),f.get("city"),f.get("industry"),f.get("skills"),f.get("experienceYears"),f.get("expectedSalary"),f.get("source"),f.get("remark"),json(f.get("tags")),json(f.get("collaborators")),id);event("talent",id,"edit","更新候选人档案",Map.of());return talentDetail(id);}
    @Transactional public void deleteTalent(long id){Map<String,Object>r=talent(id,true);unlocked("talent",id);if(!"draft".equals(r.get("status"))||jdbc.queryForObject("SELECT COUNT(*) FROM oa_recruit_dispute WHERE talent_id=?",Long.class,id)>0||jdbc.queryForObject("SELECT COUNT(*) FROM oa_recruit_application WHERE talent_id=?",Long.class,id)>0||jdbc.queryForObject("SELECT COUNT(*) FROM oa_recruit_task WHERE talent_id=?",Long.class,id)>0)throw new ApiException(409,"仅未被推荐或跟进任务引用的草稿可删除");jdbc.update("UPDATE oa_recruit_talent SET status='deleted',revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",id);event("talent",id,"delete","删除未引用草稿",Map.of());}
    @Transactional public Map<String,Object> talentAction(long id,String action,Map<String,Object>b){Map<String,Object>r=talent(id,true);revision(r,b);unlocked("talent",id);String reason=required(b.get("reason"),2000,"原因或跟进内容");if("merged".equals(r.get("status")))throw new ApiException(409,"该档案已合并");
        if("status".equals(action)){String status=required(b.get("status"),32,"人才状态");if(!Set.of("uncontacted","contacting","available","not_now","dormant","archived","disabled").contains(status))throw new ApiException(400,"人才状态无效");jdbc.update("UPDATE oa_recruit_talent SET status=?,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",status,id);}
        else if("contact".equals(action))jdbc.update("UPDATE oa_recruit_talent SET last_contact_time=CURRENT_TIMESTAMP,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",id);
        else if("collaborators".equals(action))jdbc.update("UPDATE oa_recruit_talent SET collaborators_json=?,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",json(collaborators(b.get("collaborators"))),id);
        else throw new ApiException(400,"不支持的候选人操作");event("talent",id,action,("status".equals(action)?"人才状态："+text(r.get("status"))+" → "+text(b.get("status"))+"；":"")+reason,Map.of());return talentDetail(id);}

    public List<Map<String,Object>> duplicates(Map<String,String>q){List<Map<String,Object>>all=rows(TALENT_SELECT+" WHERE t.tenant_id=1 AND t.status NOT IN ('deleted','merged') ORDER BY t.id").stream().filter(this::visible).toList();List<Map<String,Object>>out=new ArrayList<>();for(Map<String,Object>a:all){String phone=text(a.get("phone")),email=text(a.get("email"));List<Map<String,Object>>matches=all.stream().filter(b->num(b.get("id"))!=num(a.get("id"))&&(!phone.isBlank()&&phone.equals(text(b.get("phone")))||!email.isBlank()&&email.equalsIgnoreCase(text(b.get("email"))))).toList();if(!matches.isEmpty()&&(text(q.get("keyword")).isBlank()||contains(a.get("name"),q.get("keyword")))){Map<String,Object>r=new LinkedHashMap<>(a);r.put("duplicateIds",matches.stream().map(m->m.get("id")).toList());out.add(publicTalent(r));}}return out;}
    @Transactional public void batchTags(Map<String,Object>b){List<Object>ids=array(b.get("ids"));if(ids.isEmpty()||ids.size()>200)throw new ApiException(400,"请选择1至200名候选人");List<String>tags=validTags(b.get("tags"));for(Object id:ids){Map<String,Object>r=talent(num(id),true);unlocked("talent",num(id));LinkedHashSet<Object>combined=new LinkedHashSet<>(array(r.get("tagsJson")));combined.addAll(tags);if(combined.size()>30)throw new ApiException(400,"候选人标签超过30个");jdbc.update("UPDATE oa_recruit_talent SET tags_json=?,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",json(combined),num(id));event("talent",num(id),"tag","批量打标签",tags);}}
    public List<Map<String,Object>> tags(){return rows("SELECT * FROM oa_recruit_tag ORDER BY id DESC");}
    @Transactional public void saveTag(Map<String,Object>b){String name=required(b.get("name"),80,"标签名称");long id=num(b.get("id"));String status=text(b.getOrDefault("status","active"));if(!Set.of("active","disabled").contains(status))throw new ApiException(400,"标签状态无效");if(jdbc.queryForObject("SELECT COUNT(*) FROM oa_recruit_tag WHERE name=? AND id<>?",Long.class,name,id)>0)throw new ApiException(400,"标签名称已存在");if(id==0)jdbc.update("INSERT INTO oa_recruit_tag (name,status) VALUES (?,?)",name,status);else{Map<String,Object>old=one("SELECT * FROM oa_recruit_tag WHERE id=? FOR UPDATE",id);if(!name.equals(old.get("name")))for(Map<String,Object>r:rows("SELECT id,tags_json FROM oa_recruit_talent"))if(array(r.get("tagsJson")).contains(old.get("name")))unlocked("talent",num(r.get("id")));jdbc.update("UPDATE oa_recruit_tag SET name=?,status=? WHERE id=?",name,status,id);if(!name.equals(old.get("name")))for(Map<String,Object>r:rows("SELECT id,tags_json FROM oa_recruit_talent")){List<Object>tags=array(r.get("tagsJson"));if(tags.remove(old.get("name"))){tags.add(name);jdbc.update("UPDATE oa_recruit_talent SET tags_json=?,revision=revision+1 WHERE id=?",json(tags),r.get("id"));}}}}
    public List<Map<String,Object>> searches(){return rows("SELECT id,name,query_json FROM oa_recruit_search WHERE user_id=? ORDER BY id DESC",AuthContext.userId());}
    @Transactional public void saveSearch(Map<String,Object>b){String name=required(b.get("name"),100,"搜索名称");Map<String,Object>q=object(b.get("query"));q.keySet().retainAll(Set.of("keyword","city","industry","skills","experienceMin","status","tag","ownerUserId"));if(num(b.get("id"))>0){jdbc.update("DELETE FROM oa_recruit_search WHERE id=? AND user_id=?",num(b.get("id")),AuthContext.userId());return;}jdbc.update("DELETE FROM oa_recruit_search WHERE name=? AND user_id=?",name,AuthContext.userId());jdbc.update("INSERT INTO oa_recruit_search (user_id,name,query_json) VALUES (?,?,?)",AuthContext.userId(),name,json(q));}
    @Transactional public long createTask(long id,Map<String,Object>b){Map<String,Object>r=talent(id,true);unlocked("talent",id);long owner=num(b.get("ownerUserId"));user(owner);LocalDate due=date(b.get("dueDate"),"跟进期限");if(due.isBefore(today()))throw new ApiException(400,"跟进期限不能早于今天");long task=insert("INSERT INTO oa_recruit_task (talent_id,owner_user_id,title,due_date,create_by) VALUES (?,?,?,?,?)",id,owner,required(b.get("title"),200,"任务内容"),due,AuthContext.userId());LinkedHashSet<Object>collaborators=new LinkedHashSet<>(array(r.get("collaboratorsJson")));collaborators.add(owner);jdbc.update("UPDATE oa_recruit_talent SET collaborators_json=?,revision=revision+1 WHERE id=?",json(collaborators),id);event("talent",id,"task","分配跟进任务",Map.of("taskId",task));return task;}
    @Transactional public void completeTask(long id,Map<String,Object>b){Map<String,Object>t=one("SELECT * FROM oa_recruit_task WHERE id=? FOR UPDATE",id);Map<String,Object>r=talent(num(t.get("talentId")),true);unlocked("talent",num(r.get("id")));if(num(t.get("ownerUserId"))!=AuthContext.userId()&&!visible(r))throw new ApiException(403,"不能完成他人的任务");if(!"todo".equals(t.get("status")))throw new ApiException(409,"任务已完成");String result=required(b.get("result"),2000,"跟进结果");jdbc.update("UPDATE oa_recruit_task SET status='done',result=?,complete_time=CURRENT_TIMESTAMP WHERE id=?",result,id);jdbc.update("UPDATE oa_recruit_talent SET last_contact_time=CURRENT_TIMESTAMP,revision=revision+1 WHERE id=?",r.get("id"));event("talent",num(r.get("id")),"contact",result,Map.of("taskId",id));}

    public Map<String,Object> options(){
        Map<String,Object>r=new LinkedHashMap<>();
        boolean pipeline=system.hasModuleAccess(AuthContext.userId(),PIPELINE);
        boolean canJobs=pipeline||system.hasModuleAccess(AuthContext.userId(),JOB);
        boolean canTalents=pipeline||system.hasModuleAccess(AuthContext.userId(),TALENT);
        r.put("currentUserId",AuthContext.userId());
        r.put("users",rows("SELECT user_id AS id,nick_name AS name FROM sys_user WHERE tenant_id=1 AND del_flag='0' AND status='0' ORDER BY user_id"));
        r.put("customers",canJobs?rows("SELECT m.id,m.name,m.owner_user_id,u.dept_id AS owner_dept_id FROM oa_merchant m LEFT JOIN sys_user u ON u.user_id=m.owner_user_id WHERE m.tenant_id=1 AND m.status='0' AND m.public_pool='0'").stream().filter(c->scopes.canAccess(AuthContext.userId(),c.get("ownerDeptId"),c.get("ownerUserId"))).map(c->Map.of("id",c.get("id"),"name",c.get("name"))).toList():List.of());
        r.put("jobs",canJobs?jobs(Map.of()).stream().map(j->Map.of("id",j.get("id"),"name",j.get("title")+" · "+j.get("customerName"),"status",j.get("status"))).toList():List.of());
        r.put("talents",canTalents?talents(Map.of()).stream().map(t->Map.of("id",t.get("id"),"name",t.get("name"),"status",t.get("status"))).toList():List.of());
        r.put("contracts",canJobs?rows("SELECT c.id,c.name,c.customer_id,m.owner_user_id,u.dept_id AS owner_dept_id FROM oa_crm_contract c JOIN oa_merchant m ON m.id=c.customer_id LEFT JOIN sys_user u ON u.user_id=m.owner_user_id WHERE c.tenant_id=1 AND c.effective_version>0").stream().filter(c->scopes.canAccess(AuthContext.userId(),c.get("ownerDeptId"),c.get("ownerUserId"))).map(c->Map.of("id",c.get("id"),"name",c.get("name"),"customerId",c.get("customerId"))).toList():List.of());
        r.put("tags",canTalents?tags():List.of());r.put("searches",canTalents?searches():List.of());return r;
    }
    @Transactional public Map<String,Object> importRows(String type,Map<String,Object>b){List<Object>data=array(b.get("rows"));if(data.isEmpty()||data.size()>200)throw new ApiException(400,"每次导入1至200条");int i=0;for(Object row:data){i++;try{if("jobs".equals(type))jobFields(object(row));else talentFields(object(row));}catch(ApiException e){throw new ApiException(400,"第"+i+"行："+e.getMessage());}}for(Object row:data){if("jobs".equals(type))createJob(object(row));else createTalent(object(row));}return Map.of("count",i);}
    public List<Map<String,Object>> duplicateJobs(Map<String,String>q){List<Map<String,Object>>all=jobs(Map.of());return jobs(q).stream().filter(r->{List<Object>matches=all.stream().filter(x->num(x.get("id"))!=num(r.get("id"))&&num(x.get("customerId"))==num(r.get("customerId"))&&text(x.get("title")).replaceAll("\\s","").equalsIgnoreCase(text(r.get("title")).replaceAll("\\s",""))).map(x->x.get("id")).toList();r.put("duplicateIds",matches);return !matches.isEmpty();}).toList();}
    @Transactional public List<Map<String,Object>> exportRows(String type,Map<String,String>q){List<Map<String,Object>>r="jobs".equals(type)?jobs(q):talents(q);if(r.size()>500)throw new ApiException(400,"请缩小筛选范围，每次最多导出500条");for(Map<String,Object>x:r)event("jobs".equals(type)?"job":"talent",num(x.get("id")),"export","授权导出",Map.of());return r;}
}
