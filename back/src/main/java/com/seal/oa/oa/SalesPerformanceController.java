package com.seal.oa.oa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.ApiResult;
import com.seal.oa.system.DataScopeService;
import com.seal.oa.system.MemberIdentity;
import com.seal.oa.system.SystemService;
import com.seal.oa.web.RequiresModuleAction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/project/crm/sales-performance")
public class SalesPerformanceController {
    private static final long MODULE_ID = 341112L;
    private static final long SALES_DEPARTMENT_ID = 326L;
    private static final TypeReference<Map<String,Object>> JSON_MAP = new TypeReference<>() {};
    private final JdbcTemplate jdbc;
    private final DataScopeService scopes;
    private final SystemService system;
    private final ObjectMapper json;

    public SalesPerformanceController(JdbcTemplate jdbc, DataScopeService scopes, SystemService system, ObjectMapper json) {
        this.jdbc=jdbc; this.scopes=scopes; this.system=system; this.json=json;
    }

    @GetMapping
    @RequiresModuleAction(moduleId=MODULE_ID, action="access")
    public Map<String,Object> report(@RequestParam(required=false) String from, @RequestParam(required=false) String to) {
        LocalDate end=parse(to,LocalDate.now()), start=parse(from,end.withDayOfMonth(1));
        if(start.isAfter(end)||ChronoUnit.DAYS.between(start,end)>365) throw new ApiException(400,"Invalid reporting period");
        long actor=AuthContext.userId(); long tenant=system.requireCurrentUser().tenantId();
        List<Map<String,Object>> members=jdbc.queryForList("""
                SELECT u.user_id,u.nick_name,u.dept_id,d.dept_name FROM sys_user u
                JOIN sys_dept d ON d.dept_id=u.dept_id AND d.tenant_id=u.tenant_id
                WHERE u.tenant_id=? AND u.dept_id=? AND u.del_flag='0' AND u.status='0' AND %s
                  AND NOT EXISTS (SELECT 1 FROM sys_dept_principal dp WHERE dp.dept_id=u.dept_id AND dp.user_id=u.user_id)
                ORDER BY u.user_id
                """.formatted(MemberIdentity.condition("u")),tenant,SALES_DEPARTMENT_ID).stream()
                .filter(u->scopes.canAccess(actor,u.get("dept_id"),u.get("user_id"))).toList();
        if(members.isEmpty()) throw new ApiException(403,"No sales members in current data scope");
        String manager=jdbc.queryForList("""
                SELECT u.nick_name FROM sys_dept_principal dp
                JOIN sys_user u ON u.user_id=dp.user_id AND u.tenant_id=? AND u.del_flag='0' AND u.status='0'
                JOIN sys_dept d ON d.dept_id=dp.dept_id AND d.tenant_id=u.tenant_id
                WHERE dp.dept_id=? AND %s
                """.formatted(MemberIdentity.condition("u")),tenant,SALES_DEPARTMENT_ID).stream()
                .map(row->Objects.toString(row.get("nick_name"),"")).findFirst().orElse("");
        Map<Long,Map<String,Object>> people=new LinkedHashMap<>();
        for(var m:members){
            long id=number(m.get("user_id")); Map<String,Object> p=new LinkedHashMap<>();
            p.put("userId",id);p.put("name",m.get("nick_name"));p.put("department",m.get("dept_name"));
            p.put("customerCount",0);p.put("newCustomers",0);p.put("followups",0);p.put("overdueCustomers",0);
            p.put("activeOpportunities",0);p.put("stagnantOpportunities",0);p.put("pipelineAmount",BigDecimal.ZERO);
            p.put("signedContracts",0);p.put("signedAmount",BigDecimal.ZERO);p.put("receivedAmount",BigDecimal.ZERO);people.put(id,p);
        }
        String marks=String.join(",",Collections.nCopies(people.size(),"?")); List<Object> owners=new ArrayList<>(people.keySet());
        List<Object> customerArgs=new ArrayList<>(List.of(Date.valueOf(start),Date.valueOf(end.plusDays(1))));customerArgs.addAll(owners);customerArgs.add(tenant);
        jdbc.queryForList("""
                SELECT m.owner_user_id,COUNT(*) total,SUM(CASE WHEN m.create_time>=? AND m.create_time<? THEN 1 ELSE 0 END) new_total
                FROM oa_merchant m WHERE m.status='0' AND m.public_pool='0' AND m.owner_user_id IN (%s) AND m.tenant_id=? GROUP BY m.owner_user_id
                """.formatted(marks),customerArgs.toArray()).forEach(r->{
            Map<String,Object> p=people.get(number(r.get("owner_user_id")));p.put("customerCount",number(r.get("total")));p.put("newCustomers",number(r.get("new_total")));
        });
        List<Object> followupArgs=new ArrayList<>(List.of(Date.valueOf(start),Date.valueOf(end.plusDays(1))));followupArgs.addAll(owners);followupArgs.add(tenant);
        jdbc.queryForList("""
                SELECT f.owner_user_id,COUNT(*) total FROM oa_customer_followup f JOIN oa_merchant m ON m.id=f.merchant_id
                WHERE f.followup_time>=? AND f.followup_time<? AND f.owner_user_id IN (%s) AND m.tenant_id=? AND m.status='0' GROUP BY f.owner_user_id
                """.formatted(marks),followupArgs.toArray()).forEach(r->{if(people.containsKey(number(r.get("owner_user_id"))))people.get(number(r.get("owner_user_id"))).put("followups",number(r.get("total")));});
        List<Object> overdueArgs=new ArrayList<>();overdueArgs.add(tenant);overdueArgs.addAll(owners);overdueArgs.add(tenant);overdueArgs.addAll(owners);
        jdbc.queryForList("""
                SELECT m.owner_user_id,COUNT(*) total FROM oa_merchant m WHERE m.tenant_id=? AND m.status='0' AND m.public_pool='0' AND m.owner_user_id IN (%s)
                  AND EXISTS(SELECT 1 FROM oa_customer_followup f WHERE f.id=(SELECT f2.id FROM oa_customer_followup f2 WHERE f2.merchant_id=m.id ORDER BY f2.followup_time DESC,f2.id DESC LIMIT 1) AND f.next_followup_time<CURRENT_TIMESTAMP)
                GROUP BY m.owner_user_id
                UNION ALL
                SELECT m.owner_user_id,COUNT(*) total FROM oa_merchant m WHERE m.tenant_id=? AND m.status='0' AND m.public_pool='0' AND m.owner_user_id IN (%s)
                  AND NOT EXISTS(SELECT 1 FROM oa_customer_followup f WHERE f.merchant_id=m.id) GROUP BY m.owner_user_id
                """.formatted(marks,marks),overdueArgs.toArray()).forEach(r->{
            if(people.containsKey(number(r.get("owner_user_id")))){var p=people.get(number(r.get("owner_user_id")));p.put("overdueCustomers",number(p.get("overdueCustomers"))+number(r.get("total")));}
        });

        List<Map<String,Object>> opportunities=new ArrayList<>();
        for(var raw:jdbc.queryForList("SELECT id,payload_json,create_time,update_time FROM biz_record WHERE tenant_id=? AND module='/project/business/list'",tenant)){
            Map<String,Object> payload=parse(raw.get("payload_json"));long owner=number(payload.get("ownerUserId"));if(!people.containsKey(owner))continue;
            String stage=Objects.toString(payload.getOrDefault("stage",payload.get("businessState")),"");
            if(Set.of("\u8d62\u5355","\u5df2\u8d62\u5355","\u8f93\u5355","\u5df2\u5173\u95ed").contains(stage))continue;
            long days=ChronoUnit.DAYS.between(date(raw.get("update_time"),date(raw.get("create_time"),LocalDate.now())),LocalDate.now());
            BigDecimal amount=decimal(payload.getOrDefault("expectedAmount",payload.get("amount")));
            Map<String,Object> row=new LinkedHashMap<>();row.put("opportunityId",raw.get("id"));row.put("customerId",payload.get("customerId"));row.put("name",payload.getOrDefault("name","Unnamed opportunity"));
            row.put("customerName",payload.getOrDefault("customerName",""));row.put("stage",stage);row.put("ownerUserId",owner);row.put("ownerName",people.get(owner).get("name"));row.put("updatedDays",days);row.put("amount",amount);opportunities.add(row);
            Map<String,Object> p=people.get(owner);p.put("activeOpportunities",number(p.get("activeOpportunities"))+1);p.put("pipelineAmount",decimal(p.get("pipelineAmount")).add(amount));
            if(days>=14)p.put("stagnantOpportunities",number(p.get("stagnantOpportunities"))+1);
        }
        List<Object> contractArgs=new ArrayList<>();contractArgs.add(tenant);contractArgs.addAll(owners);contractArgs.add(Date.valueOf(start));contractArgs.add(Date.valueOf(end.plusDays(1)));
        jdbc.queryForList("""
                SELECT m.owner_user_id,COUNT(c.id) contracts,COALESCE(SUM(c.amount),0) amount FROM oa_crm_contract c JOIN oa_merchant m ON m.id=c.customer_id
                WHERE c.tenant_id=? AND c.deleted=0 AND c.effective_version>0 AND m.owner_user_id IN (%s)
                  AND c.id IN(SELECT v.contract_id FROM oa_crm_contract_version v WHERE v.status='active' AND v.signed_date>=? AND v.signed_date<?) GROUP BY m.owner_user_id
                """.formatted(marks),contractArgs.toArray()).forEach(r->{if(people.containsKey(number(r.get("owner_user_id")))){var p=people.get(number(r.get("owner_user_id")));p.put("signedContracts",number(r.get("contracts")));p.put("signedAmount",decimal(r.get("amount")));}});
        List<Object> receiptArgs=new ArrayList<>();receiptArgs.add(tenant);receiptArgs.add(Date.valueOf(start));receiptArgs.add(Date.valueOf(end.plusDays(1)));receiptArgs.addAll(owners);
        jdbc.queryForList("""
                SELECT m.owner_user_id,COALESCE(SUM(CASE WHEN f.record_type IN ('refund','reversal') THEN -f.amount ELSE f.amount END),0) amount
                FROM oa_finance_record f JOIN oa_merchant m ON m.id=f.customer_id
                WHERE m.tenant_id=? AND f.record_type IN ('receipt','refund','reversal') AND f.status='confirmed' AND f.business_date>=? AND f.business_date<?
                  AND m.owner_user_id IN (%s) GROUP BY m.owner_user_id
                """.formatted(marks),receiptArgs.toArray()).forEach(r->{if(people.containsKey(number(r.get("owner_user_id"))))people.get(number(r.get("owner_user_id"))).put("receivedAmount",decimal(r.get("amount")));});
        List<Map<String,Object>> staff=people.values().stream().sorted(Comparator.comparing((Map<String,Object> p)->decimal(p.get("receivedAmount"))).reversed()
                .thenComparing(p->decimal(p.get("signedAmount")),Comparator.reverseOrder()).thenComparing(p->number(p.get("newCustomers")),Comparator.reverseOrder())).toList();
        List<Object> attentionArgs=new ArrayList<>();attentionArgs.add(tenant);attentionArgs.addAll(owners);
        List<Map<String,Object>> attention=new ArrayList<>(jdbc.queryForList("""
                SELECT CASE WHEN f.id IS NULL THEN 'No follow-up' ELSE 'Follow-up overdue' END type,m.id customer_id,m.name customer_name,m.owner_user_id owner_id,u.nick_name owner_name,
                       COALESCE(f.next_followup_time,m.assigned_at,m.create_time) due_time
                FROM oa_merchant m JOIN sys_user u ON u.user_id=m.owner_user_id
                LEFT JOIN oa_customer_followup f ON f.id=(SELECT f2.id FROM oa_customer_followup f2 WHERE f2.merchant_id=m.id ORDER BY f2.followup_time DESC,f2.id DESC LIMIT 1)
                WHERE m.tenant_id=? AND m.status='0' AND m.public_pool='0' AND m.owner_user_id IN (%s)
                  AND (f.id IS NULL OR f.next_followup_time<CURRENT_TIMESTAMP) ORDER BY due_time LIMIT 20
                """.formatted(marks),attentionArgs.toArray()));
        opportunities.stream().filter(r->number(r.get("updatedDays"))>=14).sorted(Comparator.comparingLong(r->-number(r.get("updatedDays")))).limit(20).forEach(r->{
            Map<String,Object> item=new LinkedHashMap<>();item.put("type","Stagnant opportunity");item.put("opportunityId",r.get("opportunityId"));item.put("customerId",r.get("customerId"));item.put("customerName",r.get("customerName"));
            item.put("name",r.get("name"));item.put("stage",r.get("stage"));item.put("ownerName",r.get("ownerName"));item.put("updatedDays",r.get("updatedDays"));item.put("amount",r.get("amount"));attention.add(item);
        });
        Map<String,Object> summary=new LinkedHashMap<>();summary.put("salesCount",staff.size());summary.put("customers",sum(staff,"customerCount"));summary.put("newCustomers",sum(staff,"newCustomers"));summary.put("followups",sum(staff,"followups"));
        summary.put("overdueCustomers",sum(staff,"overdueCustomers"));summary.put("activeOpportunities",sum(staff,"activeOpportunities"));summary.put("stagnantOpportunities",sum(staff,"stagnantOpportunities"));
        summary.put("signedAmount",staff.stream().map(p->decimal(p.get("signedAmount"))).reduce(BigDecimal.ZERO,BigDecimal::add));summary.put("receivedAmount",staff.stream().map(p->decimal(p.get("receivedAmount"))).reduce(BigDecimal.ZERO,BigDecimal::add));
        return ApiResult.ok(Map.of("period",Map.of("from",start,"to",end),"department",members.get(0).get("dept_name"),"manager",manager,"summary",summary,"staff",staff,"attention",attention,
                "scope",system.isUnrestricted(actor)?"all":scopes.resolve(actor).selfOnly()?"self":"department"));
    }
    private long sum(List<Map<String,Object>> rows,String key){return rows.stream().mapToLong(r->number(r.get(key))).sum();}
    private Map<String,Object> parse(Object value){try{return value==null?Map.of():json.readValue(value.toString(),JSON_MAP);}catch(Exception ignored){return Map.of();}}
    private LocalDate parse(String value,LocalDate fallback){try{return value==null||value.isBlank()?fallback:LocalDate.parse(value);}catch(Exception e){throw new ApiException(400,"Dates must use YYYY-MM-DD");}}
    private LocalDate date(Object value,LocalDate fallback){if(value==null)return fallback;if(value instanceof java.sql.Timestamp t)return t.toLocalDateTime().toLocalDate();if(value instanceof java.sql.Date d)return d.toLocalDate();try{return LocalDate.parse(value.toString().substring(0,10));}catch(Exception e){return fallback;}}
    private long number(Object value){try{return value==null?0:Long.parseLong(value.toString());}catch(Exception e){return 0;}}
    private BigDecimal decimal(Object value){try{return value==null?BigDecimal.ZERO:new BigDecimal(value.toString());}catch(Exception e){return BigDecimal.ZERO;}}
}
