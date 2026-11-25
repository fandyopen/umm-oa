package com.seal.oa;

import com.seal.oa.system.ModuleActionService;
import com.seal.oa.system.ModuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:umm_oa_docs_workspace;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
class DocumentationModuleIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired ModuleService modules;
    @Autowired ModuleActionService actions;

    @Test
    void documentationWorkspaceMigratesGrantsAndPreservesLaterConfiguration() {
        Map<String, Object> root = modules.module(341100L);
        assertEquals(0L, ((Number) root.get("parentId")).longValue());
        assertEquals("Layout", root.get("component"));
        assertEquals("/docs", root.get("path"));
        assertEquals(6L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_module WHERE parent_id=341100", Long.class));
        for (long page = 341701; page <= 341706; page++) assertTrue(actions.allActions(page).isEmpty());

        // Only the explicitly authorized rule is returned; technical documentation stays inaccessible.
        List<Map<String, Object>> routes = modules.routers(1L, false, Set.of("docs:commission:list"), Set.of(341704L));
        Map<String, Object> docs = routes.stream().filter(row -> ((Number) row.get("moduleId")).longValue() == 341100L).findFirst().orElseThrow();
        assertEquals(List.of(341704L), children(docs).stream().map(row -> ((Number) row.get("moduleId")).longValue()).toList());
        assertTrue(modules.routers(1L, false, Set.of(), Set.of()).stream().noneMatch(row -> ((Number) row.get("moduleId")).longValue() == 341100L));
        assertTrue(modules.routers(1L, false, Set.of(), Set.of(341100L)).isEmpty());

        jdbc.update("UPDATE sys_module SET status='1' WHERE module_id=341100");
        assertTrue(modules.routers(1L, false, Set.of("docs:commission:list"), Set.of(341704L)).isEmpty());
        jdbc.update("UPDATE sys_module SET status='0' WHERE module_id=341100");

        // Simulate an existing installation before the documentation module promotion.
        jdbc.update("DELETE FROM sys_permission_migration WHERE migration_key='docs_workspace_v1'");
        jdbc.update("UPDATE sys_module SET parent_id=340309, path='docs', component='docs/index', hidden=1, perms='system:docs:list' WHERE module_id=341100");
        jdbc.update("INSERT INTO sys_role(role_id,role_name,role_key) VALUES (9991,'Documentation migration','docs_migration_test')");
        jdbc.update("INSERT INTO sys_role_module(role_id,module_id) VALUES (9991,341100)");
        migrate();
        assertEquals(0L, ((Number) modules.module(341100L).get("parentId")).longValue());
        assertEquals(6L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_module WHERE role_id=9991 AND module_id BETWEEN 341701 AND 341706", Long.class));

        jdbc.update("DELETE FROM sys_role_module WHERE role_id=9991 AND module_id=341702");
        jdbc.update("UPDATE sys_module SET module_name='Company handbook', path='handbook', hidden=1, module_sort=25, status='1' WHERE module_id=341703");
        migrate();
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_module WHERE role_id=9991 AND module_id=341702", Long.class));
        Map<String, Object> customized = modules.module(341703L);
        assertEquals("Company handbook", customized.get("moduleName"));
        assertEquals("handbook", customized.get("path"));
        assertEquals("1", customized.get("status"));
        assertEquals(1, ((Number) customized.get("hidden")).intValue());
        assertEquals(25, ((Number) customized.get("moduleSort")).intValue());
    }

    private void migrate() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("docs-module.sql"));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.execute(dataSource);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> children(Map<String, Object> node) {
        return (List<Map<String, Object>>) node.get("children");
    }
}
