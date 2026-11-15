package com.seal.oa.common;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RetiredBusinessSchemaTest {
    private static final List<String> RETIRED = List.of(
            "oa_project", "oa_project_member", "oa_contract", "oa_contract_item",
            "oa_purchase_apply", "oa_purchase_apply_item", "oa_purchase_payment",
            "oa_expense", "oa_expense_item", "oa_expense_invoice", "oa_loan",
            "oa_loan_detail", "oa_repayment", "oa_budget", "oa_budget_item",
            "oa_fund_plan", "oa_warehouse", "oa_warehouse_in", "oa_warehouse_out", "oa_invoice");

    @Test
    void freshInitializationAndRepeatedUpgradeRemoveRetiredTablesAndPreserveCurrentData() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:cleanup_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        var bootstrap = new LocalDatabaseBootstrap(source);
        bootstrap.run(null);
        assertAbsent(jdbc);
        for (String table : List.of("oa_crm_contract", "oa_finance_record", "oa_hr_employee",
                "oa_recruit_application", "oa_commission_rule", "oa_process_instance", "oa_mobile_message_read")) {
            assertEquals(1, tableCount(jdbc, table), table);
        }
        // Simulate an existing installation with historical tables and real current business data.
        for (String table : RETIRED) {
            jdbc.execute("CREATE TABLE " + table + " (id BIGINT PRIMARY KEY)");
            jdbc.update("INSERT INTO " + table + " (id) VALUES (123)");
        }
        jdbc.update("UPDATE sys_user SET email='preserved@example.test' WHERE user_id=2");
        jdbc.update("UPDATE oa_merchant SET remark='CUSTOMER-CONTENT-TO-KEEP' WHERE id=1");
        jdbc.update("INSERT INTO oa_mobile_message_read(tenant_id,user_id,message_key) VALUES(1,1,'keep-read-state')");
        var users = jdbc.queryForList("SELECT * FROM sys_user ORDER BY user_id");
        var customers = jdbc.queryForList("SELECT * FROM oa_merchant ORDER BY id");
        var grants = jdbc.queryForList("SELECT * FROM sys_role_module ORDER BY role_id,module_id");
        var requests = jdbc.queryForList("SELECT * FROM oa_workflow_request ORDER BY id");
        bootstrap.run(null);
        assertAbsent(jdbc);
        bootstrap.run(null);
        assertAbsent(jdbc);
        assertEquals(users, jdbc.queryForList("SELECT * FROM sys_user ORDER BY user_id"));
        assertEquals(customers, jdbc.queryForList("SELECT * FROM oa_merchant ORDER BY id"));
        assertEquals(grants, jdbc.queryForList("SELECT * FROM sys_role_module ORDER BY role_id,module_id"));
        assertEquals(requests, jdbc.queryForList("SELECT * FROM oa_workflow_request ORDER BY id"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oa_mobile_message_read WHERE message_key='keep-read-state'", Integer.class));
    }

    private static int tableCount(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name=?", Integer.class, table);
    }

    private static void assertAbsent(JdbcTemplate jdbc) {
        for (String table : RETIRED) assertEquals(0, tableCount(jdbc, table), table);
    }
}
