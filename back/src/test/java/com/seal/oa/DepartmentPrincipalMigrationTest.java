package com.seal.oa;

import com.seal.oa.common.LocalDatabaseBootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DepartmentPrincipalMigrationTest {
    @Test
    void migrationPreservesExistingSupervisorAndIsSafeToRepeat() throws Exception {
        var source = database(64);
        var jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO sys_dept_principal VALUES (1, 12)");
        jdbc.update("INSERT INTO sys_dept_leader VALUES (1, 11), (2, 22)");
        var bootstrap = new LocalDatabaseBootstrap(source);
        try (Connection connection = source.getConnection()) {
            ReflectionTestUtils.invokeMethod(bootstrap, "migrateDepartmentSupervisors", connection);
            ReflectionTestUtils.invokeMethod(bootstrap, "migrateDepartmentSupervisors", connection);
        }
        assertEquals(12L, jdbc.queryForObject(
                "SELECT user_id FROM sys_dept_principal WHERE dept_id = 1", Long.class));
        assertEquals(22L, jdbc.queryForObject(
                "SELECT user_id FROM sys_dept_principal WHERE dept_id = 2", Long.class));
        assertEquals("user12", jdbc.queryForObject("SELECT leader FROM sys_dept WHERE dept_id = 1", String.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO sys_dept_principal VALUES (1, 11)"));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.queryForList("SELECT * FROM sys_dept_leader"));
    }

    @Test
    void failedMigrationRollsBackSupervisorChangesAndKeepsLegacyData() throws Exception {
        var source = database(2);
        var jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO sys_dept_principal VALUES (1, 12)");
        jdbc.update("INSERT INTO sys_dept_leader VALUES (2, 22)");
        var bootstrap = new LocalDatabaseBootstrap(source);
        try (Connection connection = source.getConnection()) {
            assertThrows(Exception.class, () ->
                    ReflectionTestUtils.invokeMethod(bootstrap, "migrateDepartmentSupervisors", connection));
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dept_principal", Integer.class));
        assertEquals(12L, jdbc.queryForObject("SELECT user_id FROM sys_dept_principal", Long.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dept_leader", Integer.class));
    }

    private DriverManagerDataSource database(int leaderLength) {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sys_dept (dept_id BIGINT PRIMARY KEY, del_flag VARCHAR(1), leader VARCHAR("
                + leaderLength + "), update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_user (user_id BIGINT PRIMARY KEY, dept_id BIGINT, user_name VARCHAR(64),"
                + " del_flag VARCHAR(1), status VARCHAR(1))");
        jdbc.execute("CREATE TABLE sys_dept_principal (dept_id BIGINT, user_id BIGINT, PRIMARY KEY(dept_id, user_id))");
        jdbc.execute("CREATE TABLE sys_dept_leader (dept_id BIGINT, user_id BIGINT, PRIMARY KEY(dept_id, user_id))");
        jdbc.update("INSERT INTO sys_dept VALUES (1, '0', NULL, NULL), (2, '0', NULL, NULL)");
        jdbc.update("INSERT INTO sys_user VALUES (11, 1, 'user11', '0', '0'),"
                + " (12, 1, 'user12', '0', '0'), (22, 2, 'user22', '0', '0')");
        return source;
    }
}
