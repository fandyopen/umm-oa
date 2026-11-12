package com.seal.oa.commission;

import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.hr.HrEmployeeService;
import com.seal.oa.hr.HrPayrollService;
import com.seal.oa.workflow.WorkflowActionRequestService;
import com.seal.oa.workflow.WorkflowActionService;
import com.seal.oa.workflow.WorkflowActionContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:commission_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Transactional
class CommissionIntegrationTest {
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired com.seal.oa.system.ModuleActionService actions;
    @Autowired CommissionService service;
    @Autowired CommissionWorkflowHandler handler;
    @Autowired WorkflowActionService workflows;
    @Autowired WorkflowActionRequestService requests;
    @Autowired HrPayrollService payroll;
    @Autowired HrEmployeeService employees;
    @Autowired JdbcTemplate jdbc;
    long customer,contract,job,talent,application,receipt,user,rule;
    LocalDate date=CommissionService.today().minusDays(2);
    @BeforeEach void setup() {
        AuthContext.set(new AuthContext.CurrentUser(1L,"13610316814"));
        jdbc.update("INSERT INTO sys_user(tenant_id,dept_id,user_name,nick_name,password,status,del_flag) SELECT 1,dept_id,?,'提成测试员工',password,'0','0' FROM sys_user WHERE user_id=1", "CM"+System.nanoTime());
        user=jdbc.queryForObject("SELECT MAX(user_id) FROM sys_user",Long.class);
        employees.ensure(user);
        jdbc.update("UPDATE oa_hr_employee SET account_name='提成测试员工',bank_name='测试银行',bank_account='TEST-COMMISSION' WHERE user_id=?",user);
        customer=service.insert("INSERT INTO oa_merchant(tenant_id,name,owner_user_id,status,public_pool) VALUES(1,'提成测试客户',1,'0','0')");
        contract=service.insert("INSERT INTO oa_crm_contract(tenant_id,customer_id,code,name,service_type,amount,start_date,end_date,status,terms_json,effective_version) VALUES(1,?,?,'提成测试合同','猎头',10000,?,?,'active','[]',1)",customer,"CM"+System.nanoTime(),date.minusMonths(1),date.plusYears(1));
        job=service.insert("INSERT INTO oa_recruit_job(customer_id,contract_id,owner_user_id,title,code,create_by) VALUES(?,?,1,'测试职位',?,1)",customer,contract,"CM"+System.nanoTime());
        talent=service.insert("INSERT INTO oa_recruit_talent(owner_user_id,name,create_by) VALUES(1,'测试候选人',1)");
        application=service.insert("INSERT INTO oa_recruit_application(job_id,talent_id,consultant_id,stage,actual_entry_date,guarantee_term_key,guarantee_end,guarantee_status) VALUES(?,?,?,'guarantee',?,'fee-main',?,'active')",job,talent,user,date,date.plusDays(30));
        receipt=service.insert("INSERT INTO oa_crm_contract_receipt(contract_id,term_key,amount,receipt_date) VALUES(?,'fee-main',10000,?)",contract,date);
        rule=CommissionService.number(service.saveRule(ruleBody()).get("id"));
    }
    @AfterEach void clear(){AuthContext.clear();}
    Map<String,Object> ruleBody(){return Map.of("name","测试比例，仅供测试","businessKind","first","effectiveDate",date.toString(),"poolRate",10,"developmentShare",30,"initialRelease",50);}
    Map<String,Object> saveAttribution(){return service.saveAttribution(application,Map.of("businessKind","first","ruleId",rule,"reason","核对开发维护及交付贡献","participants",List.of(Map.of("userId",user,"side","development","share",100),Map.of("userId",user,"side","delivery","share",100))));}
    long ready(){var d=saveAttribution();var a=(Map<String,Object>)d.get("attribution");long id=CommissionService.number(a.get("id"));service.confirmAttribution(id,Map.of("revision",a.get("revision"),"reason","双方确认贡献"),true);allocate(id,"10000");return id;}
    void allocate(long id,String amount){service.allocate(receipt,Map.of("rows",List.of(Map.of("attributionId",id,"amount",amount)),"signature",service.allocationSignature(receipt),"allocationDate",date.toString(),"reason","按实际到账分配"));}
    Map<String,Object> generate(){return service.generate(0,Map.of("period",date.toString().substring(0,7),"cutoffDate",date.toString(),"reason","月度核对"));}
    Map<String,Object> payload(Map<String,Object>b){return Map.of("batchId",b.get("id"),"revision",b.get("revision"));}
    void direct(Map<String,Object>b){var r=workflows.execute(Map.of("moduleId",341410,"actionCode","submit","payload",payload(b),"submissionKey","commission_"+b.get("id")));assertEquals("completed",r.get("status"));}
    BigDecimal total(Map<String,Object>b){return ((List<Map<String,Object>>)b.get("lines")).stream().map(r->CommissionService.money(r.get("amount"))).reduce(BigDecimal.ZERO,BigDecimal::add);}
    void refund(String amount){long negative=service.insert("INSERT INTO oa_crm_contract_receipt(contract_id,term_key,amount,receipt_date) VALUES(?,'fee-main',?,?)",contract,new BigDecimal(amount).negate(),date);jdbc.update("INSERT INTO oa_finance_record(record_type,customer_id,contract_id,term_key,amount,business_date,status,original_receipt_id,receipt_id,create_token) VALUES('refund',?,?,'fee-main',?,?,'confirmed',?,?,?)",customer,contract,new BigDecimal(amount),date,receipt,negative,UUID.randomUUID().toString());}

    @Test void stagingApprovalPayrollAndGuaranteeReleaseCloseTheLoop(){
        ready();var b=generate();assertEquals(new BigDecimal("500.00"),total(b));
        assertThrows(ApiException.class,this::generate);
        direct(b);var approved=service.detail(CommissionService.number(b.get("id")));assertEquals("approved",approved.get("status"));
        long payrollId=CommissionService.number(service.toPayroll(CommissionService.number(b.get("id")),Map.of("revision",approved.get("revision"))).get("payrollId"));
        assertEquals(payrollId,CommissionService.number(service.toPayroll(CommissionService.number(b.get("id")),Map.of()).get("payrollId")));
        assertEquals("commission",jdbc.queryForObject("SELECT batch_type FROM oa_hr_payroll WHERE id=?",String.class,payrollId));
        assertEquals(new BigDecimal("500.00"),jdbc.queryForObject("SELECT commission FROM oa_hr_payroll_item WHERE payroll_id=?",BigDecimal.class,payrollId));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM oa_commission_line WHERE payroll_item_id IS NOT NULL",Integer.class));
        assertThrows(ApiException.class,()->payroll.recalculate(payrollId,Map.of("revision",0)));
        assertThrows(ApiException.class,this::generate);
        jdbc.update("UPDATE oa_recruit_application SET guarantee_status='completed',guarantee_end=? WHERE id=?",date,application);
        var rest=generate();assertEquals(new BigDecimal("500.00"),total(rest));
        direct(rest);assertThrows(ApiException.class,this::generate);
    }
    @Test void refundsCreateNegativeDeltasAndDoNotRewriteApprovedHistory(){
        ready();var initial=generate();direct(initial);refund("2000");
        var delta=generate();assertEquals(new BigDecimal("-100.00"),total(delta));direct(delta);
        assertEquals(new BigDecimal("500.00"),total(service.detail(CommissionService.number(initial.get("id")))));
        var d=service.detail(CommissionService.number(delta.get("id")));long p=CommissionService.number(service.toPayroll(CommissionService.number(d.get("id")),Map.of("revision",d.get("revision"))).get("payrollId"));
        assertEquals(new BigDecimal("-100.00"),jdbc.queryForObject("SELECT adjustment FROM oa_hr_payroll_item WHERE payroll_id=?",BigDecimal.class,p));
        assertThrows(ApiException.class,this::generate);
    }
    @Test void abnormalRecruitmentHoldsNewReleaseButStillRecoversActualRefunds(){
        ready();var first=generate();direct(first);jdbc.update("UPDATE oa_recruit_application SET guarantee_status='refund' WHERE id=?",application);
        assertThrows(ApiException.class,this::generate);refund("2000");assertEquals(new BigDecimal("-100.00"),total(generate()));
    }
    @Test void allocationCannotOverdrawOrCrossContractOrUseStaleVersion(){
        long a=ready();assertThrows(ApiException.class,()->allocate(a,"10000.01"));
        assertThrows(ApiException.class,()->service.allocate(receipt,Map.of("rows",List.of(),"signature","stale","allocationDate",date.toString(),"reason","test")));
        jdbc.update("UPDATE oa_crm_contract_receipt SET term_key='other' WHERE id=?",receipt);
        assertThrows(ApiException.class,()->allocate(a,"100"));
    }
    @Test void attributionAndRulesAreFrozenAndInvalidSharesRejected(){
        var bad=new HashMap<>(ruleBody());bad.put("poolRate",101);assertThrows(ApiException.class,()->service.saveRule(bad));
        var b=saveAttribution();var a=(Map<String,Object>)b.get("attribution");
        var invalid=new HashMap<String,Object>();invalid.putAll(Map.of("revision",a.get("revision"),"ruleId",rule,"businessKind","first","reason","bad","participants",List.of(Map.of("userId",user,"side","development","share",99),Map.of("userId",user,"side","delivery","share",100))));
        assertThrows(ApiException.class,()->service.saveAttribution(application,invalid));
        service.confirmAttribution(CommissionService.number(a.get("id")),Map.of("revision",a.get("revision"),"reason","confirm"),true);
        allocate(CommissionService.number(a.get("id")),"10000");
        service.toggleRule(rule,Map.of("enabled",false));jdbc.update("UPDATE oa_merchant SET owner_user_id=? WHERE id=?",user,customer);
        assertEquals(new BigDecimal("500.00"),total(generate()));
        assertThrows(ApiException.class,()->service.confirmAttribution(CommissionService.number(a.get("id")),Map.of("revision",1,"reason","change"),false));
    }
    @Test void pendingWorkflowFreezesDetailsAndStaleApprovalCanBeRejected(){
        ready();var b=generate();long request=requests.create(341410,"submit","commission.batch.submit",Map.of(),"测试审批",payload(b),1L,"13610316814");
        assertEquals("approving",service.detail(CommissionService.number(b.get("id"))).get("status"));
        assertEquals(true,requests.payload(request).get("commissionBatch"));
        var c=new WorkflowActionContext(request,341410,"submit","commission.batch.submit",Map.of(),requests.payload(request),null,"13610316814");
        refund("1000");assertThrows(ApiException.class,()->handler.validate(c));
        assertThrows(ApiException.class,()->handler.validateCompletion(c,"","",true,"",1L));
        handler.workflowStateChanged(c,"","审批未通过","","");
        assertEquals("rejected",service.detail(CommissionService.number(b.get("id"))).get("status"));
        var next=service.generate(CommissionService.number(b.get("id")),Map.of("period",date.toString().substring(0,7),"cutoffDate",date.toString(),"revision",service.detail(CommissionService.number(b.get("id"))).get("revision"),"reason","退款后重算"));
        assertEquals(new BigDecimal("450.00"),total(next));direct(next);
    }
    @Test void voidReleasesReservationsAndCrossTenantReadsAreRejected(){
        ready();var b=generate();service.voidBatch(CommissionService.number(b.get("id")),Map.of("revision",b.get("revision"),"reason","重算"));assertEquals(new BigDecimal("500.00"),total(generate()));
        jdbc.update("UPDATE oa_commission_rule SET tenant_id=2 WHERE id=?",rule);assertTrue(service.rules().isEmpty());
        assertThrows(ApiException.class,()->service.toggleRule(rule,Map.of("enabled",true)));
        AuthContext.set(new AuthContext.CurrentUser(user,"CM"));assertThrows(ApiException.class,service::options);
    }
    @Test void pennyRoundingNeverCreatesNegativeCommission(){
        var team=List.<Map<String,Object>>of(Map.of("userId",1,"share",25),Map.of("userId",2,"share",25),Map.of("userId",3,"share",25),Map.of("userId",4,"share",25));
        var parts=service.split(new BigDecimal("0.02"),team);
        assertEquals(new BigDecimal("0.02"),parts.stream().reduce(BigDecimal.ZERO,BigDecimal::add));
        assertTrue(parts.stream().allMatch(p->p.signum()>=0));
    }
    @Test void payrollRequiresTaxVerificationLocksCommissionAndTracksPartialPaymentAndSelf(){
        ready();var b=generate();direct(b);var approved=service.detail(CommissionService.number(b.get("id")));
        long p=CommissionService.number(service.toPayroll(CommissionService.number(b.get("id")),Map.of("revision",approved.get("revision"))).get("payrollId"));
        var pay=payroll.detail(p,false);var row=((List<Map<String,Object>>)pay.get("rows")).get(0);
        assertEquals(2,((List<?>)row.get("commissionSources")).size());
        assertThrows(ApiException.class,()->workflows.execute(Map.of("moduleId",341005,"actionCode","submit","payload",Map.of("payrollId",p,"revision",pay.get("revision")))));
        var modified=new LinkedHashMap<>(row);modified.put("commission",9999);modified.put("reason","试图覆盖提成");modified.put("employeeNo",((Map<?,?>)row.get("snapshot")).get("employeeNo"));
        assertThrows(ApiException.class,()->payroll.previewImport(p,Map.of("revision",pay.get("revision"),"rows",List.of(modified))));
        payroll.verify(p,Map.of("revision",pay.get("revision"),"reason","个税及扣款测试为零，逐人核对"));
        workflows.execute(Map.of("moduleId",341005,"actionCode","submit","payload",Map.of("payrollId",p,"revision",payroll.detail(p,false).get("revision"))));
        var payment=new HashMap<String,Object>(Map.of("requestToken",UUID.randomUUID().toString(),"amount",100,"paymentDate",date.toString(),"status","paid","referenceNo",UUID.randomUUID().toString(),"fileName","bank.pdf","fileContent",Base64.getEncoder().encodeToString("test proof".getBytes())));
        payroll.pay(CommissionService.number(row.get("id")),payment);
        assertEquals("unpaid",((List<Map<String,Object>>)service.detail(CommissionService.number(b.get("id"))).get("lines")).get(0).get("paymentStatus"));
        payment.put("amount",400);payment.put("requestToken",UUID.randomUUID().toString());payment.put("referenceNo",UUID.randomUUID().toString());payroll.pay(CommissionService.number(row.get("id")),payment);
        assertEquals("paid",((List<Map<String,Object>>)service.detail(CommissionService.number(b.get("id"))).get("lines")).get(0).get("paymentStatus"));
        jdbc.update("INSERT INTO sys_role(role_id,role_name,role_key,data_scope) VALUES(99601,'提成自助测试','commission_self_test','5')");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,99601)",user);jdbc.update("INSERT INTO sys_role_module(role_id,module_id) VALUES(99601,341006)");
        AuthContext.set(new AuthContext.CurrentUser(user,jdbc.queryForObject("SELECT user_name FROM sys_user WHERE user_id=?",String.class,user)));
        assertEquals(2,service.self().size());assertTrue(service.self().stream().allMatch(r->"已结清".equals(r.get("paymentStatus"))));
        assertThrows(ApiException.class,service::batches);
    }
    String login(String name) throws Exception {
        var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/login").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username",name,"password","admin123123")))).andReturn();
        var result=json.readTree(response.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));assertEquals(200,result.path("code").asInt(),result.toString());return result.path("token").asText();
    }
    @Test void roleAssignedApproverReceivesTodoAndCompletesFrozenCommission() throws Exception {
        ready();var b=generate();String name=jdbc.queryForObject("SELECT user_name FROM sys_user WHERE user_id=?",String.class,user);
        jdbc.update("INSERT INTO sys_role(role_id,role_name,role_key,data_scope) VALUES(99602,'提成审批测试','commission_review_test','1')");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,99602)",user);jdbc.update("INSERT INTO sys_role_module(role_id,module_id) VALUES(99602,341410)");
        jdbc.update("INSERT INTO sys_role_module_action(role_id,module_id,action_code) VALUES(99602,341410,'approve')");
        actions.updateAction(341410,"submit",Map.of("flowDesignEnabled",true));String admin=login("13610316814");String reviewer=login(name);
        var process=Map.of("nodes",List.of(Map.of("id","start","type","start","name","发起人"),Map.of("id","review","type","approval","name","核算审批","approver","commission_review_test"),Map.of("id","end","type","end","name","结束")),"edges",List.of(Map.of("source","start","target","review"),Map.of("source","review","target","end")));
        var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/activiti/mongo/setProcess").header("Authorization","Bearer "+admin).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("menuId",341410,"menuName","提成核算","actionCode","submit","approvalEnabled",true,"processData",process)))).andReturn();
        assertEquals(200,json.readTree(response.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("code").asInt());
        response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/workflow/action/execute").header("Authorization","Bearer "+admin).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("moduleId",341410,"actionCode","submit","payload",payload(b),"submissionKey","role_commission_submit")))).andReturn();
        var result=json.readTree(response.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));assertEquals(200,result.path("code").asInt(),result.toString());var submission=result.path("data");assertEquals("pendingApproval",submission.path("status").asText());
        var taskResponse=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/project/activity/taskWaitList").header("Authorization","Bearer "+reviewer)).andReturn();
        assertTrue(taskResponse.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains(submission.path("instanceId").asText()));
        response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/activiti/process/complete").header("Authorization","Bearer "+reviewer).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("taskId",submission.path("taskId").asText(),"instanceId",submission.path("instanceId").asText(),"variables",Map.of("pass",true,"comment","核对同意"))))).andReturn();
        result=json.readTree(response.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));assertEquals(200,result.path("code").asInt(),result.toString());
        assertEquals("approved",jdbc.queryForObject("SELECT status FROM oa_commission_batch WHERE id=?",String.class,b.get("id")));
        assertEquals("approved",jdbc.queryForObject("SELECT status FROM oa_workflow_request WHERE id=?",String.class,submission.path("requestId").asLong()));
    }

    @Test void freeReplacementReusesDisabledOriginalRuleAndOnlyTransfersExistingCash(){
        ready();var initial=generate();direct(initial);
        long original=application;
        long secondTalent=service.insert("INSERT INTO oa_recruit_talent(owner_user_id,name,create_by) VALUES(1,'替换候选人',1)");
        application=service.insert("INSERT INTO oa_recruit_application(job_id,talent_id,consultant_id,stage,actual_entry_date,guarantee_term_key,guarantee_end,guarantee_status) VALUES(?,?,?,'guarantee',?,'fee-main',?,'active')",job,secondTalent,user,date,date.plusDays(30));
        jdbc.update("INSERT INTO oa_recruit_replacement(original_application_id,replacement_application_id,create_by) VALUES(?,?,1)",original,application);
        long different=CommissionService.number(service.saveRule(ruleBody()).get("id"));long savedRule=rule;rule=different;assertThrows(ApiException.class,this::saveAttribution);rule=savedRule;
        service.toggleRule(rule,Map.of("enabled",false));
        assertEquals(rule,CommissionService.number(((Map<?,?>)service.attributionForApplication(application).get("application")).get("originalRuleId")));
        var data=saveAttribution();var attr=(Map<String,Object>)data.get("attribution");long id=CommissionService.number(attr.get("id"));service.confirmAttribution(id,Map.of("revision",attr.get("revision"),"reason","替换交付确认"),true);
        long oldAttr=jdbc.queryForObject("SELECT id FROM oa_commission_attribution WHERE application_id=?",Long.class,original);
        assertThrows(ApiException.class,()->service.allocate(receipt,Map.of("rows",List.of(Map.of("attributionId",oldAttr,"amount",10000),Map.of("attributionId",id,"amount",10000)),"signature",service.allocationSignature(receipt),"allocationDate",date.toString(),"reason","重复预算")));
        allocate(id,"10000");var adjusted=generate();assertEquals(new BigDecimal("0.00"),total(adjusted));
        var amounts=((List<Map<String,Object>>)adjusted.get("lines")).stream().map(l->CommissionService.money(l.get("amount"))).toList();
        assertTrue(amounts.stream().anyMatch(a->a.signum()<0));assertTrue(amounts.stream().anyMatch(a->a.signum()>0));direct(adjusted);assertThrows(ApiException.class,this::generate);
    }
    @Test void partialAllocationSharesRefundProportionAndNewAttributionDoesNotWidenOldBatch(){
        long attr=ready();allocate(attr,"5000");refund("2000");var b=generate();assertEquals(new BigDecimal("200.00"),total(b));
        long second=service.insert("INSERT INTO oa_recruit_talent(owner_user_id,name,create_by) VALUES(1,'新业务',1)");
        application=service.insert("INSERT INTO oa_recruit_application(job_id,talent_id,consultant_id,actual_entry_date,guarantee_term_key,guarantee_status) VALUES(?,?,?,?,'fee-main','active')",job,second,user,date);
        var draft=saveAttribution();var a=(Map<String,Object>)draft.get("attribution");service.confirmAttribution(CommissionService.number(a.get("id")),Map.of("revision",a.get("revision"),"reason","新业务确认"),true);
        direct(b);assertThrows(ApiException.class,()->service.generate(0,Map.of("period",date.toString().substring(0,7),"cutoffDate",date.minusDays(1).toString(),"reason","旧期更正")));
        service.allocate(receipt,Map.of("rows",List.of(Map.of("attributionId",attr,"amount",4000)),"signature",service.allocationSignature(receipt),"allocationDate",CommissionService.today().toString(),"reason","今日重新分摊"));
        assertThrows(ApiException.class,this::generate);
    }

}
