package com.seal.oa.oa;

import com.seal.oa.auth.AuthContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.List;
import com.seal.oa.common.ApiException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:sales_performance;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@Transactional
class SalesPerformanceIntegrationTest {
    @Autowired SalesPerformanceController controller;
    @Autowired JdbcTemplate jdbc;

    @AfterEach void clear(){AuthContext.clear();}

    @Test void freshlyAssignedAndReassignedCustomersAreNotOverdueButExpiredFirstFollowupsHaveCompleteDetails(){
        long owner=salesperson();
        long fresh=customer(owner,LocalDateTime.now().minusHours(1));
        long overdue=customer(owner,LocalDateTime.now().minusDays(4));
        // A past owner's overdue plan must not follow a customer into a new assignment.
        followup(fresh,4,LocalDateTime.now().minusDays(8),LocalDateTime.now().minusDays(3));
        var data=data();
        var row=staff(data,owner);
        assertEquals(1L,row.get("overdueCustomers"));
        var attention=attention(data);
        assertFalse(attention.stream().anyMatch(r->Long.valueOf(fresh).equals(r.get("customerId"))));
        var detail=attention.stream().filter(r->Long.valueOf(overdue).equals(r.get("customerId"))).findFirst().orElseThrow();
        assertEquals("监管测试客户",detail.get("customerName"));
        assertEquals("监管测试销售",detail.get("ownerName"));
        assertEquals("潜在客户",detail.get("stage"));
        assertEquals("首次跟进已超期",detail.get("name"));
        assertNotNull(detail.get("dueTime"));
    }

    @Test void explicitPlansOverrideIntervalAndMissingPlansUseConfiguredInterval(){
        long owner=salesperson();
        long planned=customer(owner,LocalDateTime.now().minusDays(30));
        long unplanned=customer(owner,LocalDateTime.now().minusDays(30));
        followup(planned,owner,LocalDateTime.now().minusDays(10),LocalDateTime.now().plusDays(2));
        followup(unplanned,owner,LocalDateTime.now().minusDays(10),null);
        assertEquals(1L,staff(data(),owner).get("overdueCustomers"));
        controller.saveRules(Map.of("firstFollowupDays",20,"followupIntervalDays",20,"stagnantOpportunityDays",30));
        assertEquals(0L,((Number)staff(data(),owner).get("overdueCustomers")).longValue());
    }

    @Test void administratorFollowupResolvesCustomerOverdueWithoutCountingAdministratorAsSales(){
        long owner=salesperson();
        long customer=customer(owner,LocalDateTime.now().minusDays(20));
        assertEquals(1L,((Number)staff(data(),owner).get("overdueCustomers")).longValue());
        followup(customer,1,LocalDateTime.now().minusHours(1),LocalDateTime.now().plusDays(2));
        var result=data();
        assertEquals(0L,((Number)staff(result,owner).get("overdueCustomers")).longValue());
        assertEquals(0L,((Number)staff(result,owner).get("followups")).longValue());
        assertFalse(attention(result).stream().anyMatch(r->Long.valueOf(customer).equals(r.get("customerId"))));
        followup(customer,1,LocalDateTime.now(),LocalDateTime.now().minusHours(1));
        var overdue=attention(data()).stream().filter(r->Long.valueOf(customer).equals(r.get("customerId"))).findFirst().orElseThrow();
        assertEquals(owner,overdue.get("ownerUserId"));
        assertEquals("Follow-up overdue",overdue.get("type"));
        assertEquals("已超过最近一次跟进约定的下次跟进时间",overdue.get("reason"));
    }

    @Test void changingRulesRecomputesBothCountsAndDetailsAndCannotReadOtherTenantsRules(){
        long owner=salesperson();customer(owner,LocalDateTime.now().minusDays(4));
        assertEquals(1L,staff(data(),owner).get("overdueCustomers"));
        controller.saveRules(Map.of("firstFollowupDays",5,"followupIntervalDays",8,"stagnantOpportunityDays",20));
        var data=data();
        assertEquals(0L,((Number)staff(data,owner).get("overdueCustomers")).longValue());
        assertTrue(attention(data).stream().noneMatch(r->Long.valueOf(owner).equals(r.get("ownerUserId"))));
        jdbc.update("INSERT INTO sys_config(config_name,config_key,config_value) VALUES('外企业规则','crm.sales.rules.2','{\"firstFollowupDays\":1}')");
        assertEquals(5L,((Number)((Map<?,?>)data().get("rules")).get("firstFollowupDays")).longValue());
        assertThrows(ApiException.class,()->controller.saveRules(Map.of("firstFollowupDays",0,"followupIntervalDays",8,"stagnantOpportunityDays",20)));
        AuthContext.set(new AuthContext.CurrentUser(owner,"test"));
        assertThrows(ApiException.class,()->controller.saveRules(Map.of("firstFollowupDays",1,"followupIntervalDays",8,"stagnantOpportunityDays",20)));
    }

    @Test void configuredOpportunityStagnationUsesSameThresholdForRankingAndAttention(){
        long owner=salesperson();
        String payload="{\"ownerUserId\":"+owner+",\"name\":\"停滞监管商机\",\"stage\":\"需求确认\",\"expectedAmount\":1000}";
        jdbc.update("INSERT INTO biz_record(tenant_id,module,business_key,payload_json,create_time,update_time) VALUES(1,'/project/business/list',?,?,?,?)",UUID.randomUUID().toString(),payload,Timestamp.valueOf(LocalDateTime.now().minusDays(30)),Timestamp.valueOf(LocalDateTime.now().minusDays(15)));
        assertEquals(1L,((Number)staff(data(),owner).get("stagnantOpportunities")).longValue());
        assertTrue(attention(data()).stream().anyMatch(r->"停滞监管商机".equals(r.get("name"))));
        assertTrue(attention(data()).stream().filter(r->"停滞监管商机".equals(r.get("name"))).allMatch(r->Long.valueOf(owner).equals(r.get("ownerUserId"))));
        controller.saveRules(Map.of("firstFollowupDays",3,"followupIntervalDays",7,"stagnantOpportunityDays",20));
        assertEquals(0L,((Number)staff(data(),owner).get("stagnantOpportunities")).longValue());
        assertFalse(attention(data()).stream().anyMatch(r->"停滞监管商机".equals(r.get("name"))));
    }

    long salesperson(){
        AuthContext.set(new AuthContext.CurrentUser(1L,"13610316814"));
        String login="supervision_"+UUID.randomUUID();
        jdbc.update("INSERT INTO sys_user(tenant_id,dept_id,user_name,nick_name,password,status,del_flag) VALUES(1,326,?,'监管测试销售','x','0','0')",login);
        return jdbc.queryForObject("SELECT user_id FROM sys_user WHERE user_name=?",Long.class,login);
    }
    long customer(long owner,LocalDateTime assigned){
        String serial="SV-"+UUID.randomUUID();
        jdbc.update("INSERT INTO oa_merchant(tenant_id,serial,name,owner_user_id,owner_user_name,public_pool,assigned_at,cooperation_status) VALUES(1,?,'监管测试客户',?,'监管测试销售','0',?,'潜在客户')",serial,owner,Timestamp.valueOf(assigned));
        return jdbc.queryForObject("SELECT id FROM oa_merchant WHERE serial=?",Long.class,serial);
    }
    void followup(long customer,long owner,LocalDateTime time,LocalDateTime next){
        jdbc.update("INSERT INTO oa_customer_followup(tenant_id,merchant_id,followup_time,next_followup_time,content,owner_user_id) VALUES(1,?,?,?,'测试跟进',?)",customer,Timestamp.valueOf(time),next==null?null:Timestamp.valueOf(next),owner);
    }
    @SuppressWarnings("unchecked") Map<String,Object> data(){return (Map<String,Object>)controller.report(null,null).get("data");}
    @SuppressWarnings("unchecked") Map<String,Object> staff(Map<String,Object> data,long owner){return ((List<Map<String,Object>>)data.get("staff")).stream().filter(r->Long.valueOf(owner).equals(r.get("userId"))).findFirst().orElseThrow();}
    @SuppressWarnings("unchecked") List<Map<String,Object>> attention(Map<String,Object> data){return (List<Map<String,Object>>)data.get("attention");}

    @Test void reportExecutesAgainstBusinessTablesAndRespectsAdminTenantScope(){
        AuthContext.set(new AuthContext.CurrentUser(1L,"13610316814"));
        var response=controller.report(null,null);
        assertEquals(200,response.get("code"));
        @SuppressWarnings("unchecked") Map<String,Object> data=(Map<String,Object>)response.get("data");
        assertEquals("all",data.get("scope"));
        assertNotNull(data.get("summary"));
        assertNotNull(data.get("staff"));
        assertNotNull(data.get("attention"));
    }

    @Test void salesScopeDoesNotIncludeSuperAdminOrOtherTenantAndLimitsPeriod(){
        AuthContext.set(new AuthContext.CurrentUser(1L,"13610316814"));
        jdbc.update("INSERT INTO sys_user(tenant_id,dept_id,user_name,nick_name,password,status,del_flag) VALUES(1,326,'sales_perf_test','测试销售','x','0','0')");
        jdbc.update("INSERT INTO sys_user(tenant_id,dept_id,user_name,nick_name,password,status,del_flag) VALUES(2,326,'sales_perf_foreign','外企业销售','x','0','0')");
        var result=controller.report("2026-11-01","2026-11-15");
        @SuppressWarnings("unchecked") Map<String,Object> data=(Map<String,Object>)result.get("data");
        @SuppressWarnings("unchecked") var staff=(java.util.List<Map<String,Object>>)data.get("staff");
        assertTrue(staff.stream().anyMatch(row->row.get("name").equals("测试销售")));
        assertFalse(staff.stream().anyMatch(row->row.get("name").equals("外企业销售")));
        assertFalse(staff.stream().anyMatch(row->row.get("name").equals("欧志锋")));
        assertThrows(RuntimeException.class,()->controller.report("2024-01-01","2026-11-15"));
    }

    @Test void departmentPrincipalIsShownSeparatelyAndExcludedFromSalesRanking(){
        AuthContext.set(new AuthContext.CurrentUser(1L,"13610316814"));
        String userName="sales_perf_manager_"+UUID.randomUUID();
        jdbc.update("INSERT INTO sys_user(tenant_id,dept_id,user_name,nick_name,password,status,del_flag) VALUES(1,326,?,'拓展部经理测试','x','0','0')",userName);
        long managerId=jdbc.queryForObject("SELECT user_id FROM sys_user WHERE user_name=?",Long.class,userName);
        jdbc.update("DELETE FROM sys_dept_principal WHERE dept_id=326");
        jdbc.update("INSERT INTO sys_dept_principal(dept_id,user_id) VALUES(326,?)",managerId);

        var response=controller.report(null,null);
        @SuppressWarnings("unchecked") Map<String,Object> data=(Map<String,Object>)response.get("data");
        @SuppressWarnings("unchecked") var staff=(java.util.List<Map<String,Object>>)data.get("staff");
        assertEquals("拓展部经理测试",data.get("manager"));
        assertFalse(staff.stream().anyMatch(row->Long.valueOf(managerId).equals(row.get("userId"))));
        @SuppressWarnings("unchecked") Map<String,Object> summary=(Map<String,Object>)data.get("summary");
        assertEquals(staff.size(),summary.get("salesCount"));
    }

    @Test void reportAggregatesCustomerFollowupOpportunitySignedContractAndNetReceipts(){
        AuthContext.set(new AuthContext.CurrentUser(1L,"13610316814"));
        String userName="sales_perf_"+UUID.randomUUID();
        jdbc.update("INSERT INTO sys_user(tenant_id,dept_id,user_name,nick_name,password,status,del_flag) VALUES(1,326,?,'测试拓展销售','x','0','0')",userName);
        long owner=jdbc.queryForObject("SELECT user_id FROM sys_user WHERE user_name=?",Long.class,userName);
        String serial="SP-"+UUID.randomUUID();
        jdbc.update("INSERT INTO oa_merchant(tenant_id,serial,name,owner_user_id,owner_user_name,public_pool) VALUES(1,?,'拓展绩效客户',?,'测试拓展销售','0')",serial,owner);
        long customer=jdbc.queryForObject("SELECT id FROM oa_merchant WHERE serial=?",Long.class,serial);
        jdbc.update("INSERT INTO oa_customer_followup(tenant_id,merchant_id,followup_time,content,owner_user_id,owner_user_name) VALUES(1,?,CURRENT_TIMESTAMP,'有效跟进',?,'测试拓展销售')",customer,owner);
        String payload="{\"ownerUserId\":"+owner+",\"name\":\"测试商机\",\"customerName\":\"拓展绩效客户\",\"customerId\":"+customer+",\"stage\":\"\\u9700\\u6c42\\u786e\\u8ba4\",\"expectedAmount\":10000}";
        jdbc.update("INSERT INTO biz_record(tenant_id,module,business_key,payload_json,status) VALUES(1,'/project/business/list',?,?, 'approved')",serial,payload);
        LocalDate today=LocalDate.now();String code="SP-CONTRACT-"+UUID.randomUUID();
        jdbc.update("INSERT INTO oa_crm_contract(tenant_id,customer_id,code,name,service_type,amount,start_date,end_date,status,version_no,effective_version,terms_json) VALUES(1,?,?,?,'recruit',5000,?,?,'active',1,1,'[]')",customer,code,"测试合同",today,today.plusMonths(1));
        long contract=jdbc.queryForObject("SELECT id FROM oa_crm_contract WHERE code=?",Long.class,code);
        jdbc.update("INSERT INTO oa_crm_contract_version(contract_id,version_no,snapshot_json,status,submit_user_id,approval_user_id,signed_date) VALUES(?,1,'{}','active',1,1,?)",contract,today);
        jdbc.update("INSERT INTO oa_finance_record(record_type,customer_id,contract_id,amount,business_date,status,create_token) VALUES('receipt',?,?,250,?,'confirmed',?)",customer,contract,today,UUID.randomUUID().toString());
        jdbc.update("INSERT INTO oa_finance_record(record_type,customer_id,contract_id,amount,business_date,status,create_token) VALUES('refund',?,?,50,?,'confirmed',?)",customer,contract,today,UUID.randomUUID().toString());
        var response=controller.report(today.withDayOfMonth(1).toString(),today.toString());
        @SuppressWarnings("unchecked") Map<String,Object> data=(Map<String,Object>)response.get("data");
        @SuppressWarnings("unchecked") var staff=(java.util.List<Map<String,Object>>)data.get("staff");
        var row=staff.stream().filter(item->Long.valueOf(owner).equals(item.get("userId"))).findFirst().orElseThrow();
        assertEquals(1L,row.get("customerCount"));
        assertEquals(1L,row.get("newCustomers"));
        assertEquals(1L,row.get("followups"));
        assertEquals(1L,row.get("activeOpportunities"));
        assertEquals(1L,row.get("signedContracts"));
        assertEquals("5000.00",row.get("signedAmount").toString());
        assertEquals("200.00",row.get("receivedAmount").toString());
    }
}
