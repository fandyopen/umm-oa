package com.seal.oa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.common.ApiException;
import com.seal.oa.system.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:customer_cleanup_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "app.customer-cleanup-backup-dir=./target/customer-cleanup-test-backups"
})
@AutoConfigureMockMvc
class CustomerCleanupIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired CustomerCleanupService cleanup;
    @Autowired CustomerCleanupGate gate;
    @Autowired SystemService system;
    @Autowired ModuleService modules;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @Autowired com.seal.oa.auth.JwtService jwt;
    @Autowired com.seal.oa.oa.CrmController crm;
    long customer;
    @BeforeEach void setup() {
        root(); customer=customer("清理测试-"+UUID.randomUUID());
    }
    @AfterEach void teardown() { AuthContext.clear(); }
    void root() { AuthContext.set(new AuthContext.CurrentUser(1L,"13610316814")); }
    long customer(String name) { return insert("oa_merchant",Map.of("name",name,"owner_user_id",1,"cooperation_status","合作中","contract_amount",100,"received_amount",50)); }
    long insert(String table,Map<String,Object> data) {
        var keys=new ArrayList<>(data.keySet());
        var key=new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement=connection.prepareStatement("INSERT INTO "+table+"("+String.join(",",keys)+") VALUES("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",new String[]{"id"});
            for(int i=0;i<keys.size();i++)statement.setObject(i+1,data.get(keys.get(i)));
            return statement;
        },key);
        return key.getKey().longValue();
    }
    long contract(long customer) {
        return insert("oa_crm_contract",Map.of("customer_id",customer,"code",UUID.randomUUID().toString(),"name","有效合同","service_type","招聘","start_date","2026-01-01","end_date","2027-01-01","terms_json","[]","status","active","effective_version",1));
    }
    long job(long customer,long contract) {
        return insert("oa_recruit_job",Map.of("customer_id",customer,"contract_id",contract,"owner_user_id",1,"title","关闭职位","code",UUID.randomUUID().toString(),"status","deleted","create_by",1));
    }
    long talent() { return insert("oa_recruit_talent",Map.of("owner_user_id",1,"name","共享候选人","create_by",1)); }
    long application(long job,long talent) { return insert("oa_recruit_application",Map.of("job_id",job,"talent_id",talent,"consultant_id",1,"stage","completed")); }
    Map<String,Object> body(long id,String operation) {
        var preview=cleanup.preview(id);
        return new LinkedHashMap<>(Map.of("operation",operation,"reason","清理测试数据","confirmId",String.valueOf(id),"confirmName",((Map<?,?>)preview.get("customer")).get("name"),"signature",preview.get("signature")));
    }
    long count(String table,long id) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE id=?",Long.class,id); }

    @Test void wholeGraphIsDeletedRegardlessOfStatusWhileOtherCustomerAndSharedTalentSurvive() throws Exception {
        long other=customer("其他客户"),contract=contract(customer),otherContract=contract(other),job=job(customer,contract),otherJob=job(other,otherContract),talent=talent();
        long app=application(job,talent),otherApp=application(otherJob,talent);
        long contractHistory=insert("oa_crm_contract_history",Map.of("customer_id",customer,"contract_id",contract,"action","archive","reason","删除保留历史"));
        insert("oa_crm_contract_version",Map.of("contract_id",contract,"version_no",1,"snapshot_json","{}","status","signed","submit_user_id",1,"approval_user_id",1));
        long receipt=insert("oa_crm_contract_receipt",Map.of("contract_id",contract,"term_key","main","amount",50,"receipt_date","2026-11-01"));
        long receivable=insert("oa_finance_receivable",Map.of("customer_id",customer,"title","人工应收","amount",50,"due_date","2026-11-01","status","confirmed"));
        long finance=insert("oa_finance_record",Map.of("customer_id",customer,"contract_id",contract,"receipt_id",receipt,"record_type","receipt","amount",50,"status","confirmed","create_token",UUID.randomUUID().toString()));
        insert("oa_finance_record",Map.of("customer_id",customer,"receivable_id",receivable,"record_type","invoice","amount",50,"status","approving","create_token",UUID.randomUUID().toString()));
        long collection=insert("oa_finance_collection",Map.of("customer_id",customer,"receivable_id",receivable,"owner_user_id",1,"content","催收"));
        for(var entry:Map.of("record",finance,"receivable",receivable,"collection",collection).entrySet())insert("oa_finance_history",Map.of("entity_type",entry.getKey(),"entity_id",entry.getValue(),"action","测试","content","{}"));
        insert("oa_recruit_interview",Map.of("application_id",app,"round_no",1,"interview_time","2026-11-01 10:00:00"));
        insert("oa_recruit_exception",Map.of("application_id",app,"exception_type","test","content","测试","owner_user_id",1));
        insert("oa_recruit_guarantee_handling",Map.of("application_id",app,"operation","complete","event_date","2026-11-01","reason","测试","operator_id",1));
        insert("oa_recruit_attachment",Map.of("application_id",app,"file_key",UUID.randomUUID().toString(),"original_name","入职.pdf","content_type","application/pdf","file_size",2,"create_by",1));
        insert("oa_recruit_replacement",Map.of("original_application_id",app,"replacement_application_id",otherApp,"create_by",1));
        insert("oa_recruit_event",Map.of("entity_type","application","entity_id",app,"event_type","entered","operator_id",1));
        insert("oa_recruit_version",Map.of("job_id",job,"version_no",1,"snapshot_json","{}","create_by",1));
        long request=insert("oa_workflow_request",Map.of("module_id",341301,"action_code","demand","title","职位审批","payload_json","{\"entityId\":"+job+"}"));
        insert("oa_recruit_approval",Map.of("workflow_request_id",request,"entity_type","job","entity_id",job,"action_code","demand","revision",0,"payload_json","{}","create_by",1));
        String instance="cleanup-"+UUID.randomUUID();
        jdbc.update("INSERT INTO oa_process_instance(id,business_type,business_id) VALUES(?,'workflowAction',?)",instance,request);
        jdbc.update("INSERT INTO oa_process_task(id,instance_id) VALUES(?,?)",instance+"-task",instance);
        insert("oa_todo",Map.of("title","审批待办","business_type","workflowAction","business_id",request,"process_instance_id",instance));
        insert("oa_todo",Map.of("title","客户提醒","business_type","crmCustomerFollowup","business_id",customer));
        jdbc.update("INSERT INTO oa_workflow_action_submission(user_id,submission_key,fingerprint,response_json) VALUES(1,?,'test',?)",instance,"{\"requestId\":"+request+"}");
        insert("oa_merchant_contact",Map.of("merchant_id",customer,"name","联系人"));
        var preview=cleanup.preview(customer);
        assertTrue(((Number)preview.get("total")).longValue()>15);
        Map<String,Object> result=cleanup.execute(customer,body(customer,"cleanup"));
        assertEquals(0L,((Number)cleanup.preview(customer).get("total")).longValue());
        assertEquals(1,count("oa_merchant",customer));assertEquals(1,count("oa_merchant",other));
        assertEquals(1,count("oa_recruit_talent",talent));assertEquals(1,count("oa_recruit_application",otherApp));
        assertEquals(1,count("oa_recruit_job",otherJob));assertEquals(1,count("oa_crm_contract",otherContract));
        assertEquals(0,count("oa_crm_contract",contract));assertEquals(0,count("oa_finance_record",finance));
        assertEquals(0,count("oa_crm_contract_history",contractHistory));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM oa_process_instance WHERE id=?",Integer.class,instance));
        assertEquals(BigDecimal.ZERO.setScale(2),jdbc.queryForObject("SELECT received_amount FROM oa_merchant WHERE id=?",BigDecimal.class,customer));
        byte[] backup=cleanup.backup((String)result.get("id"));
        try(var zip=new ZipInputStream(new ByteArrayInputStream(backup))) {
            assertEquals("customer-data.json",zip.getNextEntry().getName());
            var snapshot=json.readTree(zip.readAllBytes());
            assertEquals(customer,snapshot.path("customer").path("id").asLong());
            assertEquals(1,snapshot.path("deleteRows").path("oa_crm_contract").size());
        }
        cleanup.execute(customer,body(customer,"delete"));
        assertEquals(0,count("oa_merchant",customer));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM sys_customer_cleanup_history WHERE customer_id=?",Integer.class,customer));
    }

    @Test void previewIsExactAndStaleOrWrongConfirmationNeverDeletesAnything() {
        long contact=insert("oa_merchant_contact",Map.of("merchant_id",customer,"name","原联系人"));
        Map<String,Object> stale=body(customer,"cleanup");
        insert("oa_merchant_contact",Map.of("merchant_id",customer,"name","新增联系人"));
        assertEquals(409,assertThrows(ApiException.class,() -> cleanup.execute(customer,stale)).code());
        assertEquals(1,count("oa_merchant_contact",contact));
        Map<String,Object> wrong=body(customer,"cleanup");wrong.put("confirmId",String.valueOf(customer)+"0");
        assertEquals(400,assertThrows(ApiException.class,() -> cleanup.execute(customer,wrong)).code());
        assertEquals(409,assertThrows(ApiException.class,() -> cleanup.execute(customer,body(customer,"delete"))).code());
    }

    @Test void failedDeletionRollsBackEveryModuleAndCreatesNoSuccessAudit() {
        long contract=contract(customer),contact=insert("oa_merchant_contact",Map.of("merchant_id",customer,"name","不能丢失"));
        jdbc.execute("CREATE TABLE cleanup_test_blocker(id BIGINT PRIMARY KEY, contract_id BIGINT REFERENCES oa_crm_contract(id))");
        try {
            jdbc.update("INSERT INTO cleanup_test_blocker VALUES(1,?)",contract);
            assertThrows(RuntimeException.class,() -> cleanup.execute(customer,body(customer,"cleanup")));
            assertEquals(1,count("oa_merchant_contact",contact));assertEquals(1,count("oa_crm_contract",contract));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_customer_cleanup_history WHERE customer_id=?",Integer.class,customer));
        } finally { jdbc.execute("DROP TABLE cleanup_test_blocker"); }
    }

    @Test void backupFailurePreventsAllDatabaseWrites() throws Exception {
        Path blocked=Path.of("target/customer-cleanup-test-backups/blocked-"+UUID.randomUUID());
        Files.createDirectories(blocked.getParent());Files.writeString(blocked,"not a directory");
        var service=new CustomerCleanupService(jdbc,json,system,gate,transactions,blocked.resolve("child").toString());
        long contact=insert("oa_merchant_contact",Map.of("merchant_id",customer,"name","保留"));
        try { assertThrows(ApiException.class,() -> service.execute(customer,body(customer,"cleanup")));assertEquals(1,count("oa_merchant_contact",contact)); }
        finally { Files.delete(blocked); }
    }

    @Test void sharedCommissionPayrollRecalculatesUnpaidButPreservesPaidFactsAndOtherCustomerSources() {
        long other=customer("共享工资其他客户"),contract=contract(customer),otherContract=contract(other),talent=talent();
        long app=application(job(customer,contract),talent),otherApp=application(job(other,otherContract),talent);
        long attribution=attribution(customer,contract,app),otherAttribution=attribution(other,otherContract,otherApp);
        long batch=insert("oa_commission_batch",Map.of("tenant_id",1,"period","2026-11","cutoff_date","2026-11-30","status","linked","signature","signature","scope_json","["+attribution+","+otherAttribution+"]","create_by",1));
        long payroll=insert("oa_hr_payroll",Map.of("period","2026-11","batch_type","commission","rules_json","{}","status","approved","published",1,"create_by",1));
        jdbc.update("UPDATE oa_commission_batch SET payroll_id=? WHERE id=?",payroll,batch);
        long unpaid=payrollItem(payroll,101,80),paid=payrollItem(payroll,102,40);
        line(batch,attribution,101,50,unpaid);line(batch,otherAttribution,101,30,unpaid);
        line(batch,attribution,102,20,paid);long surviving=line(batch,otherAttribution,102,20,paid);
        long payment=insert("oa_hr_payment",Map.of("item_id",paid,"request_token",UUID.randomUUID().toString(),"amount",10,"payment_date","2026-11-01","status","paid","reference_no",UUID.randomUUID().toString(),"operator_id",1));
        var paidBefore=jdbc.queryForMap("SELECT * FROM oa_hr_payroll_item WHERE id=?",paid);
        cleanup.execute(customer,body(customer,"cleanup"));
        assertEquals(new BigDecimal("30.00"),jdbc.queryForObject("SELECT commission FROM oa_hr_payroll_item WHERE id=?",BigDecimal.class,unpaid));
        assertEquals(new BigDecimal("30.00"),jdbc.queryForObject("SELECT net_amount FROM oa_hr_payroll_item WHERE id=?",BigDecimal.class,unpaid));
        assertEquals(paidBefore,jdbc.queryForMap("SELECT * FROM oa_hr_payroll_item WHERE id=?",paid));
        assertEquals(1,count("oa_hr_payment",payment));assertEquals(1,count("oa_commission_line",surviving));
        assertEquals("[\""+otherAttribution+"\"]",jdbc.queryForObject("SELECT scope_json FROM oa_commission_batch WHERE id=?",String.class,batch));
        assertEquals("draft",jdbc.queryForObject("SELECT status FROM oa_hr_payroll WHERE id=?",String.class,payroll));
        assertEquals(0,jdbc.queryForObject("SELECT published FROM oa_hr_payroll WHERE id=?",Integer.class,payroll));
        assertEquals(0L,((Number)cleanup.preview(customer).get("total")).longValue());
    }
    long attribution(long customer,long contract,long app) {
        long job=jdbc.queryForObject("SELECT job_id FROM oa_recruit_application WHERE id=?",Long.class,app);
        var data=new LinkedHashMap<String,Object>();
        data.putAll(Map.of("tenant_id",1,"application_id",app,"customer_id",customer,"job_id",job,"contract_id",contract,"rule_id",1,"rule_json","{}","participants_json","[]","business_kind","first","basis_date","2026-11-01"));
        data.putAll(Map.of("reason","测试","create_by",1,"status","confirmed"));
        return insert("oa_commission_attribution",data);
    }
    @Test void exclusiveUnpaidCommissionAndPayrollBatchesAreDeletedWithTheirHistory() {
        long contract=contract(customer),app=application(job(customer,contract),talent());
        long attribution=attribution(customer,contract,app);
        long batch=insert("oa_commission_batch",Map.of("tenant_id",1,"period","2026-11","cutoff_date","2026-11-30","status","linked","signature","signature","scope_json","["+attribution+"]","create_by",1));
        long payroll=insert("oa_hr_payroll",Map.of("period","2026-11","batch_type","commission","rules_json","{}","status","approved","published",1,"create_by",1));
        jdbc.update("UPDATE oa_commission_batch SET payroll_id=? WHERE id=?",payroll,batch);
        long item=payrollItem(payroll,103,50);
        line(batch,attribution,103,50,item);
        long commissionHistory=insert("oa_commission_history",Map.of("tenant_id",1,"entity_type","batch","entity_id",batch,"action","生成","detail_json","{}","operator_id",1));
        long hrHistory=insert("oa_hr_history",Map.of("entity_type","payroll","entity_id",payroll,"action","生成","detail_json","{}","operator_id",1));
        long payslip=insert("oa_todo",Map.of("title","工资条","business_type","hrPayslip","business_id",item));
        cleanup.execute(customer,body(customer,"cleanup"));
        for(var entry:Map.of("oa_commission_batch",batch,"oa_hr_payroll",payroll,"oa_hr_payroll_item",item,"oa_commission_history",commissionHistory,"oa_hr_history",hrHistory,"oa_todo",payslip).entrySet())
            assertEquals(0,count(entry.getKey(),entry.getValue()),entry.getKey());
        assertEquals(0L,((Number)cleanup.preview(customer).get("total")).longValue());
    }
    long payrollItem(long payroll,long user,int amount) {
        return insert("oa_hr_payroll_item",Map.of("payroll_id",payroll,"user_id",user,"snapshot_json","{}","base_amount",0,"allowance",0,"commission",amount,"gross_amount",amount,"net_amount",amount,"verified",1));
    }
    long line(long batch,long attribution,long user,int amount,long item) {
        return insert("oa_commission_line",Map.of("batch_id",batch,"attribution_id",attribution,"user_id",user,"side","delivery","amount",amount,"snapshot_json","{}","payroll_item_id",item));
    }

    @Test void jsonOwnershipDoesNotConfuseCustomerIdsOrUnrelatedFinanceAndSalaryIds() {
        long other=customer("同名客户"); jdbc.update("UPDATE oa_merchant SET name='同名客户' WHERE id=?",customer);
        long own=insert("biz_record",Map.of("module","/project/business/list","payload_json","{\"customerId\":\""+customer+"\",\"id\":900001}"));
        long unrelated=insert("biz_record",Map.of("module","/project/business/list","payload_json","{\"customerId\":"+other+"}"));
        long finance=insert("oa_finance_record",Map.of("customer_id",customer,"record_type","cost","amount",1,"create_token",UUID.randomUUID().toString()));
        long salaryRequest=insert("oa_workflow_request",Map.of("module_id",341002,"action_code","submit","title","无关薪资审批","payload_json","{\"recordId\":"+finance+"}"));
        assertEquals(2,cleanup.search("同名客户").size());
        cleanup.execute(customer,body(customer,"cleanup"));
        assertEquals(0,count("biz_record",own));assertEquals(1,count("biz_record",unrelated));assertEquals(1,count("oa_workflow_request",salaryRequest));
    }

    @Test void fixedSuperAdminRequiredEvenForRoleAdminAndCrossTenantRequestsAreDenied() {
        long member=insertUser();
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,1)",member);
        AuthContext.set(new AuthContext.CurrentUser(member,"cleanup-role-admin"));
        assertEquals(403,assertThrows(ApiException.class,() -> cleanup.preview(customer)).code());
        assertFalse(containsMenu(modules.routers(1L,true,Set.of(),Set.of()),341510));
        root();jdbc.update("UPDATE oa_merchant SET tenant_id=2 WHERE id=?",customer);
        assertEquals(404,assertThrows(ApiException.class,() -> cleanup.preview(customer)).code());
        assertTrue(containsMenu(modules.routers(1L,true,Set.of(),Set.of()),341510));
    }
    @Test void cleanedCustomerDoesNotRegenerateOldRiskTodosUntilEditedAgain() {
        jdbc.update("UPDATE oa_merchant SET create_time='2020-01-01',assigned_at='2020-01-01' WHERE id=?",customer);
        cleanup.execute(customer,body(customer,"cleanup"));
        crm.generateDailyRiskTodos();
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM oa_todo WHERE business_type='crmRisk' AND business_id=?",Integer.class,customer));
        assertEquals(0L,((Number)cleanup.preview(customer).get("total")).longValue());
        jdbc.update("UPDATE oa_merchant SET update_time=DATEADD('SECOND',2,CURRENT_TIMESTAMP) WHERE id=?",customer);
        crm.generateDailyRiskTodos();
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM oa_todo WHERE business_type='crmRisk' AND business_id=?",Integer.class,customer)>0);
    }

    @Test void customerNameAndIdCannotMatchNumericPrefixesInJsonOrOtherModules() {
        long similar=customer("与目标不同的客户");
        long unrelated=insert("biz_record",Map.of("module","/project/business/list","payload_json","{\"customerId\":"+customer+"0}"));
        long own=insert("biz_record",Map.of("module","/project/business/list","payload_json","{\"customerId\":"+customer+",\"id\":99999001}"));
        long keptTodo=insert("oa_todo",Map.of("business_type","hrPayslip","business_id",customer,"title","同ID工资待办"));
        long ownTodo=insert("oa_todo",Map.of("business_type","crmOpportunityFollowup","business_id",99999001,"title","目标商机待办"));
        cleanup.execute(customer,body(customer,"cleanup"));
        assertEquals(1,count("biz_record",unrelated));assertEquals(1,count("oa_todo",keptTodo));assertEquals(0,count("oa_todo",ownTodo));
        assertEquals(1,count("oa_merchant",similar));assertEquals(0,count("biz_record",own));
    }

    @Test void writeGateWaitsForInFlightBusinessWritesAndBlocksNewWritesDuringCleanup() throws Exception {
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        var businessStarted=new java.util.concurrent.CountDownLatch(1);
        var releaseBusiness=new java.util.concurrent.CountDownLatch(1);
        var cleanupStarted=new java.util.concurrent.CountDownLatch(1);
        try {
            var write=executor.submit(() -> gate.backgroundWrite(() -> {
                businessStarted.countDown();
                try { releaseBusiness.await(5,java.util.concurrent.TimeUnit.SECONDS); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            }));
            assertTrue(businessStarted.await(2,java.util.concurrent.TimeUnit.SECONDS));
            var clean=executor.submit(() -> gate.exclusive(() -> { cleanupStarted.countDown();return true; }));
            assertFalse(cleanupStarted.await(100,java.util.concurrent.TimeUnit.MILLISECONDS));
            releaseBusiness.countDown();write.get(2,java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(clean.get(2,java.util.concurrent.TimeUnit.SECONDS));
        } finally { releaseBusiness.countDown();executor.shutdownNow(); }
    }
    long insertUser() {
        jdbc.update("INSERT INTO sys_user(user_name,nick_name,password) VALUES('cleanup-role-admin','角色管理员','test')");
        return jdbc.queryForObject("SELECT user_id FROM sys_user WHERE user_name='cleanup-role-admin'",Long.class);
    }
    boolean containsMenu(List<Map<String,Object>> rows,long id) {
        return rows.stream().anyMatch(r -> Objects.equals(((Number)r.get("moduleId")).longValue(),id)
                || (r.get("children") instanceof List<?> children && containsMenu((List<Map<String,Object>>)(List<?>)children,id)));
    }
    @Test void endpointsRequireLoginAndPreviewAndBackupAreReadOnly() throws Exception {
        mvc.perform(get("/system/customer-cleanup/customers").param("keyword",String.valueOf(customer))).andExpect(jsonPath("$.code").value(401));
        var login=mvc.perform(post("/login").contentType("application/json").content("{\"username\":\"13610316814\",\"password\":\"admin123123\"}")).andReturn();
        String token=json.readTree(login.getResponse().getContentAsByteArray()).path("token").asText();
        mvc.perform(get("/system/customer-cleanup/customers/"+customer+"/preview").header("Authorization","Bearer "+token))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.customer.id").value(customer));
        assertEquals(1,count("oa_merchant",customer));
        mvc.perform(get("/system/customer-cleanup/history/nonexistent/backup").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.code").value(404));
    }
}
