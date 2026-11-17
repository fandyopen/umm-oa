package com.seal.oa.commission;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.JdbcMaps;
import com.seal.oa.hr.HrPayrollService;
import com.seal.oa.system.DataScopeService;
import com.seal.oa.system.SystemService;
import com.seal.oa.workflow.WorkflowActionContext;
import com.seal.oa.workflow.WorkflowApprovalContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;

@Service
public class CommissionService {
    public static final long MODULE = 341410;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final SystemService system;
    private final DataScopeService scope;
    private final HrPayrollService payroll;

    public CommissionService(JdbcTemplate jdbc, ObjectMapper mapper, SystemService system, DataScopeService scope, HrPayrollService payroll) {
        this.jdbc=jdbc; this.mapper=mapper; this.system=system; this.scope=scope; this.payroll=payroll;
    }
    long tenant() { return system.requireCurrentUser().tenantId(); }
    long actor() { return AuthContext.userId(); }
    void require(String action) {
        if (!system.hasModuleAction(actor(), MODULE, action)) throw new ApiException(403,"没有提成核算操作权限");
    }
    void mutex() { jdbc.queryForObject("SELECT tenant_id FROM sys_tenant WHERE tenant_id=? FOR UPDATE",Long.class,tenant()); }
    public List<Map<String,Object>> rules() { require("access"); return rows("SELECT * FROM oa_commission_rule WHERE tenant_id=? ORDER BY effective_date DESC,id DESC",tenant()); }

    @Transactional public Map<String,Object> saveRule(Map<String,Object> b) {
        require("rule"); mutex();
        String kind=kind(b.get("businessKind"));
        BigDecimal pool=rate(b.get("poolRate")), development=rate(b.get("developmentShare")), release=rate(b.get("initialRelease"));
        if(pool.signum()==0) throw new ApiException(400,"总提成率须大于零");
        LocalDate effective=day(b.get("effectiveDate"));
        long id=insert("INSERT INTO oa_commission_rule(tenant_id,name,business_kind,effective_date,pool_rate,development_share,initial_release,enabled,create_by) VALUES(?,?,?,?,?,?,?,?,?)",
                tenant(),required(b.get("name"),150),kind,effective,pool,development,release,1,actor());
        history("rule",id,"新增提成规则版本",b);
        return one("SELECT * FROM oa_commission_rule WHERE id=?",id);
    }
    @Transactional public void toggleRule(long id,Map<String,Object>b) {
        require("rule"); mutex(); one("SELECT id FROM oa_commission_rule WHERE id=? AND tenant_id=?",id,tenant());
        jdbc.update("UPDATE oa_commission_rule SET enabled=? WHERE id=?",Boolean.TRUE.equals(b.get("enabled"))?1:0,id);
        history("rule",id,"切换规则可选状态",b);
    }
    Map<String,Object> application(long id) { return application(id,true); }
    Map<String,Object> application(long id,boolean enforceScope) {
        Map<String,Object> r=one("""
                SELECT a.*,j.customer_id,j.contract_id,CASE WHEN j.status='deleted' THEN CONCAT(j.title,'（职位已删除）') ELSE j.title END AS job_title,m.name AS customer_name,
                  m.owner_user_id AS development_user_id,a.consultant_id AS delivery_user_id,t.name AS talent_name,ca.rule_id AS original_rule_id,ca.business_kind AS original_business_kind
                FROM oa_recruit_application a JOIN oa_recruit_job j ON j.id=a.job_id
                JOIN oa_merchant m ON m.id=j.customer_id JOIN oa_recruit_talent t ON t.id=a.talent_id
                LEFT JOIN oa_recruit_replacement rr ON rr.replacement_application_id=a.id
                LEFT JOIN oa_commission_attribution ca ON ca.application_id=rr.original_application_id AND ca.status='confirmed' AND ca.tenant_id=a.tenant_id
                WHERE a.id=? AND a.tenant_id=? AND j.tenant_id=? AND m.tenant_id=?
                """,id,tenant(),tenant(),tenant());
        if(enforceScope)checkCustomer(number(r.get("customerId"))); return r;
    }
    void checkCustomer(long id) {
        var c=one("SELECT owner_user_id FROM oa_merchant WHERE id=? AND tenant_id=?",id,tenant());
        if(!canAccessOwner(c.get("ownerUserId"))) throw new ApiException(403,"客户不在数据范围内");
    }
    boolean canAccessOwner(Object owner) {
        if (WorkflowApprovalContext.active(actor())) return true;
        var users=rows("SELECT dept_id FROM sys_user WHERE user_id=? AND tenant_id=?",number(owner),tenant());
        return scope.canAccess(actor(),users.isEmpty()?null:users.get(0).get("deptId"),owner);
    }
    Map<String,Object> attribution(long id) {
        var r=one("SELECT * FROM oa_commission_attribution WHERE id=? AND tenant_id=?",id,tenant());
        checkCustomer(number(r.get("customerId"))); return r;
    }

    Map<String,Object> contract(long id) { return contract(id,true); }
    Map<String,Object> contract(long id,boolean enforceScope) {
        var c=one("SELECT c.*,m.name AS customer_name,m.owner_user_id FROM oa_crm_contract c JOIN oa_merchant m ON m.id=c.customer_id AND m.tenant_id=c.tenant_id WHERE c.id=? AND c.tenant_id=?",id,tenant());
        if(enforceScope)checkCustomer(number(c.get("customerId")));
        var versions=rows("SELECT snapshot_json,signed_date FROM oa_crm_contract_version WHERE contract_id=? AND version_no=?",id,c.get("effectiveVersion"));
        c.put("effectiveAmount",versions.isEmpty()?money(c.get("amount")):money(object(versions.get(0).get("snapshotJson")).get("amount")));
        c.put("effectiveTerms",versions.isEmpty()?list(c.get("termsJson")):list(object(versions.get(0).get("snapshotJson")).get("feeTerms")));
        c.put("signedDate",versions.isEmpty()?null:versions.get(0).get("signedDate"));
        return c;
    }
    Map<String,Object> contractPlan(long contractId) {
        var found=rows("SELECT * FROM oa_commission_attribution WHERE contract_source_id=? AND tenant_id=?",contractId,tenant());
        return found.isEmpty()?Map.of():found.get(0);
    }
    void firstContract(long customer,long contract) {
        if(count("SELECT COUNT(*) FROM oa_commission_attribution WHERE tenant_id=? AND customer_id=? AND contract_id<>? AND business_kind='first' AND status='confirmed'",tenant(),customer,contract)>0)
            throw new ApiException(409,"此客户已确认其他首单合同，请按老客续单维护");
    }
    public Map<String,Object> contractCommission(long id) {
        if(!system.hasModuleAccess(actor(),MODULE)&&!system.hasModuleAccess(actor(),341201L))throw new ApiException(403,"没有合同提成查看权限");
        var c=contract(id);var plan=contractPlan(id);var result=new LinkedHashMap<String,Object>();
        result.put("contract",Map.of("id",id,"name",c.get("name"),"customerId",c.get("customerId"),"customerName",c.get("customerName"),"ownerUserId",number(c.get("ownerUserId")),"amount",c.get("effectiveAmount"),"effectiveVersion",number(c.get("effectiveVersion")),"feeTerms",c.get("effectiveTerms")));
        result.put("canManage",system.hasModuleAction(actor(),MODULE,"attribute"));
        result.put("canViewFinance",system.hasModuleAccess(actor(),MODULE));
        var cashEvidence=new ArrayList<Object>();BigDecimal netReceived=contractCash(id,today(),cashEvidence);result.put("netReceived",money(netReceived));
        result.put("legacy",count("SELECT COUNT(*) FROM oa_commission_attribution WHERE contract_id=? AND tenant_id=? AND contract_source_id IS NULL AND parent_id IS NULL",id,tenant())>0);
        if(!plan.isEmpty()) {
            var view=new LinkedHashMap<>(plan);view.put("rule",object(view.remove("ruleJson")));view.put("participants",list(view.remove("participantsJson")));result.put("plan",view);
            var rule=object(plan.get("ruleJson"));BigDecimal estimated=money(decimal(c.get("effectiveAmount")).multiply(decimal(rule.get("poolRate"))).divide(HUNDRED,8,RoundingMode.HALF_UP));
            BigDecimal development=money(estimated.multiply(decimal(rule.get("developmentShare"))).divide(HUNDRED,8,RoundingMode.HALF_UP));
            BigDecimal cash=netReceived;
            result.put("netReceived",money(cash));result.put("estimatedPool",estimated);result.put("estimatedDevelopment",development);result.put("estimatedDelivery",estimated.subtract(development));
            result.put("developmentEarned","confirmed".equals(plan.get("status"))?money(money(cash.multiply(decimal(rule.get("poolRate"))).divide(HUNDRED,8,RoundingMode.HALF_UP)).multiply(decimal(rule.get("developmentShare"))).divide(HUNDRED,8,RoundingMode.HALF_UP)):BigDecimal.ZERO);
            result.put("deliveryEarned",deliveryBudgets(id,today()).values().stream().reduce(BigDecimal.ZERO,BigDecimal::add));
            result.put("acceptances",rows("SELECT id,basis_date,acceptance_reference,status,revision,participants_json FROM oa_commission_attribution WHERE parent_id=? AND source_type='acceptance' ORDER BY id DESC",plan.get("id")).stream().map(a->{a.put("participants",list(a.remove("participantsJson")));return a;}).toList());
        }
        result.put("rules",system.hasModuleAction(actor(),MODULE,"attribute")?rows("SELECT * FROM oa_commission_rule WHERE tenant_id=? ORDER BY effective_date DESC,id DESC",tenant()):List.of());
        result.put("people",system.hasModuleAction(actor(),MODULE,"attribute")?people():List.of());
        return result;
    }
    public List<Map<String,Object>> contracts() {
        require("access");return rows("SELECT c.id,c.name,c.code,c.customer_id,m.name AS customer_name,m.owner_user_id,a.id AS attribution_id,a.status AS attribution_status FROM oa_crm_contract c JOIN oa_merchant m ON m.id=c.customer_id AND m.tenant_id=c.tenant_id LEFT JOIN oa_commission_attribution a ON a.contract_source_id=c.id AND a.tenant_id=c.tenant_id WHERE c.tenant_id=? AND c.effective_version>0 AND c.deleted=0 ORDER BY c.id DESC",tenant()).stream().filter(c->canAccessOwner(c.get("ownerUserId"))).toList();
    }
    List<Map<String,Object>> people() {
        return rows("SELECT u.user_id AS id,u.nick_name AS name,d.dept_name FROM sys_user u JOIN oa_hr_employee e ON e.user_id=u.user_id LEFT JOIN sys_dept d ON d.dept_id=u.dept_id WHERE u.tenant_id=? AND u.status='0' AND u.del_flag='0' AND %s ORDER BY u.user_id".formatted(com.seal.oa.system.MemberIdentity.condition("u")),tenant());
    }
    @Transactional public Map<String,Object> saveContractCommission(long id,Map<String,Object>b) {
        require("attribute");mutex();var c=contract(id);
        if(number(c.get("effectiveVersion"))==0||number(c.get("deleted"))!=0)throw new ApiException(409,"请先签署生效合同");
        var old=contractPlan(id);
        if(!old.isEmpty()){version(old,b);expect(old,"draft");}
        if(count("SELECT COUNT(*) FROM oa_commission_attribution WHERE contract_id=? AND tenant_id=? AND contract_source_id IS NULL AND parent_id IS NULL",id,tenant())>0)
            throw new ApiException(409,"此合同已有原方式的招聘归属，请沿用原核算，避免重复计提");
        var rule=one("SELECT * FROM oa_commission_rule WHERE id=? AND tenant_id=? AND enabled=1",number(b.get("ruleId")),tenant());
        LocalDate date=c.get("signedDate")==null?today():day(c.get("signedDate"));
        if(day(rule.get("effectiveDate")).isAfter(date))throw new ApiException(409,"规则不能晚于合同签署日期生效");
        String mode=text(b.get("settlementMode"));if(!Set.of("entry","acceptance").contains(mode))throw new ApiException(400,"请选择交付按入职或服务验收结算");
        var participants=participants(b.get("participants"),rule,Set.of("development"));String reason=required(b.get("reason"),1000);
        if("first".equals(rule.get("businessKind")))firstContract(number(c.get("customerId")),id);
        long attributionId=old.isEmpty()?insert("INSERT INTO oa_commission_attribution(tenant_id,customer_id,contract_id,rule_id,rule_json,participants_json,business_kind,basis_date,reason,create_by,source_type,contract_source_id,contract_version,settlement_mode) VALUES(?,?,?,?,?,?,?,?,?,?,'contract',?,?,?)",tenant(),c.get("customerId"),id,rule.get("id"),json(rule),json(participants),rule.get("businessKind"),date,reason,actor(),id,c.get("effectiveVersion"),mode):number(old.get("id"));
        if(!old.isEmpty())jdbc.update("UPDATE oa_commission_attribution SET rule_id=?,rule_json=?,participants_json=?,business_kind=?,basis_date=?,reason=?,contract_version=?,settlement_mode=?,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",rule.get("id"),json(rule),json(participants),rule.get("businessKind"),date,reason,c.get("effectiveVersion"),mode,attributionId);
        history("attribution",attributionId,"保存合同销售归属",b);return contractCommission(id);
    }
    @Transactional public Map<String,Object> confirmContractCommission(long id,Map<String,Object>b) {
        require("attribute");mutex();var c=contract(id);var plan=contractPlan(id);version(plan,b);expect(plan,"draft");
        if(number(c.get("effectiveVersion"))!=number(plan.get("contractVersion"))||number(c.get("deleted"))!=0)throw new ApiException(409,"生效合同版本已变化，请重新保存归属");
        if("first".equals(plan.get("businessKind")))firstContract(number(c.get("customerId")),id);
        jdbc.update("UPDATE oa_commission_attribution SET status='confirmed',revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",plan.get("id"));
        history("attribution",number(plan.get("id")),"确认合同销售归属与提成规则",Map.of("reason",required(b.get("reason"),1000)));return contractCommission(id);
    }
    @Transactional public Map<String,Object> acceptDelivery(long id,Map<String,Object>b) {
        require("attribute");mutex();var c=contract(id);var plan=contractPlan(id);expect(plan,"confirmed");
        if(!"acceptance".equals(plan.get("settlementMode")))throw new ApiException(409,"此合同交付按候选人入职结算，请维护招聘成果");
        LocalDate date=day(b.get("acceptanceDate"));if(date.isAfter(today())||date.isBefore(day(plan.get("basisDate"))))throw new ApiException(400,"验收日期须在合同签署日至今天之间");
        var participants=participants(b.get("participants"),object(plan.get("ruleJson")),Set.of("delivery"));
        String reference=required(b.get("acceptanceReference"),1000);
        if(list(c.get("effectiveTerms")).stream().noneMatch(t->text(t.get("key")).equals(text(b.get("termKey")))))throw new ApiException(400,"请选择生效合同的收费条款");
        if(count("SELECT COUNT(*) FROM oa_commission_attribution WHERE parent_id=? AND acceptance_reference=?",plan.get("id"),reference)>0)throw new ApiException(409,"此验收凭据已登记，请勿重复提交");
        long attributionId=insert("INSERT INTO oa_commission_attribution(tenant_id,customer_id,contract_id,term_key,rule_id,rule_json,participants_json,business_kind,basis_date,status,reason,create_by,source_type,parent_id,contract_version,settlement_mode,acceptance_reference) VALUES(?,?,?,?,?,?,?,?,?,'confirmed',?,?,'acceptance',?,?,'acceptance',?)",tenant(),c.get("customerId"),id,required(b.get("termKey"),64),plan.get("ruleId"),plan.get("ruleJson"),json(participants),plan.get("businessKind"),date,required(b.get("reason"),1000),actor(),plan.get("id"),plan.get("contractVersion"),reference);
        history("attribution",attributionId,"确认服务交付验收",b);return contractCommission(id);
    }
    BigDecimal contractCash(long id,LocalDate cutoff,List<Object> evidence) {
        BigDecimal cash=BigDecimal.ZERO;
        for(var receipt:rows("SELECT * FROM oa_crm_contract_receipt WHERE contract_id=? AND amount>0 ORDER BY id",id))
            cash=cash.add(netAllocated(Map.of("receiptId",receipt.get("id"),"amount",receipt.get("amount"),"allocationDate",receipt.get("receiptDate")),cutoff,evidence));
        return cash;
    }
    // Split the rounded delivery budget once across every child, including the unallocated balance.
    // This avoids creating extra cents when one contract recruits many people.
    Map<Long,BigDecimal> deliveryBudgets(long contract,LocalDate cutoff) {
        var plan=contractPlan(contract);if(plan.isEmpty()||!"confirmed".equals(plan.get("status")))return Map.of();
        var rule=object(plan.get("ruleJson"));var result=new LinkedHashMap<Long,BigDecimal>();var evidence=new ArrayList<Object>();
        BigDecimal cash=contractCash(contract,cutoff,evidence);if(cash.signum()==0)return result;
        BigDecimal pool=money(cash.multiply(decimal(rule.get("poolRate"))).divide(HUNDRED,8,RoundingMode.HALF_UP));
        BigDecimal total=pool.subtract(money(pool.multiply(decimal(rule.get("developmentShare"))).divide(HUNDRED,8,RoundingMode.HALF_UP)));
        var amounts=new TreeMap<Long,BigDecimal>();
        for(var allocation:rows("SELECT x.* FROM oa_commission_allocation x JOIN oa_commission_attribution a ON a.id=x.attribution_id WHERE a.parent_id=? AND a.status='confirmed' AND x.allocation_date<=? AND a.basis_date<=? ORDER BY a.id,x.receipt_id",plan.get("id"),cutoff,cutoff))
            amounts.merge(number(allocation.get("attributionId")),netAllocated(allocation,cutoff,evidence),BigDecimal::add);
        BigDecimal used=amounts.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
        if(used.compareTo(cash)>0)throw new ApiException(409,"交付分摊超过合同净回款，请核对");
        var weights=new ArrayList<>(amounts.values());weights.add(cash.subtract(used));var parts=weightedSplit(total,weights,cash);
        int index=0;for(long id:amounts.keySet())result.put(id,parts.get(index++));return result;
    }
    List<BigDecimal> weightedSplit(BigDecimal total,List<BigDecimal> weights,BigDecimal denominator) {
        var amounts=new ArrayList<BigDecimal>();var fractions=new ArrayList<BigDecimal>();BigDecimal sum=BigDecimal.ZERO;
        for(var weight:weights){BigDecimal exact=total.multiply(weight).divide(denominator,12,RoundingMode.HALF_UP),rounded=exact.setScale(2,RoundingMode.DOWN);amounts.add(rounded);fractions.add(exact.subtract(rounded));sum=sum.add(rounded);}
        var order=new ArrayList<Integer>();for(int i=0;i<weights.size();i++)if(weights.get(i).signum()>0)order.add(i);
        order.sort(Comparator.<Integer,BigDecimal>comparing(fractions::get).reversed().thenComparingInt(i->i));
        int cents=total.subtract(sum).movePointRight(2).intValueExact();for(int i=0;i<cents;i++){int n=order.get(i%order.size());amounts.set(n,amounts.get(n).add(new BigDecimal("0.01")));}return amounts;
    }
    public Map<String,Object> options() {
        require("access");
        var applications=rows("""
                SELECT a.id,a.stage,a.actual_entry_date,a.guarantee_status,CASE WHEN j.status='deleted' THEN CONCAT(j.title,'（职位已删除）') ELSE j.title END AS job_title,
                j.customer_id,j.contract_id,m.name AS customer_name,m.owner_user_id,
                t.name AS talent_name,a.consultant_id,a.guarantee_term_key,c.id AS attribution_id,c.status AS attribution_status
                FROM oa_recruit_application a JOIN oa_recruit_job j ON j.id=a.job_id
                JOIN oa_merchant m ON m.id=j.customer_id JOIN oa_recruit_talent t ON t.id=a.talent_id
                LEFT JOIN oa_commission_attribution c ON c.application_id=a.id
                WHERE a.tenant_id=? AND m.tenant_id=? ORDER BY a.create_time DESC,a.id DESC
                """,tenant(),tenant()).stream().filter(r->canAccessOwner(r.get("ownerUserId"))).toList();
        var people=rows("""
                SELECT u.user_id AS id,u.nick_name AS name,d.dept_name FROM sys_user u
                JOIN oa_hr_employee e ON e.user_id=u.user_id LEFT JOIN sys_dept d ON d.dept_id=u.dept_id
                WHERE u.tenant_id=? AND u.del_flag='0' AND u.status='0' AND %s ORDER BY u.user_id
                """.formatted(com.seal.oa.system.MemberIdentity.condition("u")),tenant());
        var acceptances=rows("SELECT a.id AS attribution_id,a.contract_id,a.term_key AS guarantee_term_key,a.status AS attribution_status,a.acceptance_reference AS talent_name,c.name AS job_title,m.name AS customer_name,a.customer_id FROM oa_commission_attribution a JOIN oa_crm_contract c ON c.id=a.contract_id JOIN oa_merchant m ON m.id=a.customer_id WHERE a.tenant_id=? AND a.source_type='acceptance' ORDER BY a.id",tenant()).stream().filter(a->{try{checkCustomer(number(a.get("customerId")));return true;}catch(ApiException e){if(e.code()==403)return false;throw e;}}).toList();
        return Map.of("applications",applications,"people",people,"rules",rules(),"receipts",receipts(),"contracts",contracts(),"acceptances",acceptances);
    }
    public Map<String,Object> attributionForApplication(long id) {
        require("access"); var a=application(id);
        var found=rows("SELECT * FROM oa_commission_attribution WHERE application_id=? AND tenant_id=?",id,tenant());
        var plan=contractPlan(number(a.get("contractId")));
        if(!plan.isEmpty()){var view=new LinkedHashMap<>(plan);view.put("rule",object(view.remove("ruleJson")));view.remove("participantsJson");a.put("contractPlan",view);}
        if(found.isEmpty()) return Map.of("application",a);
        var r=found.get(0); r.put("participants",list(r.remove("participantsJson"))); r.put("rule",object(r.remove("ruleJson")));
        return Map.of("application",a,"attribution",r);
    }

    @Transactional public Map<String,Object> saveAttribution(long applicationId,Map<String,Object>b) {
        require("attribute"); mutex(); var a=application(applicationId);
        if(number(a.get("contractId"))==0) throw new ApiException(409,"职位须先关联生效合同");
        one("SELECT id FROM oa_crm_contract WHERE id=? AND tenant_id=? AND effective_version>0",a.get("contractId"),tenant());
        var found=rows("SELECT * FROM oa_commission_attribution WHERE application_id=?",applicationId);
        long id=found.isEmpty()?0:number(found.get(0).get("id"));
        if(id>0) { version(found.get(0),b); expect(found.get(0),"draft"); }
        String kind=kind(b.get("businessKind"));
        var rule=one("SELECT * FROM oa_commission_rule WHERE id=? AND tenant_id=?",number(b.get("ruleId")),tenant());
        var plan=contractPlan(number(a.get("contractId")));
        if(!plan.isEmpty()) {
            expect(plan,"confirmed");
            if(!"entry".equals(plan.get("settlementMode")))throw new ApiException(409,"此合同交付按服务验收结算，请登记验收成果");
            if(number(rule.get("id"))!=number(plan.get("ruleId")))throw new ApiException(409,"交付须沿用合同冻结的提成规则");
            rule=object(plan.get("ruleJson"));
        }
        LocalDate basis=a.get("actualEntryDate")==null?today():day(a.get("actualEntryDate"));
        if(!kind.equals(rule.get("businessKind")) || day(rule.get("effectiveDate")).isAfter(basis))
            throw new ApiException(409,"规则类型或生效日期与本次业务不符");
        // A free replacement shares its original budget and rule; only personnel can be redistributed.
        var original=rows("""
                SELECT c.rule_id FROM oa_recruit_replacement r LEFT JOIN oa_commission_attribution c
                ON c.application_id=r.original_application_id AND c.status='confirmed' WHERE r.replacement_application_id=?
                """,applicationId);
        if(plan.isEmpty() && original.isEmpty() && number(rule.get("enabled"))!=1) throw new ApiException(409,"规则已停用，请选择可用版本");
        if(!original.isEmpty() && number(original.get(0).get("ruleId"))!=number(rule.get("id")))
            throw new ApiException(409,"免费替换须沿用原交付的提成规则");
        if("first".equals(kind) && count("SELECT COUNT(*) FROM oa_commission_attribution WHERE tenant_id=? AND customer_id=? AND business_kind='first' AND status='confirmed' AND contract_id<>?",tenant(),a.get("customerId"),a.get("contractId"))>0)
            throw new ApiException(409,"此客户已确认其他首单合同，请按老客续单维护");
        var participants=participants(b.get("participants"),rule,plan.isEmpty()?Set.of("development","delivery"):Set.of("delivery"));
        String reason=required(b.get("reason"),1000);
        if(id==0) id=insert("""
                INSERT INTO oa_commission_attribution(tenant_id,application_id,customer_id,job_id,contract_id,term_key,
                rule_id,rule_json,participants_json,business_kind,basis_date,reason,create_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,tenant(),applicationId,a.get("customerId"),a.get("jobId"),a.get("contractId"),a.get("guaranteeTermKey"),
                rule.get("id"),json(rule),json(participants),kind,basis,reason,actor());
        else jdbc.update("""
                UPDATE oa_commission_attribution SET contract_id=?,term_key=?,rule_id=?,rule_json=?,participants_json=?,
                business_kind=?,basis_date=?,reason=?,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?
                """,a.get("contractId"),a.get("guaranteeTermKey"),rule.get("id"),json(rule),json(participants),kind,basis,reason,id);
        if(!plan.isEmpty())jdbc.update("UPDATE oa_commission_attribution SET parent_id=?,contract_version=? WHERE id=?",plan.get("id"),plan.get("contractVersion"),id);
        history("attribution",id,"保存业绩归属",b); return attributionForApplication(applicationId);
    }
    List<Map<String,Object>> participants(Object input,Map<String,Object>rule) {
        return participants(input,rule,Set.of("development","delivery"));
    }
    List<Map<String,Object>> participants(Object input,Map<String,Object>rule,Set<String> sides) {
        var result=new ArrayList<Map<String,Object>>(); var seen=new HashSet<String>();
        var sums=new HashMap<String,BigDecimal>();
        for(var p:list(input)) {
            String side=text(p.get("side")); if(!sides.contains(side)) throw new ApiException(400,"提成部门无效");
            long user=number(p.get("userId")); BigDecimal share=rate(p.get("share"));
            if(share.signum()==0 || !seen.add(side+":"+user)) throw new ApiException(400,"归属人员重复或比例为零");
            var u=one("""
                    SELECT u.user_id,u.nick_name,d.dept_name FROM sys_user u JOIN oa_hr_employee e ON e.user_id=u.user_id
                    LEFT JOIN sys_dept d ON d.dept_id=u.dept_id WHERE u.user_id=? AND u.tenant_id=? AND u.status='0' AND u.del_flag='0' AND %s
                    """.formatted(com.seal.oa.system.MemberIdentity.condition("u")),user,tenant());
            result.add(Map.of("userId",user,"name",text(u.get("nickName")),"deptName",text(u.get("deptName")),"side",side,"share",share));
            sums.merge(side,share,BigDecimal::add);
        }
        for(String side:sides) {
            BigDecimal budget="development".equals(side)?decimal(rule.get("developmentShare")):HUNDRED.subtract(decimal(rule.get("developmentShare")));
            if(budget.signum()>0 && sums.getOrDefault(side,BigDecimal.ZERO).compareTo(HUNDRED)!=0)
                throw new ApiException(400,("development".equals(side)?"拓展":"交付")+"部门内分配比例须合计100%");
            if(budget.signum()==0 && sums.containsKey(side)) throw new ApiException(400,"零预算部门无需分配人员");
        }
        result.sort(Comparator.comparing(p->p.get("side")+":"+p.get("userId"))); return result;
    }
    @Transactional public void confirmAttribution(long id,Map<String,Object>b,boolean confirm) {
        require("attribute"); mutex(); var r=attribution(id); version(r,b);
        if(!"application".equals(r.get("sourceType")))throw new ApiException(409,"请通过合同提成入口维护合同或验收归属");
        var a=application(number(r.get("applicationId")));
        if(confirm) {
            expect(r,"draft");
            if("first".equals(r.get("businessKind")) && count("SELECT COUNT(*) FROM oa_commission_attribution WHERE tenant_id=? AND customer_id=? AND business_kind='first' AND status='confirmed' AND contract_id<>?",tenant(),r.get("customerId"),r.get("contractId"))>0)
                throw new ApiException(409,"客户已确认其他首单合同，请重新保存为续单归属");
            if(a.get("actualEntryDate")==null) throw new ApiException(409,"实际入职后才能确认业绩归属");
            if(number(r.get("contractId"))!=number(a.get("contractId")) || !text(r.get("termKey")).equals(text(a.get("guaranteeTermKey"))))
                throw new ApiException(409,"入职合同或保证期条款已变化，请重新保存归属");
            if(!day(r.get("basisDate")).equals(day(a.get("actualEntryDate")))) throw new ApiException(409,"入职日期已变化，请重新保存归属");
        } else {
            expect(r,"confirmed");
            if(count("SELECT COUNT(*) FROM oa_commission_allocation WHERE attribution_id=?",id)>0
                    ||count("SELECT COUNT(*) FROM oa_commission_line WHERE attribution_id=?",id)>0)
                throw new ApiException(409,"已有分摊或核算记录，不能撤销归属；请通过分摊调整与差额核算处理");
        }
        jdbc.update("UPDATE oa_commission_attribution SET status=?,revision=revision+1,update_time=CURRENT_TIMESTAMP WHERE id=?",confirm?"confirmed":"draft",id);
        history("attribution",id,confirm?"确认并冻结归属":"撤销归属确认",Map.of("reason",required(b.get("reason"),1000)));
    }
    public List<Map<String,Object>> receipts() {
        require("access");
        return rows("""
                SELECT r.*,c.customer_id,c.name AS contract_name,m.name AS customer_name,m.owner_user_id,
                COALESCE((SELECT SUM(x.amount) FROM oa_commission_allocation x WHERE x.receipt_id=r.id),0) AS allocated_amount
                FROM oa_crm_contract_receipt r JOIN oa_crm_contract c ON c.id=r.contract_id
                JOIN oa_merchant m ON m.id=c.customer_id WHERE r.amount>0 AND c.tenant_id=? AND m.tenant_id=? ORDER BY r.receipt_date DESC,r.id DESC
                """,tenant(),tenant()).stream().filter(r->canAccessOwner(r.get("ownerUserId"))).toList();
    }
    Map<String,Object> receipt(long id) {
        return one("""
                SELECT r.*,c.customer_id FROM oa_crm_contract_receipt r JOIN oa_crm_contract c ON c.id=r.contract_id
                WHERE r.id=? AND r.amount>0 AND c.tenant_id=?
                """,id,tenant());
    }
    public Map<String,Object> allocation(long receiptId) {
        require("access"); var source=receipt(receiptId); checkCustomer(number(source.get("customerId")));
        return Map.of("receipt",source,"rows",rows("SELECT * FROM oa_commission_allocation WHERE receipt_id=? ORDER BY id",receiptId),
                "history",rows("SELECT * FROM oa_commission_history WHERE tenant_id=? AND entity_type='allocation' AND entity_id=? ORDER BY id DESC",tenant(),receiptId));
    }
    @Transactional public void allocate(long receiptId,Map<String,Object>b) {
        require("allocate"); mutex(); var receipt=receipt(receiptId); checkCustomer(number(receipt.get("customerId")));
        var requested=list(b.get("rows")); var seen=new HashSet<Long>(); BigDecimal sum=BigDecimal.ZERO;
        if(!text(b.get("signature")).equals(hash(rows("SELECT * FROM oa_commission_allocation WHERE receipt_id=? ORDER BY id",receiptId))))
            throw new ApiException(409,"回款分摊已变化，请刷新后重试");
        LocalDate date=day(b.get("allocationDate"));
        if(date.isAfter(today()) || date.isBefore(day(receipt.get("receiptDate")))) throw new ApiException(400,"分摊日期须在实际到账日期至今天之间");
        for(var input:requested) {
            long id=number(input.get("attributionId")); var a=attribution(id); expect(a,"confirmed");
            if("contract".equals(a.get("sourceType")))throw new ApiException(400,"销售按合同净回款自动核算，无需分摊至销售归属");
            var plan=contractPlan(number(a.get("contractId")));
            if(!plan.isEmpty() && number(a.get("parentId"))!=number(plan.get("id")))throw new ApiException(409,"合同与交付归属不一致");
            if(!seen.add(id) || number(a.get("contractId"))!=number(receipt.get("contractId")) || !text(a.get("termKey")).equals(text(receipt.get("termKey"))))
                throw new ApiException(400,"分摊须关联相同合同、收费条款的唯一招聘成果");
            BigDecimal amount=money(input.get("amount")); if(amount.signum()<=0) throw new ApiException(400,"分摊金额须大于零");
            sum=sum.add(amount);
        }
        if(sum.compareTo(money(receipt.get("amount")))>0) throw new ApiException(409,"分摊合计超过原实际到账金额");
        String reason=required(b.get("reason"),1000);
        jdbc.update("DELETE FROM oa_commission_allocation WHERE receipt_id=?",receiptId);
        for(var input:requested) jdbc.update("INSERT INTO oa_commission_allocation(tenant_id,receipt_id,attribution_id,amount,allocation_date) VALUES(?,?,?,?,?)",
                tenant(),receiptId,number(input.get("attributionId")),money(input.get("amount")),date);
        history("allocation",receiptId,"调整回款分摊",Map.of("rows",requested,"reason",reason,"date",date.toString()));
    }
    public String allocationSignature(long receiptId) {
        allocation(receiptId); return hash(rows("SELECT * FROM oa_commission_allocation WHERE receipt_id=? ORDER BY id",receiptId));
    }

    // A positive source is counted once. Refund/reversal rows reduce its allocations pro rata.
    BigDecimal netAllocated(Map<String,Object> allocation,LocalDate cutoff,List<Object> evidence) {
        var r=receipt(number(allocation.get("receiptId")));
        if(day(r.get("receiptDate")).isAfter(cutoff) || day(allocation.get("allocationDate")).isAfter(cutoff)) return BigDecimal.ZERO;
        var unlinked=rows("""
                SELECT r.id FROM oa_crm_contract_receipt r WHERE r.contract_id=? AND r.term_key=? AND r.amount<0 AND r.receipt_date<=?
                AND NOT EXISTS(SELECT 1 FROM oa_finance_record f WHERE f.receipt_id=r.id AND f.status='confirmed' AND f.original_receipt_id IS NOT NULL)
                """,r.get("contractId"),r.get("termKey"),cutoff);
        if(!unlinked.isEmpty()) throw new ApiException(409,"合同存在未关联原到账的负向流水，须先核对后才能计算提成");
        BigDecimal refunded=jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount),0) FROM oa_finance_record WHERE original_receipt_id=?
                AND record_type IN ('refund','reversal') AND status='confirmed' AND business_date<=?
                """,BigDecimal.class,r.get("id"),cutoff);
        BigDecimal original=money(r.get("amount")), net=original.subtract(refunded);
        if(net.signum()<0) throw new ApiException(409,"原到账的累计退款冲销超过到账金额");
        evidence.add(List.of(allocation,r.get("amount"),r.get("receiptDate"),refunded));
        return money(allocation.get("amount")).multiply(net).divide(original,8,RoundingMode.HALF_UP);
    }
    List<Map<String,Object>> attributions() {
        return rows("SELECT * FROM oa_commission_attribution WHERE tenant_id=? AND status='confirmed' ORDER BY id",tenant())
                .stream().filter(r->{try {checkCustomer(number(r.get("customerId")));return true;}catch(ApiException e){if(e.code()==403)return false;throw e;}}).toList();
    }
    BigDecimal reserved(long attribution,long user,String side,long excluded) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(l.amount),0) FROM oa_commission_line l JOIN oa_commission_batch b ON b.id=l.batch_id
                WHERE l.attribution_id=? AND l.user_id=? AND l.side=? AND b.id<>?
                AND b.status IN ('draft','approving','approved','linked')
                """,BigDecimal.class,attribution,user,side,excluded);
    }
    List<BigDecimal> split(BigDecimal total,List<Map<String,Object>> team) {
        if(team.isEmpty()) return List.of();
        var amounts=new ArrayList<BigDecimal>();var remainder=new ArrayList<BigDecimal>();BigDecimal assigned=BigDecimal.ZERO;
        for(var person:team) {
            var exact=total.multiply(decimal(person.get("share"))).divide(HUNDRED,8,RoundingMode.HALF_UP);
            var amount=exact.setScale(2,RoundingMode.DOWN);amounts.add(amount);remainder.add(exact.subtract(amount));assigned=assigned.add(amount);
        }
        var order=new ArrayList<Integer>();for(int i=0;i<team.size();i++) order.add(i);
        order.sort(Comparator.<Integer,BigDecimal>comparing(remainder::get).reversed().thenComparingLong(i->number(team.get(i).get("userId"))));
        int cents=total.subtract(assigned).movePointRight(2).intValueExact();
        for(int i=0;i<cents;i++) {int index=order.get(i%order.size());amounts.set(index,amounts.get(index).add(new BigDecimal("0.01")));}
        return amounts;
    }
    record Calculation(List<Map<String,Object>> lines,List<Map<String,Object>> overview,String signature) {}
    Calculation calculate(LocalDate cutoff,long excluded) { return calculate(cutoff,excluded,null); }
    Calculation calculate(LocalDate cutoff,long excluded,Set<Long> frozenScope) {return calculate(cutoff,excluded,frozenScope,null);}
    Calculation calculate(LocalDate cutoff,long excluded,Set<Long> frozenScope,Long selfUser) {
        var lines=new ArrayList<Map<String,Object>>();var overview=new ArrayList<Map<String,Object>>();var evidence=new ArrayList<Object>();
        var budgets=new HashMap<Long,Map<Long,BigDecimal>>();
        var attributions=selfUser==null?attributions():rows("SELECT * FROM oa_commission_attribution WHERE tenant_id=? AND status='confirmed' ORDER BY id",tenant()).stream().filter(a->list(a.get("participantsJson")).stream().anyMatch(p->number(p.get("userId"))==selfUser)).toList();
        for(var attribution:attributions) {
            long id=number(attribution.get("id")); if(frozenScope!=null && !frozenScope.contains(id)) continue;
            if(day(attribution.get("basisDate")).isAfter(cutoff)) continue;
            boolean sales="contract".equals(attribution.get("sourceType")),acceptance="acceptance".equals(attribution.get("sourceType"));
            boolean delivery=number(attribution.get("parentId"))>0;
            var contract=contract(number(attribution.get("contractId")),selfUser==null);
            Map<String,Object> a;
            if(sales||acceptance) {
                var c=contract;
                a=new LinkedHashMap<>();a.put("id",0L);a.put("contractId",c.get("id"));a.put("customerName",c.get("customerName"));a.put("contractName",c.get("name"));a.put("jobTitle",sales?"合同销售业绩":"服务验收");a.put("talentName",sales?"":text(attribution.get("acceptanceReference")));a.put("guaranteeStatus",acceptance?"completed":"cash");a.put("guaranteeEnd",attribution.get("basisDate"));
            } else a=application(number(attribution.get("applicationId")),selfUser==null);
            if(!sales && !acceptance && (number(attribution.get("contractId"))!=number(a.get("contractId")) || !text(attribution.get("termKey")).equals(text(a.get("guaranteeTermKey")))
                    || !day(attribution.get("basisDate")).equals(day(a.get("actualEntryDate")))))
                throw new ApiException(409,"招聘成果 #"+a.get("id")+" 的入职依据变化，须先核对归属和分摊");
            var rule=object(attribution.get("ruleJson")); var sources=rows("SELECT * FROM oa_commission_allocation WHERE attribution_id=? ORDER BY receipt_id",id);
            var basis=new ArrayList<Object>(); BigDecimal cash=BigDecimal.ZERO;
            for(var source:sources) cash=cash.add(netAllocated(source,cutoff,basis));
            if(sales)cash=contractCash(number(attribution.get("contractId")),cutoff,basis);
            BigDecimal pool=cash.multiply(decimal(rule.get("poolRate"))).divide(HUNDRED,8,RoundingMode.HALF_UP);
            boolean complete="completed".equals(a.get("guaranteeStatus")) && a.get("guaranteeEnd")!=null && !day(a.get("guaranteeEnd")).isAfter(cutoff);
            boolean active="active".equals(a.get("guaranteeStatus"));
            boolean previouslyCompleted=rows("SELECT l.snapshot_json FROM oa_commission_line l JOIN oa_commission_batch b ON b.id=l.batch_id WHERE l.attribution_id=? AND b.id<>? AND b.status IN ('draft','approving','approved','linked')",id,excluded)
                    .stream().anyMatch(l->"completed".equals(object(l.get("snapshotJson")).get("guaranteeStatus")));
            var participants=list(attribution.get("participantsJson")); var personRows=new ArrayList<Map<String,Object>>();
            BigDecimal allTotal=money(pool);
            BigDecimal developmentTotal=money((sales||delivery?allTotal:pool).multiply(decimal(rule.get("developmentShare"))).divide(HUNDRED,8,RoundingMode.HALF_UP));
            BigDecimal deliveryTotal=delivery?budgets.computeIfAbsent(number(attribution.get("contractId")),c->deliveryBudgets(c,cutoff)).getOrDefault(id,BigDecimal.ZERO):allTotal.subtract(developmentTotal);
            for(String side:List.of("development","delivery")) {
                if(sales && !"development".equals(side) || delivery && !"delivery".equals(side))continue;
                var team=participants.stream().filter(p->side.equals(p.get("side"))).toList();
                BigDecimal teamTotal="development".equals(side)?developmentTotal:deliveryTotal;
                var teamAmounts=split(teamTotal,team);
                for(int i=0;i<team.size();i++) {
                    var p=team.get(i);long user=number(p.get("userId"));
                    if(selfUser!=null && user!=selfUser)continue;
                    BigDecimal total=teamAmounts.get(i);
                    BigDecimal previous=reserved(id,user,side,excluded);
                    BigDecimal eligible=complete?total:active?money(total.multiply(decimal(rule.get("initialRelease"))).divide(HUNDRED,8,RoundingMode.HALF_UP)):(previouslyCompleted?total:money(total.multiply(decimal(rule.get("initialRelease"))).divide(HUNDRED,8,RoundingMode.HALF_UP))).min(previous.max(BigDecimal.ZERO));
                    if(sales)eligible=total;
                    BigDecimal amount=eligible.subtract(previous);
                    var snapshot=new LinkedHashMap<String,Object>();
                    snapshot.putAll(p); snapshot.put("applicationId",a.get("id"));snapshot.put("customerName",a.get("customerName"));snapshot.put("jobTitle",a.get("jobTitle"));snapshot.put("talentName",a.get("talentName"));
                    snapshot.put("contractId",attribution.get("contractId"));snapshot.put("contractName",contract.get("name"));snapshot.put("sourceType",attribution.get("sourceType"));
                    snapshot.put("rule",rule);snapshot.put("netAllocated",money(cash));snapshot.put("totalCommission",total);snapshot.put("eligible",eligible);snapshot.put("reserved",previous);
                    snapshot.put("held",total.subtract(eligible));snapshot.put("guaranteeStatus",text(a.get("guaranteeStatus"))); snapshot.put("cutoffDate",cutoff.toString());
                    snapshot.put("holdReason",complete?"已完成保证期":active?"保证期未确认结束，按首期比例释放":"交付异常，暂缓新增释放，实际退款仍调整");
                    if(sales)snapshot.put("holdReason",cash.signum()==0?"待实际回款":"销售回款条件已满足");
                    if(acceptance)snapshot.put("holdReason","服务已验收，按分摊净回款结算");
                    personRows.add(snapshot);
                    if(amount.signum()!=0) lines.add(Map.of("attributionId",id,"userId",user,"side",side,"amount",amount,"snapshot",snapshot));
                }
            }
            evidence.add(List.of(attribution,basis,text(a.get("guaranteeStatus")),text(a.get("guaranteeEnd")),personRows));
            var view=new LinkedHashMap<String,Object>();view.putAll(Map.of("id",id,"applicationId",a.get("id"),"customerName",a.get("customerName"),"jobTitle",a.get("jobTitle"),"talentName",a.get("talentName"),
                    "ruleName",rule.get("name"),"netAllocated",money(cash),"participants",personRows,"guaranteeStatus",text(a.get("guaranteeStatus"))));view.put("contractId",attribution.get("contractId"));view.put("customerId",attribution.get("customerId"));view.put("contractName",contract.get("name"));view.put("sourceType",attribution.get("sourceType"));overview.add(view);
        }
        return new Calculation(lines,overview,hash(evidence));
    }
    public List<Map<String,Object>> overview(String cutoff) { require("access"); return calculate(cutoff==null||cutoff.isBlank()?today():day(cutoff),0).overview(); }
    public List<Map<String,Object>> batches() {
        require("access");
        return rows("""
                SELECT b.*,COALESCE(SUM(l.amount),0) AS amount,COUNT(l.id) AS line_count FROM oa_commission_batch b
                LEFT JOIN oa_commission_line l ON l.batch_id=b.id WHERE b.tenant_id=? GROUP BY b.id ORDER BY b.period DESC,b.create_time DESC,b.id DESC
                """,tenant()).stream().filter(b->{try {batchAccess(number(b.get("id")));return true;}catch(ApiException e){if(e.code()==403)return false;throw e;}}).toList();
    }
    Map<String,Object> batch(long id) { return one("SELECT * FROM oa_commission_batch WHERE id=? AND tenant_id=?",id,tenant()); }
    Set<Long> frozenScope(Map<String,Object> b) {
        try { return new LinkedHashSet<>(mapper.readValue(text(b.get("scopeJson")),new TypeReference<List<Long>>(){})); }
        catch(Exception e) { throw new ApiException(409,"核算范围无法校验，请重新核算"); }
    }
    void batchAccess(long id) {
        var b=batch(id);
        for(long attributionId:frozenScope(b)) attribution(attributionId);
        for(var r:rows("SELECT DISTINCT a.customer_id FROM oa_commission_line l JOIN oa_commission_attribution a ON a.id=l.attribution_id WHERE l.batch_id=?",id))
            checkCustomer(number(r.get("customerId")));
    }
    public Map<String,Object> detail(long id) {
        require("access"); batchAccess(id); var b=batch(id);
        var lines=rows("""
                SELECT l.*,u.nick_name AS employee_name,p.net_amount,p.payroll_id,pb.status AS payroll_status,
                COALESCE((SELECT SUM(x.amount) FROM oa_hr_payment x WHERE x.item_id=l.payroll_item_id AND x.status='paid'),0) AS payroll_paid
                FROM oa_commission_line l LEFT JOIN sys_user u ON u.user_id=l.user_id
                LEFT JOIN oa_hr_payroll_item p ON p.id=l.payroll_item_id LEFT JOIN oa_hr_payroll pb ON pb.id=p.payroll_id WHERE l.batch_id=? ORDER BY l.id
                """,id);
        for(var l:lines) {
            var snapshot=object(l.remove("snapshotJson"));l.put("snapshot",snapshot);l.put("employeeName",snapshot.get("name"));
            l.put("paymentStatus",number(l.get("payrollItemId"))==0?"unlinked":"approved".equals(l.get("payrollStatus")) && decimal(l.get("payrollPaid")).compareTo(decimal(l.get("netAmount")))==0?"paid":"unpaid");
        }
        b.put("lines",lines); b.put("history",rows("SELECT * FROM oa_commission_history WHERE tenant_id=? AND entity_type='batch' AND entity_id=? ORDER BY id DESC",tenant(),id));
        return b;
    }
    @Transactional public Map<String,Object> generate(long id,Map<String,Object>b) {
        require("generate");mutex();
        YearMonth period;try{period=YearMonth.parse(text(b.get("period")));}catch(Exception e){throw new ApiException(400,"核算月份格式错误");}
        LocalDate cutoff=day(b.get("cutoffDate"));
        if(!YearMonth.from(cutoff).equals(period) || cutoff.isAfter(today())) throw new ApiException(400,"截止日期须属于核算月份且不能晚于今天");
        String reason=required(b.get("reason"),1000);
        if(id>0){batchAccess(id);var old=batch(id);version(old,b);expect(old,"draft","rejected");}
        if(count("SELECT COUNT(*) FROM oa_commission_batch WHERE tenant_id=? AND id<>? AND status IN ('draft','approving')",tenant(),id)>0)
            throw new ApiException(409,"已有未完成的核算草稿或审批，请先批准、退回重算或作废后再生成");
        var latest=jdbc.queryForObject("SELECT MAX(cutoff_date) FROM oa_commission_batch WHERE tenant_id=? AND id<>? AND status IN ('approved','linked')",java.sql.Date.class,tenant(),id);
        if(latest!=null && cutoff.isBefore(latest.toLocalDate())) throw new ApiException(409,"核算截止日不能早于已批准批次，历史更正请在后续批次调整");
        if(count("SELECT COUNT(*) FROM oa_commission_allocation x WHERE x.tenant_id=? AND x.allocation_date>? AND EXISTS(SELECT 1 FROM oa_commission_line l JOIN oa_commission_batch b ON b.id=l.batch_id WHERE l.attribution_id=x.attribution_id AND b.status IN ('approved','linked'))",tenant(),cutoff)>0)
            throw new ApiException(409,"已核算业务的分摊在截止日后有调整，请使用调整日或之后的截止日重新核算");
        var calc=calculate(cutoff,id);
        if(calc.lines().isEmpty()) throw new ApiException(409,"没有可结算差额；销售请核对合同归属及实际回款，交付还需核对分摊与入职或验收条件");
        String scopeJson=json(attributions().stream().filter(a->!day(a.get("basisDate")).isAfter(cutoff)).map(a->a.get("id")).toList());
        if(id==0) id=insert("INSERT INTO oa_commission_batch(tenant_id,period,cutoff_date,signature,reason,create_by,scope_json) VALUES(?,?,?,?,?,?,?)",tenant(),period.toString(),cutoff,calc.signature(),reason,actor(),scopeJson);
        else {
            jdbc.update("DELETE FROM oa_commission_line WHERE batch_id=?",id);
            jdbc.update("UPDATE oa_commission_batch SET period=?,cutoff_date=?,signature=?,reason=?,status='draft',revision=revision+1,workflow_request_id=NULL,update_time=CURRENT_TIMESTAMP WHERE id=?",
                    period.toString(),cutoff,calc.signature(),reason,id);
        }
        jdbc.update("UPDATE oa_commission_batch SET scope_json=? WHERE id=?",scopeJson,id);
        for(var l:calc.lines()) jdbc.update("INSERT INTO oa_commission_line(batch_id,attribution_id,user_id,side,amount,snapshot_json) VALUES(?,?,?,?,?,?)",
                id,l.get("attributionId"),l.get("userId"),l.get("side"),l.get("amount"),json(l.get("snapshot")));
        history("batch",id,"生成核算差额",b);return detail(id);
    }
    void fresh(long id) {
        var b=batch(id); var calc=calculate(day(b.get("cutoffDate")),id,frozenScope(b));
        if(!text(b.get("signature")).equals(calc.signature())) throw new ApiException(409,"归属、回款、退款、保证期或已占用提成已变化，请重新核算");
        if(!hash(rows("SELECT attribution_id,user_id,side,amount FROM oa_commission_line WHERE batch_id=? ORDER BY attribution_id,side,user_id",id))
                .equals(hash(calc.lines().stream().map(l->Map.of("attributionId",l.get("attributionId"),"userId",l.get("userId"),"side",l.get("side"),"amount",l.get("amount")))
                        .sorted(Comparator.<Map<String,Object>>comparingLong(l->number(l.get("attributionId"))).thenComparing(l->text(l.get("side"))).thenComparingLong(l->number(l.get("userId")))).toList())))
            throw new ApiException(409,"核算明细已变化，请重新核算");
    }
    public void validateWorkflow(WorkflowActionContext c) {
        if(c.moduleId()!=MODULE || !"submit".equals(c.actionCode())) throw new ApiException(400,"提成审批动作不匹配");
        mutex();long id=number(c.payload().get("batchId"));batchAccess(id);var b=batch(id);
        if(c.requestId()==0){expect(b,"draft","rejected");version(b,c.payload());}
        else if(!"approving".equals(b.get("status")) || number(b.get("workflowRequestId"))!=c.requestId()) throw new ApiException(409,"审批申请已变化");
        fresh(id);
    }
    public void submitted(WorkflowActionContext c) {
        validateWorkflow(new WorkflowActionContext(0,c.moduleId(),c.actionCode(),c.handlerKey(),c.config(),c.payload(),c.operatorId(),c.operatorName()));long id=number(c.payload().get("batchId"));
        jdbc.update("UPDATE oa_commission_batch SET status='approving',workflow_request_id=?,revision=revision+1 WHERE id=?",c.requestId(),id);
        // Frozen application details are rendered by the common approval detail page.
        var d=detail(id); c.payload().put("commissionBatch",true);c.payload().put("period",d.get("period"));c.payload().put("lines",d.get("lines"));
        c.payload().put("title","提成核算："+d.get("period")+" / #"+id);
        history("batch",id,"提交提成审批",Map.of("requestId",c.requestId()));
    }
    public Object approve(WorkflowActionContext c) {
        validateWorkflow(c);long id=number(c.payload().get("batchId"));
        jdbc.update("UPDATE oa_commission_batch SET status='approved',revision=revision+1 WHERE id=?",id);
        history("batch",id,"批准提成核算",Map.of("requestId",c.requestId()));return Map.of("batchId",id);
    }
    public void decision(WorkflowActionContext c,boolean pass,String comment,Long operator) {
        mutex();if(!WorkflowApprovalContext.active(operator)&&!system.hasModuleAction(operator,MODULE,"approve"))throw new ApiException(403,"审批人缺少提成审批权限");long id=number(c.payload().get("batchId"));batchAccess(id);
        if(number(batch(id).get("createBy"))==operator&&!WorkflowApprovalContext.allowsSelfApproval(operator)) throw new ApiException(403,"不能审批本人提交的提成核算");
        if(!pass && text(comment).isBlank()) throw new ApiException(400,"退回须填写原因");
        // A stale application must still be rejectable so it can be recalculated.
        if(pass) validateWorkflow(c);
    }
    public void rejected(WorkflowActionContext c,String status) {
        if(!Set.of("审批未通过","rejected","cancelled","canceled","withdrawn").contains(status))return;
        mutex();long id=number(c.payload().get("batchId"));
        if(number(batch(id).get("workflowRequestId"))!=c.requestId())return;
        jdbc.update("UPDATE oa_commission_batch SET status='rejected',revision=revision+1 WHERE id=?",id);
        history("batch",id,"审批退回",Map.of("requestId",c.requestId()));
    }
    @Transactional public void voidBatch(long id,Map<String,Object>b) {
        require("void");mutex();batchAccess(id);var old=batch(id);version(old,b);expect(old,"draft","rejected");
        jdbc.update("UPDATE oa_commission_batch SET status='void',revision=revision+1 WHERE id=?",id);
        history("batch",id,"作废核算",Map.of("reason",required(b.get("reason"),1000)));
    }
    @Transactional public Map<String,Object> toPayroll(long id,Map<String,Object>b) {
        require("payroll");mutex();batchAccess(id);var old=batch(id);
        if(number(old.get("payrollId"))>0)return Map.of("payrollId",old.get("payrollId"));
        version(old,b);expect(old,"approved");
        // Approved entitlements are immutable. New refunds/releases are handled by a new delta batch.
        var totals=new TreeMap<Long,BigDecimal>();
        for(var l:rows("SELECT * FROM oa_commission_line WHERE batch_id=? ORDER BY id",id))
            totals.merge(number(l.get("userId")),money(l.get("amount")),BigDecimal::add);
        long payrollId=payroll.createCommissionPayroll(text(old.get("period")),id,totals);
        for(var row:rows("SELECT id,user_id FROM oa_hr_payroll_item WHERE payroll_id=?",payrollId))
            jdbc.update("UPDATE oa_commission_line SET payroll_item_id=? WHERE batch_id=? AND user_id=?",row.get("id"),id,row.get("userId"));
        jdbc.update("UPDATE oa_commission_batch SET status='linked',payroll_id=?,revision=revision+1 WHERE id=?",payrollId,id);
        history("batch",id,"纳入专属工资批次",Map.of("payrollId",payrollId));
        return Map.of("payrollId",payrollId);
    }
    public List<Map<String,Object>> selfOverview() {
        if(!system.hasModuleAccess(actor(),341006))throw new ApiException(403,"没有本人薪资查看权限");
        var calculated=calculate(today(),0,null,actor()).overview();var result=new ArrayList<Map<String,Object>>();
        for(var attribution:rows("SELECT * FROM oa_commission_attribution WHERE tenant_id=? ORDER BY id DESC",tenant())) {
            for(var person:list(attribution.get("participantsJson"))) {
                if(number(person.get("userId"))!=actor())continue;
                var c=contract(number(attribution.get("contractId")),false);String side=text(person.get("side"));
                Map<String,Object> progress=calculated.stream().filter(v->number(v.get("id"))==number(attribution.get("id"))).flatMap(v->list(v.get("participants")).stream()).filter(p->side.equals(p.get("side"))).findFirst().orElse(Map.of());
                BigDecimal total=money(progress.get("totalCommission")),eligible=money(progress.get("eligible")),reserved=money(progress.get("reserved"));
                BigDecimal expected=total;
                if("contract".equals(attribution.get("sourceType"))) {
                    var rule=object(attribution.get("ruleJson"));BigDecimal pool=money(decimal(c.get("effectiveAmount")).multiply(decimal(rule.get("poolRate"))).divide(HUNDRED,8,RoundingMode.HALF_UP));
                    var team=list(attribution.get("participantsJson")).stream().filter(p->side.equals(p.get("side"))).toList();
                    var amounts=split(money(pool.multiply(decimal(rule.get("developmentShare"))).divide(HUNDRED,8,RoundingMode.HALF_UP)),team);
                    for(int i=0;i<team.size();i++)if(number(team.get(i).get("userId"))==actor())expected=amounts.get(i);
                }
                String state=!"confirmed".equals(attribution.get("status"))?"归属待确认":eligible.subtract(reserved).signum()!=0?"待核算":money(progress.get("netAllocated")).signum()==0?"待回款":money(progress.get("held")).signum()>0?"待交付条件":"已核算";
                var pending=rows("SELECT DISTINCT b.status FROM oa_commission_line l JOIN oa_commission_batch b ON b.id=l.batch_id WHERE l.attribution_id=? AND l.user_id=? AND l.side=? AND b.status IN ('draft','approving','approved','linked') ORDER BY b.status",attribution.get("id"),actor(),side);
                if("已核算".equals(state)) {
                    if(pending.stream().anyMatch(b->Set.of("draft","approving").contains(text(b.get("status")))))state="待审批";
                    else if(!pending.isEmpty())state="已批准，查看发放明细";
                }
                var row=new LinkedHashMap<String,Object>();row.put("id",attribution.get("id")+":"+side);row.put("contractName",c.get("name"));row.put("customerName",c.get("customerName"));row.put("side",side);row.put("expectedAmount",expected);row.put("earnedAmount",eligible);row.put("pendingAmount",eligible.subtract(reserved));row.put("heldAmount",money(progress.get("held")));row.put("state",state);row.put("reason",progress.getOrDefault("holdReason",text(attribution.get("sourceType")).equals("contract")?"待确认销售归属":"待入职或验收并确认归属"));result.add(row);
                if(eligible.subtract(reserved).signum()<0)row.put("reason","退款或分摊调整，待核算扣回");
            }
        }
        return result;
    }
    public List<Map<String,Object>> self() {
        if(!system.hasModuleAccess(actor(),341006))throw new ApiException(403,"没有本人薪资查看权限");
        var result=rows("""
                SELECT l.id,l.amount,l.side,l.snapshot_json,b.period,b.id AS batch_id,b.status,b.payroll_id,
                l.payroll_item_id,p.net_amount,pb.status AS payroll_status,
                COALESCE((SELECT SUM(x.amount) FROM oa_hr_payment x WHERE x.item_id=l.payroll_item_id AND x.status='paid'),0) AS payroll_paid
                FROM oa_commission_line l JOIN oa_commission_batch b ON b.id=l.batch_id
                LEFT JOIN oa_hr_payroll_item p ON p.id=l.payroll_item_id LEFT JOIN oa_hr_payroll pb ON pb.id=p.payroll_id
                WHERE b.tenant_id=? AND l.user_id=? AND b.status IN ('approved','linked') ORDER BY l.id DESC
                """,tenant(),actor());
        for(var r:result) {
            var s=object(r.remove("snapshotJson")); s.remove("rule");s.remove("reserved");r.put("snapshot",s);
            r.put("paymentStatus",number(r.get("payrollItemId"))==0?"待纳入工资":"approved".equals(r.get("payrollStatus")) && decimal(r.get("netAmount")).compareTo(decimal(r.get("payrollPaid")))==0?"已结清":"工资待结清");
        }
        return result;
    }
    void history(String type,long id,String action,Object detail) {
        jdbc.update("INSERT INTO oa_commission_history(tenant_id,entity_type,entity_id,action,detail_json,operator_id) VALUES(?,?,?,?,?,?)",tenant(),type,id,action,json(detail),actor());
    }
    List<Map<String,Object>> rows(String sql,Object...args){return JdbcMaps.camelCase(jdbc.queryForList(sql,args));}
    Map<String,Object> one(String sql,Object...args){var r=rows(sql,args);if(r.isEmpty())throw new ApiException(404,"记录不存在或不属于当前企业");return r.get(0);}
    long count(String sql,Object...args){return jdbc.queryForObject(sql,Long.class,args);}
    long insert(String sql,Object...args){var key=new GeneratedKeyHolder();jdbc.update(c->{var p=c.prepareStatement(sql,new String[]{"id"});for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p;},key);return Objects.requireNonNull(key.getKey()).longValue();}
    void version(Map<String,Object>old,Map<String,Object>b){if(!b.containsKey("revision")||number(old.get("revision"))!=number(b.get("revision")))throw new ApiException(409,"数据已变化，请刷新重试");}
    void expect(Map<String,Object>r,String...states){if(!Set.of(states).contains(text(r.get("status"))))throw new ApiException(409,"当前状态不允许操作");}
    String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new ApiException(400,"数据格式错误");}}
    String hash(Object value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(mapper.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(value)));}catch(Exception e){throw new ApiException(400,"数据无法校验");}}
    Map<String,Object> object(Object value){try{if(value instanceof Map<?,?>m){var r=new LinkedHashMap<String,Object>();m.forEach((k,v)->r.put(String.valueOf(k),v));return r;}return mapper.readValue(text(value),new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw new ApiException(400,"数据格式错误");}}
    List<Map<String,Object>> list(Object value){try{if(value instanceof String s)return mapper.readValue(s,new TypeReference<List<Map<String,Object>>>(){});if(value instanceof List<?> l)return l.stream().map(this::object).toList();return List.of();}catch(Exception e){throw new ApiException(400,"明细格式错误");}}
    static String text(Object value){return value==null?"":String.valueOf(value);}
    static long number(Object value){try{return Long.parseLong(text(value));}catch(Exception e){return 0;}}
    static BigDecimal decimal(Object value){try{return value==null?BigDecimal.ZERO:new BigDecimal(text(value));}catch(Exception e){throw new ApiException(400,"金额或比例格式错误");}}
    static BigDecimal money(Object value){return decimal(value).setScale(2,RoundingMode.HALF_UP);}
    static BigDecimal rate(Object value){if(value==null)throw new ApiException(400,"请明确填写比例");var r=decimal(value);if(r.signum()<0||r.compareTo(HUNDRED)>0||r.scale()>4)throw new ApiException(400,"比例须在0到100之间，最多4位小数");return r;}
    static LocalDate day(Object value){try{return LocalDate.parse(text(value).substring(0,10));}catch(Exception e){throw new ApiException(400,"日期格式错误");}}
    static LocalDate today(){return LocalDate.now(ZoneId.of("Asia/Shanghai"));}
    static String required(Object value,int max){String s=text(value).trim();if(s.isBlank()||s.length()>max)throw new ApiException(400,"必填内容为空或超过长度限制");return s;}
    static String kind(Object value){String s=text(value);if(!Set.of("first","repeat").contains(s))throw new ApiException(400,"请选择新客首单或老客续单");return s;}
}
