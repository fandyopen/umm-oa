package com.seal.oa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.auth.AuthContext;
import com.seal.oa.hr.HrEmployeeService;
import com.seal.oa.oa.BusinessQueryController;
import com.seal.oa.oa.CustomerFinanceService;
import com.seal.oa.system.DataTableService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:list_ordering_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
@Transactional
class ListOrderingIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired HrEmployeeService employees;
    @Autowired CustomerFinanceService finance;
    @Autowired BusinessQueryController business;
    @Autowired DataTableService tables;
    @Autowired ObjectMapper json;

    @BeforeEach void login() { AuthContext.set(new AuthContext.CurrentUser(1L, "13610316814")); }
    @AfterEach void clear() { AuthContext.clear(); }

    @Test void employeesUseEntryDateBeforeIdAndKeepMissingDatesLast() {
        employee(991101, "2026-11-01");
        employee(991102, "2023-07-30");
        employee(991103, "2026-11-01");
        employee(991104, null);
        assertEquals(List.of(991103L, 991101L, 991102L, 991104L),
                employees.list(Map.of("keyword", "排序回归员工")).stream().map(r -> r.get("userId")).toList());
    }

    @Test void financeBusinessDatesAreOrderedAcrossPagesAndDataManagementHonorsExplicitSort() throws Exception {
        jdbc.update("INSERT INTO oa_merchant(name,owner_user_id,status,public_pool) VALUES('排序回归客户',4,'0','0')");
        long customer = jdbc.queryForObject("SELECT MAX(id) FROM oa_merchant", Long.class);
        long latest = receivable(customer, "2026-11-16");
        long old = receivable(customer, "2023-07-30");
        long sameDay = receivable(customer, "2026-11-16");
        assertEquals(sameDay, first(finance.records("receivable", Map.of("customerId", "" + customer, "pageSize", "1"))).get("id"));
        assertEquals(latest, first(finance.records("receivable", Map.of("customerId", "" + customer, "pageNum", "2", "pageSize", "1"))).get("id"));
        assertEquals(old, first(finance.records("receivable", Map.of("customerId", "" + customer, "pageNum", "3", "pageSize", "1"))).get("id"));
        String filter = json.writeValueAsString(List.of(Map.of("field", "customer_id", "op", "eq", "value", customer)));
        assertEquals("" + sameDay, values(first(tables.rows("oa_finance_receivable", Map.of("filters", filter, "size", "1")))).get("id").toString());
        assertEquals("" + old, values(first(tables.rows("oa_finance_receivable", Map.of("filters", filter, "sort", "due_date", "direction", "asc", "size", "1")))).get("id").toString());
    }

    @Test void opportunityDatesAreSortedBeforeServerPagination() throws Exception {
        long latest = opportunity("2026-11-16");
        long old = opportunity("2023-07-30");
        assertEquals(latest, first(business.list(Map.of("queryAll", "排序回归商机", "pageSize", "1"))).get("id"));
        assertEquals(old, first(business.list(Map.of("queryAll", "排序回归商机", "pageNum", "2", "pageSize", "1"))).get("id"));
    }

    @Test void backdatedFinanceRecordsAndMissingBusinessDatesDoNotDisplaceNewerDates() {
        jdbc.update("INSERT INTO oa_merchant(name,owner_user_id,status,public_pool) VALUES('排序回归客户',4,'0','0')");
        long customer = jdbc.queryForObject("SELECT MAX(id) FROM oa_merchant", Long.class);
        long latest = financeRecord(customer, "2026-11-16");
        long old = financeRecord(customer, "2023-07-30");
        long missing = financeRecord(customer, null);
        assertEquals(List.of(latest, old, missing), rows(finance.records("cost", Map.of("customerId", "" + customer)))
                .stream().map(row -> row.get("id")).toList());
    }

    void employee(long user, String date) {
        jdbc.update("INSERT INTO sys_user(user_id,user_name,nick_name,password,status,del_flag) SELECT ?,?,'排序回归员工',password,'0','0' FROM sys_user WHERE user_id=4", user, "ORDER-" + user);
        employees.ensure(user);
        jdbc.update("UPDATE oa_hr_employee SET entry_date=? WHERE user_id=?", date, user);
    }
    long receivable(long customer, String date) {
        jdbc.update("INSERT INTO oa_finance_receivable(customer_id,title,amount,due_date) VALUES(?,'排序回归应收',100,?)", customer, date);
        return jdbc.queryForObject("SELECT MAX(id) FROM oa_finance_receivable", Long.class);
    }
    long opportunity(String date) throws Exception {
        jdbc.update("INSERT INTO biz_record(module,business_key,payload_json) VALUES('/project/business/list',?,?)", UUID.randomUUID().toString(),
                json.writeValueAsString(Map.of("name", "排序回归商机", "ownerUserId", 4, "expectedCloseDate", date)));
        return jdbc.queryForObject("SELECT MAX(id) FROM biz_record", Long.class);
    }
    long financeRecord(long customer, String date) {
        jdbc.update("INSERT INTO oa_finance_record(customer_id,record_type,amount,business_date,create_token) VALUES(?,'cost',100,?,?)", customer, date, UUID.randomUUID().toString());
        return jdbc.queryForObject("SELECT MAX(id) FROM oa_finance_record", Long.class);
    }
    @SuppressWarnings("unchecked") List<Map<String, Object>> rows(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("rows");
    }
    @SuppressWarnings("unchecked") Map<String, Object> first(Map<String, Object> page) {
        return ((List<Map<String, Object>>) page.get("rows")).get(0);
    }
    @SuppressWarnings("unchecked") Map<String, Object> values(Map<String, Object> packed) {
        return (Map<String, Object>) packed.get("values");
    }
}
