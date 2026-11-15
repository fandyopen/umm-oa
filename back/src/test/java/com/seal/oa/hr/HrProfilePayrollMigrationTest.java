package com.seal.oa.hr;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class HrProfilePayrollMigrationTest {
    @Test
    void legacyUpgradePreservesFrozenAndConfirmedTaxAndDoesNotAutoConfirmFutureRows() {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:hr_migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE oa_hr_employee(user_id BIGINT PRIMARY KEY)");
        jdbc.execute("INSERT INTO oa_hr_employee VALUES(4)");
        jdbc.execute("CREATE TABLE oa_hr_payroll(id BIGINT PRIMARY KEY,status VARCHAR(24))");
        jdbc.execute("INSERT INTO oa_hr_payroll VALUES(1,'draft'),(2,'approved'),(3,'approving')");
        jdbc.execute("CREATE TABLE oa_hr_payroll_item(id BIGINT PRIMARY KEY,payroll_id BIGINT,tax DECIMAL(18,2),verified INT)");
        jdbc.execute("INSERT INTO oa_hr_payroll_item VALUES(1,1,0,0),(2,1,0,1),(3,1,15,0),(4,2,0,0),(5,3,0,0)");
        var migration=new HrProfilePayrollMigration(jdbc);
        migration.run(null);
        assertEquals("广州市",jdbc.queryForObject("SELECT work_city FROM oa_hr_employee WHERE user_id=4",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT tax_entered FROM oa_hr_payroll_item WHERE id=1",Integer.class));
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM oa_hr_payroll_item WHERE tax_entered=1",Integer.class));
        jdbc.execute("INSERT INTO oa_hr_payroll_item(id,payroll_id,tax,verified) VALUES(6,1,0,1)");
        migration.run(null);
        assertEquals(0,jdbc.queryForObject("SELECT tax_entered FROM oa_hr_payroll_item WHERE id=6",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM oa_hr_upgrade",Integer.class));
    }
}
