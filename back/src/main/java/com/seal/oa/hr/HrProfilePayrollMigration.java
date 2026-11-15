package com.seal.oa.hr;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Additive upgrades for both local H2 and existing MySQL installations. */
@Component
@Order(1)
public class HrProfilePayrollMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public HrProfilePayrollMigration(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(ApplicationArguments args) {
        ensureColumn("oa_hr_employee", "work_province", "VARCHAR(64) NOT NULL DEFAULT '广东省'");
        ensureColumn("oa_hr_employee", "work_city", "VARCHAR(64) NOT NULL DEFAULT '广州市'");
        ensureColumn("oa_hr_payroll_item", "tax_entered", "INT NOT NULL DEFAULT 0");
        ensureColumn("oa_hr_payroll_item", "tax_reason", "VARCHAR(1000)");
        ensureColumn("oa_hr_payroll_item", "tax_operator_id", "BIGINT");
        ensureColumn("oa_hr_payroll_item", "tax_update_time", "DATETIME");
        jdbc.execute("CREATE TABLE IF NOT EXISTS oa_hr_upgrade (upgrade_key VARCHAR(100) PRIMARY KEY)");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM oa_hr_upgrade WHERE upgrade_key='manual_tax_v1'", Integer.class) == 0) {
            // Preserve prior explicitly adjusted/verified taxes and frozen historical payrolls.
            // Never claim that a historic confirmation was entered by a particular operator.
            jdbc.update("""
                    UPDATE oa_hr_payroll_item SET tax_entered=1,tax_reason='升级前已核对或已冻结的历史个税'
                    WHERE tax_entered=0 AND (tax<>0 OR verified=1 OR payroll_id IN
                    (SELECT id FROM oa_hr_payroll WHERE status IN ('approving','approved')))
                    """);
            jdbc.update("INSERT INTO oa_hr_upgrade(upgrade_key) VALUES('manual_tax_v1')");
        }
    }

    private void ensureColumn(String table, String column, String definition) {
        var count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE LOWER(table_schema)=LOWER(?) AND LOWER(table_name)=? AND LOWER(column_name)=?
                """, Integer.class, schema(), table, column);
        if (count == null || count == 0) jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    private String schema() {
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) connection ->
                "MySQL".equals(connection.getMetaData().getDatabaseProductName()) ? connection.getCatalog() : connection.getSchema());
    }
}
