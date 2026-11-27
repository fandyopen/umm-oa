package com.seal.oa.system;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Additive upgrade for H2 and MySQL; existing notices remain unpublished drafts. */
@Component
@Order(2)
public class NoticeSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    public NoticeSchemaMigration(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override
    public void run(ApplicationArguments args) {
        ensure("tenant_id", "BIGINT NOT NULL DEFAULT 1");
        ensure("publish_status", "VARCHAR(16) NOT NULL DEFAULT 'draft'");
        ensure("published_by", "VARCHAR(100)");
        ensure("published_time", "DATETIME");
        ensure("update_by", "VARCHAR(100)");
        ensure("update_time", "DATETIME");
    }
    private void ensure(String column, String definition) {
        String schema = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) connection ->
                "MySQL".equals(connection.getMetaData().getDatabaseProductName()) ? connection.getCatalog() : connection.getSchema());
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(table_schema)=LOWER(?) AND LOWER(table_name)='sys_notice' AND LOWER(column_name)=?", Integer.class, schema, column);
        if (count == null || count == 0) jdbc.execute("ALTER TABLE sys_notice ADD COLUMN " + column + " " + definition);
    }
}
