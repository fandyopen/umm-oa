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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:sales_performance;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@Transactional
class SalesPerformanceIntegrationTest {
    @Autowired SalesPerformanceController controller;
    @Autowired JdbcTemplate jdbc;

    @AfterEach void clear(){AuthContext.clear();}

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
