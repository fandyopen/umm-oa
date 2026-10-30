package com.seal.oa.common;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(0)
@ConditionalOnProperty(
        prefix = "app.database",
        name = "bootstrap-enabled",
        havingValue = "true"
)
public class LocalDatabaseBootstrap implements ApplicationRunner {

    private static final String MARKER_TABLE = "sys_bootstrap_marker";
    private static final List<String> DATA_SCRIPTS = List.of(
            "data.sql",
            "system-reference-data.sql",
            "capability-data.sql",
            "module-data.sql",
            "organization-data.sql",
            "merchant-data.sql",
            "demo-form-data.sql",
            "parity-data.sql",
            "todo-parity-data.sql"
    );
    private static final List<String> UPGRADE_SCRIPTS = List.of(
            "organization-permission.sql",
            "workflow-action.sql",
            "docs-config.sql",
            "crm-upgrade.sql",
            "crm-menu.sql",
            "retire-business-modules.sql",
            "administrator-model.sql",
            "retire-admin-groups.sql"
    );

    private final DataSource dataSource;
    public LocalDatabaseBootstrap(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (isInitialized(connection)) {
                migrateDepartmentSupervisors(connection);
                runScripts(connection, UPGRADE_SCRIPTS);
                return;
            }

            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            configure(populator);
            populator.setIgnoreFailedDrops(true);
            populator.addScript(new ClassPathResource("schema.sql"));
            DATA_SCRIPTS.forEach(script -> populator.addScript(new ClassPathResource(script)));
            populator.populate(connection);
            migrateDepartmentSupervisors(connection);

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE sys_bootstrap_marker (
                            marker_id INT PRIMARY KEY,
                            initialized_at TIMESTAMP NOT NULL
                        )
                        """);
                statement.executeUpdate("""
                        INSERT INTO sys_bootstrap_marker (marker_id, initialized_at)
                        VALUES (1, CURRENT_TIMESTAMP)
                        """);
            }
            runScripts(connection, UPGRADE_SCRIPTS);
        }
    }

    private void runScripts(Connection connection, List<String> scripts) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        configure(populator);
        scripts.forEach(script -> populator.addScript(new ClassPathResource(script)));
        populator.populate(connection);
    }

    private void configure(ResourceDatabasePopulator populator) {
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.setContinueOnError(false);
    }

    private boolean isInitialized(Connection connection) throws SQLException {
        if (!tableExists(connection, MARKER_TABLE)) {
            return false;
        }
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM " + MARKER_TABLE + " WHERE marker_id = 1"
             )) {
            return resultSet.next() && resultSet.getInt(1) == 1;
        }
    }

    private void migrateDepartmentSupervisors(Connection connection) throws SQLException {
        if (!tableExists(connection, "sys_dept_leader")) {
            return;
        }

        Map<Long, Long> supervisors = new LinkedHashMap<>();
        collectDepartmentSupervisors(connection, "sys_dept_principal", supervisors);
        collectDepartmentSupervisors(connection, "sys_dept_leader", supervisors);

        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM sys_dept_principal");
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO sys_dept_principal (dept_id, user_id) VALUES (?, ?)"
            ); PreparedStatement updateDepartment = connection.prepareStatement("""
                    UPDATE sys_dept
                    SET leader = COALESCE((SELECT user_name FROM sys_user WHERE user_id = ?), ''),
                        update_time = CURRENT_TIMESTAMP
                    WHERE dept_id = ?
                    """)) {
                for (Map.Entry<Long, Long> entry : supervisors.entrySet()) {
                    insert.setLong(1, entry.getKey());
                    insert.setLong(2, entry.getValue());
                    insert.addBatch();

                    updateDepartment.setLong(1, entry.getValue());
                    updateDepartment.setLong(2, entry.getKey());
                    updateDepartment.addBatch();
                }
                insert.executeBatch();
                updateDepartment.executeBatch();
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(autoCommit);
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE UNIQUE INDEX IF NOT EXISTS uk_dept_principal_department
                    ON sys_dept_principal (dept_id)
                    """);
            statement.executeUpdate("DROP TABLE IF EXISTS sys_dept_leader");
        }
    }

    private void collectDepartmentSupervisors(
            Connection connection,
            String tableName,
            Map<Long, Long> supervisors
    ) throws SQLException {
        if (!tableExists(connection, tableName)) {
            return;
        }
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     """
                             SELECT relation.dept_id, relation.user_id FROM %s relation
                             JOIN sys_dept d ON d.dept_id = relation.dept_id AND d.del_flag = '0'
                             JOIN sys_user u ON u.user_id = relation.user_id
                                 AND u.dept_id = relation.dept_id AND u.del_flag = '0' AND u.status = '0'
                             ORDER BY relation.dept_id, relation.user_id
                             """.formatted(tableName)
             )) {
            while (rows.next()) {
                supervisors.putIfAbsent(rows.getLong("dept_id"), rows.getLong("user_id"));
            }
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(
                connection.getCatalog(),
                null,
                "%",
                new String[]{"TABLE"}
        )) {
            while (tables.next()) {
                if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
