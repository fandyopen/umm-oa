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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

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
            "retire-business-modules.sql"
    );

    private final DataSource dataSource;
    public LocalDatabaseBootstrap(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (isInitialized(connection)) {
                runScripts(connection, UPGRADE_SCRIPTS);
                return;
            }

            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            configure(populator);
            populator.setIgnoreFailedDrops(true);
            populator.addScript(new ClassPathResource("schema.sql"));
            DATA_SCRIPTS.forEach(script -> populator.addScript(new ClassPathResource(script)));
            populator.populate(connection);

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
