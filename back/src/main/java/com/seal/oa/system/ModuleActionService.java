package com.seal.oa.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.JdbcMaps;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ModuleActionService {

    private static final ActionDefinition ADD = new ActionDefinition("add", "新增");
    private static final ActionDefinition EDIT = new ActionDefinition("edit", "修改");
    private static final ActionDefinition REMOVE = new ActionDefinition("remove", "删除");
    private static final ActionDefinition EXPORT = new ActionDefinition("export", "导出");
    private static final ActionDefinition TRACK = new ActionDefinition("track", "跟踪");
    private static final ActionDefinition TRANSFER = new ActionDefinition("transfer", "转化项目");
    private static final ActionDefinition INVITE =
            new ActionDefinition("invite", "邀请成员", "hr.member.invite");
    private static final ActionDefinition HR_TRANSFER =
            new ActionDefinition("transfer", "调整部门", "hr.member.transfer");
    private static final ActionDefinition DISMISS =
            new ActionDefinition("dismiss", "转为离职", "hr.member.dismiss");
    private static final ActionDefinition MANAGE = new ActionDefinition("manage", "管理");

    private static final List<ActionDefinition> CRUD_ACTIONS = List.of(ADD, EDIT, REMOVE, EXPORT);
    private static final List<ActionDefinition> ADD_EDIT_REMOVE = List.of(ADD, EDIT, REMOVE);
    private static final List<ActionDefinition> REMOVE_EXPORT = List.of(REMOVE, EXPORT);

    private static final Set<Long> MANAGE_ONLY_MODULES = Set.of(
            340597L,
            340771L
    );

    private static final Set<Long> READ_ONLY_MODULES = Set.of(
            340341L,
            340342L,
            340527L,
            340785L
    );

    private static final Map<Long, List<ActionDefinition>> MODULE_ACTIONS = Map.ofEntries(
            Map.entry(340590L, List.of(ADD, EDIT, REMOVE, TRACK, TRANSFER)),
            Map.entry(340644L, ADD_EDIT_REMOVE),
            Map.entry(340352L, CRUD_ACTIONS),
            Map.entry(340314L, CRUD_ACTIONS),
            Map.entry(340315L, CRUD_ACTIONS),
            Map.entry(340316L, ADD_EDIT_REMOVE),
            Map.entry(340318L, REMOVE_EXPORT),
            Map.entry(340319L, REMOVE_EXPORT),
            Map.entry(340782L, List.of(MANAGE, INVITE, HR_TRANSFER, DISMISS)),
            Map.entry(341001L, List.of(INVITE, HR_TRANSFER, DISMISS))
    );

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ModuleActionService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void bootstrap() {
        ensureTables();
        List<Map<String, Object>> modules = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT module_id, perms, component
                FROM sys_module WHERE status = '0'
                """));
        for (Map<String, Object> module : modules) {
            refreshModule(
                    longValue(module.get("moduleId")),
                    text(module.get("perms")),
                    text(module.get("component"))
            );
        }
        jdbc.update("""
                DELETE FROM sys_role_module_action rma
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM sys_module_action ma
                    WHERE ma.module_id = rma.module_id
                      AND ma.action_code = rma.action_code
                )
                """);
        jdbc.update("""
                DELETE FROM oa_module_action_flow binding
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM sys_module_action action
                    WHERE action.module_id = binding.module_id
                      AND action.action_code = binding.action_code
                )
                """);
        jdbc.update("""
                UPDATE oa_module_action_flow binding
                SET approval_enabled = 0, update_time = CURRENT_TIMESTAMP
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM sys_module_action action
                    WHERE action.module_id = binding.module_id
                      AND action.action_code = binding.action_code
                      AND action.status = '0'
                      AND action.handler_key IS NOT NULL
                      AND TRIM(action.handler_key) <> ''
                )
                """);
        backfillRoleActions();
        applyDefaultRoleActions(
                "hr_manager_actions_v1",
                19L,
                341001L,
                List.of("invite", "transfer", "dismiss")
        );
        removeRoleAction(
                "hr_manager_role_readonly_v1",
                19L,
                340597L,
                "manage"
        );
        mergeDuplicateHrRole();
    }

    public void ensureTables() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sys_module_action (
                    module_id BIGINT NOT NULL,
                    action_code VARCHAR(32) NOT NULL,
                    action_name VARCHAR(64) NOT NULL,
                    permission_code VARCHAR(128),
                    handler_key VARCHAR(128),
                    config_json TEXT,
                    builtin INT NOT NULL DEFAULT 0,
                    status VARCHAR(1) NOT NULL DEFAULT '0',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME,
                    PRIMARY KEY (module_id, action_code)
                )
                """);
        ensureColumn("sys_module_action", "handler_key", "VARCHAR(128)");
        ensureColumn("sys_module_action", "config_json", "TEXT");
        ensureColumn("sys_module_action", "builtin", "INT NOT NULL DEFAULT 0");
        ensureColumn("sys_module_action", "status", "VARCHAR(1) NOT NULL DEFAULT '0'");
        ensureColumn("sys_module_action", "create_time", "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");
        ensureColumn("sys_module_action", "update_time", "DATETIME");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sys_role_module_action (
                    role_id BIGINT NOT NULL,
                    module_id BIGINT NOT NULL,
                    action_code VARCHAR(32) NOT NULL,
                    PRIMARY KEY (role_id, module_id, action_code)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sys_permission_migration (
                    migration_key VARCHAR(64) PRIMARY KEY,
                    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private void ensureColumn(String table, String column, String definition) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE LOWER(table_name) = ? AND LOWER(column_name) = ?
                """, Long.class, table.toLowerCase(), column.toLowerCase());
        if (count == null || count == 0L) {
            jdbc.execute(
                    "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition
            );
        }
    }

    @Transactional
    public void refreshModule(long moduleId, String perms, String component) {
        jdbc.update("DELETE FROM sys_module_action WHERE module_id = ? AND builtin = 1", moduleId);
        if (moduleId <= 0L || "Layout".equals(component) || "ParentView".equals(component)) {
            return;
        }
        if (MANAGE_ONLY_MODULES.contains(moduleId)) {
            insertManageAction(moduleId, perms);
            return;
        }
        if (READ_ONLY_MODULES.contains(moduleId)) {
            return;
        }
        List<ActionDefinition> configuredActions = MODULE_ACTIONS.get(moduleId);
        if (configuredActions != null) {
            for (ActionDefinition action : configuredActions) {
                String permissionCode = perms.endsWith(":list")
                        ? perms.substring(0, perms.length() - 5) + ":" + action.code()
                        : "module:" + moduleId + ":" + action.code();
                insertAction(moduleId, action, permissionCode);
            }
            return;
        }
        if (perms.endsWith(":list")) {
            String base = perms.substring(0, perms.length() - 5);
            for (ActionDefinition action : CRUD_ACTIONS) {
                insertAction(moduleId, action, base + ":" + action.code());
            }
            return;
        }
        if (!perms.isBlank()) {
            insertManageAction(moduleId, perms);
            return;
        }
        if (component.startsWith("eos/")) {
            for (ActionDefinition action : CRUD_ACTIONS) {
                insertAction(moduleId, action, "module:" + moduleId + ":" + action.code());
            }
            return;
        }
        insertManageAction(moduleId, "");
    }

    public List<Map<String, Object>> actions(long moduleId) {
        return JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT module_id, action_code, action_name, permission_code, handler_key,
                       config_json, builtin, status, create_time, update_time
                FROM sys_module_action WHERE module_id = ? AND status = '0'
                ORDER BY CASE action_code
                    WHEN 'add' THEN 1 WHEN 'edit' THEN 2
                    WHEN 'remove' THEN 3 WHEN 'export' THEN 4
                    WHEN 'invite' THEN 5 WHEN 'track' THEN 6
                    WHEN 'transfer' THEN 7 WHEN 'split' THEN 8
                    WHEN 'notify' THEN 9 WHEN 'confirm' THEN 10
                    WHEN 'dismiss' THEN 11
                    WHEN 'manage' THEN 90 ELSE 50 END, action_code
                """, moduleId));
    }

    public List<Map<String, Object>> allActions(long moduleId) {
        return JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT module_id, action_code, action_name, permission_code, handler_key,
                       config_json, builtin, status, create_time, update_time
                FROM sys_module_action WHERE module_id = ?
                ORDER BY builtin DESC, action_code
                """, moduleId));
    }

    public List<Map<String, Object>> workflowActions(long moduleId) {
        return JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT module_id, action_code, action_name, permission_code, handler_key,
                       config_json, builtin, status, create_time, update_time
                FROM sys_module_action
                WHERE module_id = ? AND status = '0'
                  AND handler_key IS NOT NULL AND TRIM(handler_key) <> ''
                ORDER BY builtin DESC, action_code
                """, moduleId));
    }

    public Map<String, Object> action(long moduleId, String actionCode) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT module_id, action_code, action_name, permission_code, handler_key,
                       config_json, builtin, status, create_time, update_time
                FROM sys_module_action WHERE module_id = ? AND action_code = ?
                """, moduleId, actionCode));
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    @Transactional
    public void createAction(long moduleId, Map<String, Object> body) {
        Map<String, Object> module = requireModule(moduleId);
        String actionCode = normalizeActionCode(text(body.get("actionCode")));
        String actionName = requiredText(body.get("actionName"), "动作名称不能为空");
        if (!action(moduleId, actionCode).isEmpty()) {
            throw new ApiException(400, "该模块动作编码已存在");
        }
        String permissionCode = text(body.get("permissionCode"));
        if (permissionCode.isBlank()) {
            permissionCode = defaultPermissionCode(moduleId, text(module.get("perms")), actionCode);
        }
        jdbc.update("""
                INSERT INTO sys_module_action (
                    module_id, action_code, action_name, permission_code,
                    handler_key, config_json, builtin, status
                ) VALUES (?, ?, ?, ?, ?, ?, 0, ?)
                """,
                moduleId,
                actionCode,
                actionName,
                permissionCode,
                optionalText(body.get("handlerKey"), 128),
                normalizedConfig(body.get("configJson")),
                normalizeStatus(body.get("status")));
    }

    @Transactional
    public void updateAction(long moduleId, String actionCode, Map<String, Object> body) {
        Map<String, Object> existing = action(moduleId, actionCode);
        if (existing.isEmpty()) {
            throw new ApiException(404, "模块动作不存在");
        }
        if (longValue(existing.get("builtin")) == 1L) {
            throw new ApiException(400, "内置动作由代码维护，不能在此修改");
        }
        Map<String, Object> module = requireModule(moduleId);
        String permissionCode = text(body.get("permissionCode"));
        if (permissionCode.isBlank()) {
            permissionCode = defaultPermissionCode(moduleId, text(module.get("perms")), actionCode);
        }
        jdbc.update("""
                UPDATE sys_module_action
                SET action_name = ?, permission_code = ?, handler_key = ?, config_json = ?,
                    status = ?, update_time = CURRENT_TIMESTAMP
                WHERE module_id = ? AND action_code = ?
                """,
                requiredText(body.get("actionName"), "动作名称不能为空"),
                permissionCode,
                optionalText(body.get("handlerKey"), 128),
                normalizedConfig(body.get("configJson")),
                normalizeStatus(body.get("status")),
                moduleId,
                actionCode);
    }

    @Transactional
    public void deleteAction(long moduleId, String actionCode) {
        Map<String, Object> existing = action(moduleId, actionCode);
        if (existing.isEmpty()) {
            throw new ApiException(404, "模块动作不存在");
        }
        if (longValue(existing.get("builtin")) == 1L) {
            throw new ApiException(400, "内置动作不能删除");
        }
        jdbc.update("""
                DELETE FROM oa_module_action_flow WHERE module_id = ? AND action_code = ?
                """, moduleId, actionCode);
        jdbc.update("""
                DELETE FROM sys_role_module_action WHERE module_id = ? AND action_code = ?
                """, moduleId, actionCode);
        jdbc.update("""
                DELETE FROM sys_module_action WHERE module_id = ? AND action_code = ?
                """, moduleId, actionCode);
    }

    public boolean hasAction(long userId, long moduleId, String actionCode) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM sys_role_module_action rma
                JOIN sys_role r ON r.role_id = rma.role_id
                JOIN sys_user_role ur ON ur.role_id = rma.role_id
                JOIN sys_module_action ma
                  ON ma.module_id = rma.module_id AND ma.action_code = rma.action_code
                WHERE ur.user_id = ? AND rma.module_id = ? AND rma.action_code = ?
                  AND ma.status = '0' AND r.status = '0' AND r.del_flag = '0'
                """, Long.class, userId, moduleId, actionCode);
        return count != null && count > 0L;
    }

    public boolean hasModule(long userId, long moduleId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM sys_role_module rm
                JOIN sys_role r ON r.role_id = rm.role_id
                JOIN sys_user_role ur ON ur.role_id = rm.role_id
                WHERE ur.user_id = ? AND rm.module_id = ?
                  AND r.status = '0' AND r.del_flag = '0'
                """, Long.class, userId, moduleId);
        return count != null && count > 0L;
    }

    public void syncRole(long roleId, List<?> moduleIds, List<?> actionKeys) {
        Set<Long> grantedModules = new LinkedHashSet<>();
        Set<ActionKey> grantedActions = new LinkedHashSet<>();
        for (Object value : moduleIds) {
            long moduleId = longValue(value);
            if (moduleId > 0L) {
                grantedModules.add(moduleId);
            }
        }
        for (Object value : actionKeys) {
            ActionKey key = parseActionKey(value);
            if (key != null) {
                grantedModules.add(key.moduleId());
                grantedActions.add(key);
            }
        }

        jdbc.update("DELETE FROM sys_role_module WHERE role_id = ?", roleId);
        jdbc.update("DELETE FROM sys_role_module_action WHERE role_id = ?", roleId);
        for (Long moduleId : grantedModules) {
            jdbc.update("INSERT INTO sys_role_module (role_id, module_id) VALUES (?, ?)", roleId, moduleId);
        }
        for (ActionKey key : grantedActions) {
            Long count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM sys_module_action
                    WHERE module_id = ? AND action_code = ? AND status = '0'
                    """, Long.class, key.moduleId(), key.actionCode());
            if (count != null && count > 0L) {
                jdbc.update("""
                        INSERT INTO sys_role_module_action (role_id, module_id, action_code)
                        VALUES (?, ?, ?)
                        """, roleId, key.moduleId(), key.actionCode());
            }
        }
    }

    public List<Map<String, Object>> roleActions(long roleId) {
        return JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT rma.module_id, rma.action_code, ma.permission_code
                FROM sys_role_module_action rma
                LEFT JOIN sys_module_action ma
                  ON ma.module_id = rma.module_id AND ma.action_code = rma.action_code
                WHERE rma.role_id = ? AND ma.status = '0'
                ORDER BY rma.module_id, rma.action_code
                """, roleId));
    }

    private void backfillRoleActions() {
        Long existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_role_module_action",
                Long.class
        );
        if (existing != null && existing > 0L) {
            return;
        }
        List<Map<String, Object>> roleModules = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT rm.role_id, rm.module_id
                FROM sys_role_module rm
                """));
        for (Map<String, Object> roleModule : roleModules) {
            long roleId = longValue(roleModule.get("roleId"));
            long moduleId = longValue(roleModule.get("moduleId"));
            List<Map<String, Object>> actions = actions(moduleId);
            for (Map<String, Object> action : actions) {
                jdbc.update("""
                        INSERT INTO sys_role_module_action (role_id, module_id, action_code)
                        VALUES (?, ?, ?)
                        """, roleId, moduleId, action.get("actionCode"));
            }
        }
    }

    private void applyDefaultRoleActions(
            String migrationKey,
            long roleId,
            long moduleId,
            List<String> actionCodes
    ) {
        Long applied = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_permission_migration WHERE migration_key = ?
                """, Long.class, migrationKey);
        if (applied != null && applied > 0L) {
            return;
        }
        for (String actionCode : actionCodes) {
            Long existing = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM sys_role_module_action
                    WHERE role_id = ? AND module_id = ? AND action_code = ?
                    """, Long.class, roleId, moduleId, actionCode);
            if (existing == null || existing == 0L) {
                jdbc.update("""
                        INSERT INTO sys_role_module_action (role_id, module_id, action_code)
                        VALUES (?, ?, ?)
                        """, roleId, moduleId, actionCode);
            }
        }
        jdbc.update("""
                INSERT INTO sys_permission_migration (migration_key)
                VALUES (?)
                """, migrationKey);
    }

    private void removeRoleAction(
            String migrationKey,
            long roleId,
            long moduleId,
            String actionCode
    ) {
        Long applied = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_permission_migration WHERE migration_key = ?
                """, Long.class, migrationKey);
        if (applied != null && applied > 0L) {
            return;
        }
        jdbc.update("""
                DELETE FROM sys_role_module_action
                WHERE role_id = ? AND module_id = ? AND action_code = ?
                """, roleId, moduleId, actionCode);
        jdbc.update("""
                INSERT INTO sys_permission_migration (migration_key)
                VALUES (?)
                """, migrationKey);
    }

    private void mergeDuplicateHrRole() {
        String migrationKey = "hr_role_dedup_v1";
        Long applied = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_permission_migration WHERE migration_key = ?
                """, Long.class, migrationKey);
        if (applied != null && applied > 0L) {
            return;
        }
        jdbc.update("DELETE FROM sys_role_group_member WHERE role_id IN (19, 20)");
        jdbc.update("""
                INSERT INTO sys_role_group_member (group_id, role_id)
                SELECT 53, 19 WHERE EXISTS (
                    SELECT 1 FROM sys_role WHERE role_id = 19 AND del_flag = '0'
                )
                """);
        jdbc.update("DELETE FROM sys_user_role WHERE role_id = 20");
        jdbc.update("DELETE FROM sys_role_module WHERE role_id = 20");
        jdbc.update("DELETE FROM sys_role_module_action WHERE role_id = 20");
        jdbc.update("""
                UPDATE sys_role SET del_flag = '2'
                WHERE role_id = 20 AND role_name = '人事经理'
                """);
        jdbc.update("""
                INSERT INTO sys_permission_migration (migration_key)
                VALUES (?)
                """, migrationKey);
    }

    private void insertAction(
            long moduleId,
            ActionDefinition action,
            String permissionCode
    ) {
        int updated = jdbc.update("""
                UPDATE sys_module_action
                SET action_name = ?, permission_code = ?, handler_key = ?, config_json = '{}',
                    builtin = 1, status = '0', update_time = CURRENT_TIMESTAMP
                WHERE module_id = ? AND action_code = ?
                """,
                action.name(),
                permissionCode,
                action.handlerKey(),
                moduleId,
                action.code());
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO sys_module_action (
                        module_id, action_code, action_name, permission_code,
                        handler_key, config_json, builtin, status
                    ) VALUES (?, ?, ?, ?, ?, '{}', 1, '0')
                    """,
                    moduleId,
                    action.code(),
                    action.name(),
                    permissionCode,
                    action.handlerKey());
        }
    }

    private void insertManageAction(long moduleId, String permissionCode) {
        insertAction(
                moduleId,
                MANAGE,
                permissionCode == null || permissionCode.isBlank()
                        ? "module:" + moduleId + ":" + MANAGE.code()
                        : permissionCode
        );
    }

    private Map<String, Object> requireModule(long moduleId) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT module_id, module_name, perms, component
                FROM sys_module WHERE module_id = ?
                """, moduleId));
        if (rows.isEmpty()) {
            throw new ApiException(404, "模块不存在");
        }
        return rows.get(0);
    }

    private String normalizeActionCode(String value) {
        String actionCode = value == null ? "" : value.trim();
        if (!actionCode.matches("[a-z][a-z0-9_]{0,31}")) {
            throw new ApiException(
                    400,
                    "动作编码只能使用小写字母、数字和下划线，并以字母开头"
            );
        }
        return actionCode;
    }

    private String requiredText(Object value, String message) {
        String text = text(value);
        if (text.isBlank()) {
            throw new ApiException(400, message);
        }
        return text.length() > 64 ? text.substring(0, 64) : text;
    }

    private String optionalText(Object value, int maxLength) {
        String text = text(value);
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private String defaultPermissionCode(long moduleId, String perms, String actionCode) {
        if (perms.endsWith(":list")) {
            return perms.substring(0, perms.length() - 5) + ":" + actionCode;
        }
        return "module:" + moduleId + ":" + actionCode;
    }

    private String normalizedConfig(Object value) {
        if (value == null || text(value).isBlank()) {
            return "{}";
        }
        try {
            if (value instanceof String jsonText) {
                return objectMapper.writeValueAsString(objectMapper.readTree(jsonText));
            }
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new ApiException(400, "执行器配置必须是有效 JSON");
        }
    }

    private String normalizeStatus(Object value) {
        if (value instanceof Boolean enabled) {
            return enabled ? "0" : "1";
        }
        String text = text(value);
        return "1".equals(text) || "false".equalsIgnoreCase(text) ? "1" : "0";
    }

    private ActionKey parseActionKey(Object value) {
        String text = text(value);
        if (!text.startsWith("action:")) {
            return null;
        }
        String[] parts = text.split(":", 3);
        if (parts.length < 3) {
            return null;
        }
        long moduleId = longValue(parts[1]);
        return moduleId <= 0L || parts[2].isBlank()
                ? null
                : new ActionKey(moduleId, parts[2]);
    }

    private long longValue(Object value) {
        try {
            return value instanceof Number number
                    ? number.longValue()
                    : Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record ActionDefinition(String code, String name, String handlerKey) {
        private ActionDefinition(String code, String name) {
            this(code, name, "");
        }
    }

    private record ActionKey(long moduleId, String actionCode) {
    }
}
