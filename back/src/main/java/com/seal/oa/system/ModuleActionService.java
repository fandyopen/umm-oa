package com.seal.oa.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.oa.common.ApiException;
import com.seal.oa.common.JdbcMaps;
import com.seal.oa.workflow.WorkflowActionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ModuleActionService {

    private static final ActionDefinition ADD = new ActionDefinition("add", "新增");
    private static final ActionDefinition EDIT = new ActionDefinition("edit", "修改");
    private static final ActionDefinition REMOVE = new ActionDefinition("remove", "删除");
    private static final ActionDefinition EXPORT = new ActionDefinition("export", "导出");
    private static final ActionDefinition TRACK = new ActionDefinition("track", "跟踪");
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
            340771L,
            340772L
    );

    private static final Set<Long> READ_ONLY_MODULES = Set.of(
            340341L,
            340342L,
            340527L,
            340785L,
            341100L,
            340644L,
            341101L
    );

    private static final Map<Long, List<ActionDefinition>> MODULE_ACTIONS = Map.ofEntries(
            Map.entry(340773L, List.of(new ActionDefinition("design", "流程设计"))),
            Map.entry(341002L, List.of(EDIT, EXPORT, new ActionDefinition("salary_view", "查看薪资与发薪账户"), new ActionDefinition("salary_manage", "维护薪资与发薪账户"), new ActionDefinition("salary_submit", "提交定薪调薪", "hr.salary.submit"), new ActionDefinition("approve", "审批薪资标准"))),
            Map.entry(341003L, List.of(EDIT, EXPORT, new ActionDefinition("submit", "提交人事异动", "hr.change.submit"), new ActionDefinition("approve", "审批人事异动"), new ActionDefinition("handover", "办理离职交接"))),
            Map.entry(341004L, List.of(EXPORT, new ActionDefinition("import", "导入钉钉考勤"), new ActionDefinition("confirm", "确认考勤"))),
            Map.entry(341005L, List.of(EDIT, EXPORT, new ActionDefinition("generate", "生成或重算工资"), new ActionDefinition("submit", "提交工资审批", "hr.payroll.submit"), new ActionDefinition("approve", "审批工资"), new ActionDefinition("publish", "发布工资条"), new ActionDefinition("pay", "登记工资发放"))),
            Map.entry(341006L, List.of()),
            Map.entry(341409L, List.of(EXPORT, new ActionDefinition("pay", "登记工资发放"))),
            Map.entry(341301L, List.of(ADD, EDIT, REMOVE, EXPORT,
                    new ActionDefinition("import", "批量导入"), new ActionDefinition("copy", "复制职位"),
                    new ActionDefinition("demand", "提交需求确认", "recruit.job.demand"),
                    new ActionDefinition("change", "职位变更", "recruit.job.change"),
                    new ActionDefinition("withdraw", "撤回需求"), new ActionDefinition("assign", "分配负责人"),
                    new ActionDefinition("status", "变更状态"), new ActionDefinition("warning", "配置预警"))),
            Map.entry(341302L, List.of(ADD, EDIT, REMOVE, EXPORT,
                    new ActionDefinition("import", "批量导入"), new ActionDefinition("contact", "查看联系方式"),
                    new ActionDefinition("resume", "上传简历"), new ActionDefinition("preview", "预览简历"),
                    new ActionDefinition("download", "下载简历"), new ActionDefinition("tag", "管理标签"),
                    new ActionDefinition("assign", "分配跟进任务"), new ActionDefinition("status", "变更人才状态"),
                    new ActionDefinition("withdraw", "撤回申请"), new ActionDefinition("dispute", "处理归属争议"),
                    new ActionDefinition("merge", "申请合并", "recruit.talent.merge"),
                    new ActionDefinition("handover", "申请转交", "recruit.talent.handover"))),
            Map.entry(341303L, List.of(ADD, EXPORT, new ActionDefinition("recommend", "提交与撤回推荐"),
                    new ActionDefinition("feedback", "登记客户反馈"), new ActionDefinition("interview", "安排面试"),
                    new ActionDefinition("offer", "管理Offer"), new ActionDefinition("entry", "登记入职"),
                    new ActionDefinition("guarantee", "保证期管理"), new ActionDefinition("exception", "异常处理"))),
            Map.entry(341201L, List.of(ADD, EDIT, REMOVE, new ActionDefinition("submit", "提交审批", "crm.contract.submit"), new ActionDefinition("sign", "签署生效", "crm.contract.sign"))),
            Map.entry(341202L, List.of(new ActionDefinition("approve", "审批"))),
            Map.entry(341203L, List.of(new ActionDefinition("receive", "登记回款"))),
            Map.entry(341204L, List.of(EDIT)),
            Map.entry(341205L, List.of(new ActionDefinition("handle", "处理提醒"))),
            Map.entry(341400L, List.of(ADD, EDIT, EXPORT,
                    new ActionDefinition("confirm", "确认或撤销应收"),
                    new ActionDefinition("invoice_submit", "提交开票审批", "crm.finance.invoice_submit"),
                    new ActionDefinition("receipt_confirm", "提交回款确认", "crm.finance.receipt_confirm"),
                    new ActionDefinition("refund_submit", "提交退款审批", "crm.finance.refund_submit"),
                    new ActionDefinition("adjust_confirm", "提交核销或冲销", "crm.finance.adjust_confirm"),
                    new ActionDefinition("cost_confirm", "确认成本", "crm.finance.cost_confirm"),
                    new ActionDefinition("approve", "办理财务审批或退回"),
                    new ActionDefinition("invoice_issue", "登记实际开票"), new ActionDefinition("refund_pay", "登记实际退款"),
                    new ActionDefinition("void", "作废财务记录"), new ActionDefinition("collect", "登记催收计划"),
                    new ActionDefinition("reconcile", "查看对账报表"))),
            Map.entry(340590L, List.of(ADD, EDIT, REMOVE, TRACK)),
            Map.entry(340644L, ADD_EDIT_REMOVE),
            Map.entry(340352L, List.of(ADD, EDIT, REMOVE, EXPORT, TRACK)),
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
    private final ObjectProvider<WorkflowActionHandler> handlers;
    private boolean tablesReady;

    public ModuleActionService(JdbcTemplate jdbc, ObjectMapper objectMapper,
                               ObjectProvider<WorkflowActionHandler> handlers) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.handlers = handlers;
    }

    @Transactional
    public void bootstrap() {
        ensureTables();
        migrateEditableActions();
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
        backfillRoleActions();
        backfillLegacyCustomerExportPermission();
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
        applyDefaultRoleActions("hr_archive_actions_v1",19L,341002L,List.of("edit","export","salary_view","salary_manage","salary_submit"));
        applyDefaultRoleActions("hr_change_actions_v1",19L,341003L,List.of("edit","export","submit","handover"));
        applyDefaultRoleActions("hr_attendance_actions_v1",19L,341004L,List.of("import","confirm","export"));
        applyDefaultRoleActions("hr_payroll_actions_v1",19L,341005L,List.of("generate","edit","export","submit","publish"));
        for(Long financeRole:jdbc.queryForList("SELECT DISTINCT role_id FROM sys_role_module_action WHERE module_id=341400 AND action_code='refund_pay'",Long.class))
            applyDefaultRoleActions("hr_finance_pay_"+financeRole+"_v1",financeRole,341409L,List.of("pay","export"));
        migrateWorkflowAdministration();
        migrateWorkflowOverview();
        migrateContractNavigation();
        migrateContractSigning();
        migrateGenericFollowup();
        migrateOpportunitySubmission();
    }

    private void migrateOpportunitySubmission() {
        String key = "generic_opportunity_submission_v1";
        if (migrationApplied(key)) return;
        for (String action : List.of("add", "edit")) {
            Map<String, Object> config = Map.of("endpoint", ("add".equals(action) ? "POST" : "PUT") + " /project/business",
                    "titleTemplate", "商机：{name}", "displayFields", List.of(
                            Map.of("field", "name", "label", "商机名称"), Map.of("field", "customerName", "label", "客户名称"),
                            Map.of("field", "ownerUserName", "label", "商机负责人"), Map.of("field", "expectedAmount", "label", "预计金额")));
            jdbc.update("UPDATE sys_module_action SET handler_key=?,config_json=?,update_time=CURRENT_TIMESTAMP WHERE module_id=340590 AND action_code=? AND status<>'2' AND (handler_key IS NULL OR TRIM(handler_key)='')",
                    com.seal.oa.workflow.WorkflowApiRegistry.HANDLER, normalizedConfig(config), action);
        }
        jdbc.update("INSERT INTO sys_permission_migration(migration_key) VALUES (?)", key);
    }

    private void migrateGenericFollowup() {
        if (migrationApplied("generic_customer_followup_v1")) return;
        // Attach the existing business entry without restoring deleted buttons or enabling approval.
        Map<String, Object> config = Map.of(
                "endpoint", "POST /project/merchant/{id:\\d+}/followups",
                "pathMapping", Map.of("id", "customerId"), "bodyMapping", Map.of(),
                "titleTemplate", "客户跟进：{customerName}",
                "displayFields", List.of(Map.of("field", "customerName", "label", "客户名称"),
                        Map.of("field", "followupTime", "label", "跟进时间"), Map.of("field", "method", "label", "跟进方式"),
                        Map.of("field", "content", "label", "沟通内容"), Map.of("field", "nextPlan", "label", "下一步计划"),
                        Map.of("field", "nextFollowupTime", "label", "下次跟进时间")));
        jdbc.update("UPDATE sys_module_action SET handler_key=?,config_json=?,update_time=CURRENT_TIMESTAMP WHERE module_id=340352 AND action_code='track' AND status<>'2' AND (handler_key IS NULL OR TRIM(handler_key)='')",
                com.seal.oa.workflow.WorkflowApiRegistry.HANDLER, normalizedConfig(config));
        jdbc.update("INSERT INTO sys_permission_migration(migration_key) VALUES ('generic_customer_followup_v1')");
    }

    public synchronized void ensureTables() {
        if (tablesReady) return;
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
        ensureColumn("sys_module_action", "flow_design_enabled", "INT NOT NULL DEFAULT 0");
        ensureColumn("sys_module_action", "action_sort", "INT NOT NULL DEFAULT 0");
        ensureColumn("sys_module_action", "action_description", "VARCHAR(500)");
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
        tablesReady = true;
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
        if (moduleId <= 0L || "Layout".equals(component) || "ParentView".equals(component)) {
            return;
        }
        // Defaults seed each page only once; edits and deletions are authoritative.
        String seedKey = "module_actions_seed_v2_" + moduleId;
        if (migrationApplied(seedKey)) {
            return;
        }
        jdbc.update("INSERT INTO sys_permission_migration (migration_key) VALUES (?)", seedKey);
        seedModule(moduleId, perms == null ? "" : perms, component == null ? "" : component);
    }

    private void seedModule(long moduleId, String perms, String component) {
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
        return actionRows(moduleId, "a.status = '0'", """
                a.action_sort, CASE a.action_code
                    WHEN 'add' THEN 1 WHEN 'edit' THEN 2
                    WHEN 'remove' THEN 3 WHEN 'export' THEN 4
                    WHEN 'invite' THEN 5 WHEN 'track' THEN 6
                    WHEN 'transfer' THEN 7 WHEN 'split' THEN 8
                    WHEN 'notify' THEN 9 WHEN 'confirm' THEN 10
                    WHEN 'dismiss' THEN 11
                    WHEN 'manage' THEN 90 ELSE 50 END, a.action_code
                """);
    }

    public List<Map<String, Object>> allActions(long moduleId) {
        return actionRows(moduleId, "a.status <> '2'", "a.action_sort, a.action_code");
    }

    public List<Map<String, Object>> workflowActions(long moduleId) {
        return actionRows(moduleId, "a.status = '0' AND a.handler_key IS NOT NULL AND TRIM(a.handler_key) <> ''",
                "a.action_sort, a.action_code");
    }

    public Map<String, Object> action(long moduleId, String actionCode) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT module_id, action_code, action_name, permission_code, handler_key,
                       config_json, builtin, status, create_time, update_time, flow_design_enabled,
                       action_sort, action_description
                FROM sys_module_action WHERE module_id = ? AND action_code = ? AND status <> '2'
                """, moduleId, actionCode));
        if (rows.isEmpty()) return Map.of();
        describeBusinessAction(rows.get(0));
        return rows.get(0);
    }

    @Transactional
    public Map<String, Object> createAction(long moduleId, Map<String, Object> body) {
        Map<String, Object> module = requireModule(moduleId);
        String actionCode = text(body.get("actionCode"));
        actionCode = actionCode.isBlank()
                ? nextActionCode(moduleId)
                : normalizeActionCode(actionCode);
        String actionName = requiredText(body.get("actionName"), "动作名称不能为空");
        List<String> existingStatuses = jdbc.queryForList(
                "SELECT status FROM sys_module_action WHERE module_id = ? AND action_code = ? FOR UPDATE",
                String.class, moduleId, actionCode);
        boolean restoring = !existingStatuses.isEmpty() && "2".equals(existingStatuses.get(0));
        if (!existingStatuses.isEmpty() && !restoring) {
            throw new ApiException(400, "该模块动作编码已存在");
        }
        String permissionCode = text(body.get("permissionCode"));
        if (permissionCode.isBlank()) {
            permissionCode = defaultPermissionCode(moduleId, text(module.get("perms")), actionCode);
        }
        validateConfiguration(moduleId, actionCode, body);
        if (restoring) {
            // Reuse the original identity and history; deleted grants stay revoked.
            jdbc.update("""
                    UPDATE sys_module_action
                    SET action_name=?, permission_code=?, handler_key=?, config_json=?,
                        builtin=0, status=?, flow_design_enabled=0, action_sort=?, action_description=?,
                        update_time=CURRENT_TIMESTAMP
                    WHERE module_id=? AND action_code=?
                    """, actionName, permissionCode, optionalText(body.get("handlerKey"), 128),
                    normalizedConfig(body.get("configJson")), normalizeStatus(body.get("status")),
                    longValue(body.get("actionSort")), optionalText(body.get("actionDescription"), 500),
                    moduleId, actionCode);
            jdbc.update("""
                    UPDATE oa_module_action_flow SET approval_enabled=0, update_time=CURRENT_TIMESTAMP
                    WHERE module_id=? AND action_code=?
                    """, moduleId, actionCode);
            Map<String, Object> restored = new LinkedHashMap<>(action(moduleId, actionCode));
            restored.put("restored", true);
            return restored;
        }
        jdbc.update("""
                INSERT INTO sys_module_action (
                    module_id, action_code, action_name, permission_code,
                    handler_key, config_json, builtin, status, flow_design_enabled, action_sort, action_description
                ) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)
                """,
                moduleId,
                actionCode,
                actionName,
                permissionCode,
                optionalText(body.get("handlerKey"), 128),
                normalizedConfig(body.get("configJson")),
                normalizeStatus(body.get("status")),
                enabled(body.get("flowDesignEnabled")) ? 1 : 0,
                longValue(body.get("actionSort")),
                optionalText(body.get("actionDescription"), 500));
        return action(moduleId, actionCode);
    }

    public String nextActionCode(long moduleId) {
        requireModule(moduleId);
        for (int index = 1; index <= 9999; index++) {
            String candidate = String.format(Locale.ROOT, "action_%03d", index);
            if (!actionCodeExists(moduleId, candidate)) {
                return candidate;
            }
        }
        throw new ApiException(400, "该模块按钮数量已达上限");
    }

    @Transactional
    public void updateAction(long moduleId, String actionCode, Map<String, Object> body) {
        Map<String, Object> existing = action(moduleId, actionCode);
        if (existing.isEmpty()) {
            throw new ApiException(404, "模块动作不存在");
        }
        if (body.containsKey("actionCode") && !actionCode.equals(text(body.get("actionCode")))) {
            throw new ApiException(400, "动作编码是按钮的稳定标识，保存后不可修改");
        }
        Map<String, Object> values = new LinkedHashMap<>(existing);
        values.put("flowDesignEnabled", existing.get("flowDesignEnabled"));
        values.putAll(body);
        validateConfiguration(moduleId, actionCode, values);
        Map<String, Object> module = requireModule(moduleId);
        String permissionCode = text(values.get("permissionCode"));
        if (permissionCode.isBlank()) {
            permissionCode = defaultPermissionCode(moduleId, text(module.get("perms")), actionCode);
        }
        jdbc.update("""
                UPDATE sys_module_action
                SET action_name = ?, permission_code = ?, handler_key = ?, config_json = ?,
                    status = ?, flow_design_enabled = ?, action_sort = ?, action_description = ?,
                    builtin = 0, update_time = CURRENT_TIMESTAMP
                WHERE module_id = ? AND action_code = ?
                """,
                requiredText(values.get("actionName"), "动作名称不能为空"),
                permissionCode,
                optionalText(values.get("handlerKey"), 128),
                normalizedConfig(values.get("configJson")),
                normalizeStatus(values.get("status")),
                enabled(values.get("flowDesignEnabled")) ? 1 : 0,
                longValue(values.get("actionSort")),
                optionalText(values.get("actionDescription"), 500),
                moduleId,
                actionCode);
    }

    @Transactional
    public void deleteAction(long moduleId, String actionCode) {
        Map<String, Object> existing = action(moduleId, actionCode);
        if (existing.isEmpty()) {
            throw new ApiException(404, "模块动作不存在");
        }
        Long running = jdbc.queryForObject("""
                SELECT COUNT(*) FROM oa_workflow_request
                WHERE module_id = ? AND action_code = ? AND status = 'running'
                """, Long.class, moduleId, actionCode);
        if (running != null && running > 0) {
            throw new ApiException(409, "该按钮仍有审批中的申请，请先处理完成；可停用按钮阻止新申请");
        }
        jdbc.update("""
                DELETE FROM sys_role_module_action WHERE module_id = ? AND action_code = ?
                """, moduleId, actionCode);
        jdbc.update("""
                UPDATE sys_module_action SET status = '2', flow_design_enabled = 0,
                    update_time = CURRENT_TIMESTAMP WHERE module_id = ? AND action_code = ?
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

    public boolean isActionDisabled(long moduleId, String actionCode) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sys_module_action WHERE module_id = ? AND action_code = ? AND status <> '0'",
                Long.class, moduleId, actionCode) > 0L;
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

    private void backfillLegacyCustomerExportPermission() {
        jdbc.update("""
                INSERT INTO sys_role_module_action (role_id, module_id, action_code)
                SELECT DISTINCT legacy.role_id, 340352, 'export'
                FROM sys_role_menu legacy
                JOIN sys_menu menu ON menu.menu_id = legacy.menu_id
                JOIN sys_module_action action
                  ON action.module_id = 340352 AND action.action_code = 'export' AND action.status = '0'
                WHERE menu.perms = 'project:merchant:export'
                  AND NOT EXISTS (
                      SELECT 1 FROM sys_role_module_action existing
                      WHERE existing.role_id = legacy.role_id
                        AND existing.module_id = 340352
                        AND existing.action_code = 'export'
                  )
                """);
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
        if (!actionCodeExists(moduleId, action.code())) {
            jdbc.update("""
                    INSERT INTO sys_module_action (
                        module_id, action_code, action_name, permission_code,
                        handler_key, config_json, builtin, status, flow_design_enabled
                    ) VALUES (?, ?, ?, ?, ?, '{}', 0, '0', ?)
                    """,
                    moduleId,
                    action.code(),
                    action.name(),
                    permissionCode,
                    action.handlerKey(),
                    moduleId == 341201L && "submit".equals(action.code()) ? 1 : 0);
        }
    }

    private boolean migrationApplied(String key) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sys_permission_migration WHERE migration_key = ?",
                Long.class, key) > 0L;
    }

    private void migrateEditableActions() {
        String key = "editable_button_configuration_v1";
        if (migrationApplied(key)) return;
        jdbc.update("""
                UPDATE sys_module_action a SET builtin = 0,
                    flow_design_enabled = CASE WHEN a.handler_key IS NOT NULL
                    AND TRIM(a.handler_key) <> '' AND ((a.module_id = 341201 AND a.action_code = 'submit')
                      OR EXISTS (SELECT 1 FROM oa_module_action_flow b
                        WHERE b.module_id = a.module_id AND b.action_code = a.action_code))
                    THEN 1 ELSE 0 END
                """);
        jdbc.update("INSERT INTO sys_permission_migration (migration_key) VALUES (?)", key);
    }

    private void migrateWorkflowOverview() {
        String key = "workflow_configuration_overview_v1";
        if (migrationApplied(key)) return;
        jdbc.update("""
                INSERT INTO sys_role_module (role_id,module_id)
                SELECT DISTINCT source.role_id,340773 FROM sys_role_module source
                WHERE source.module_id IN (340771,340772) AND NOT EXISTS (
                    SELECT 1 FROM sys_role_module target WHERE target.role_id=source.role_id AND target.module_id=340773)
                """);
        jdbc.update("""
                INSERT INTO sys_role_module_action (role_id,module_id,action_code)
                SELECT DISTINCT source.role_id,340773,'design' FROM sys_role_module_action source
                WHERE ((source.module_id=340772 AND source.action_code IN ('manage','workflow'))
                    OR (source.module_id=340771 AND source.action_code='manage')) AND NOT EXISTS (
                    SELECT 1 FROM sys_role_module_action target
                    WHERE target.role_id=source.role_id AND target.module_id=340773 AND target.action_code='design')
                """);
        jdbc.update("INSERT INTO sys_permission_migration (migration_key) VALUES (?)", key);
    }

    private void migrateWorkflowAdministration() {
        String key = "module_workflow_administration_v1";
        if (migrationApplied(key)) return;
        insertAction(340772L, new ActionDefinition("workflow", "配置按钮流程"), "module:340772:workflow");
        // Legacy workflow administrators gain the new entry, never routing/deletion rights.
        jdbc.update("""
                INSERT INTO sys_role_module (role_id, module_id)
                SELECT DISTINCT old.role_id, 340772 FROM sys_role_module old
                WHERE old.module_id = 340771 AND NOT EXISTS (
                    SELECT 1 FROM sys_role_module current_grant
                    WHERE current_grant.role_id = old.role_id AND current_grant.module_id = 340772)
                """);
        jdbc.update("""
                INSERT INTO sys_role_module_action (role_id, module_id, action_code)
                SELECT DISTINCT old.role_id, 340772, 'workflow' FROM sys_role_module_action old
                WHERE old.module_id = 340771 AND old.action_code = 'manage' AND NOT EXISTS (
                    SELECT 1 FROM sys_role_module_action current_grant
                    WHERE current_grant.role_id = old.role_id AND current_grant.module_id = 340772
                      AND current_grant.action_code = 'workflow')
                """);
        jdbc.update("INSERT INTO sys_permission_migration (migration_key) VALUES (?)", key);
    }

    private boolean actionCodeExists(long moduleId, String actionCode) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sys_module_action WHERE module_id = ? AND action_code = ?",
                Long.class, moduleId, actionCode) > 0L;
    }

    private void migrateContractNavigation() {
        String key = "contract_navigation_consolidation_v1";
        if (migrationApplied(key)) return;
        // Relocate entry access only. Approval, receipts, revisions and handling keep their original grants.
        jdbc.update("""
                INSERT INTO sys_role_module (role_id, module_id)
                SELECT DISTINCT source.role_id, 341201 FROM sys_role_module source
                WHERE source.module_id IN (341203, 341204) AND NOT EXISTS (
                    SELECT 1 FROM sys_role_module target
                    WHERE target.role_id = source.role_id AND target.module_id = 341201)
                """);
        jdbc.update("""
                INSERT INTO sys_role_module (role_id, module_id)
                SELECT DISTINCT source.role_id, 340341 FROM sys_role_module source
                WHERE source.module_id = 341202 AND NOT EXISTS (
                    SELECT 1 FROM sys_role_module target
                    WHERE target.role_id = source.role_id AND target.module_id = 340341)
                """);
        jdbc.update("""
                INSERT INTO sys_role_module (role_id, module_id)
                SELECT DISTINCT source.role_id, 341101 FROM sys_role_module source
                WHERE source.module_id = 341205 AND NOT EXISTS (
                    SELECT 1 FROM sys_role_module target
                    WHERE target.role_id = source.role_id AND target.module_id = 341101)
                """);
        jdbc.update("INSERT INTO sys_permission_migration (migration_key) VALUES (?)", key);
    }

    private void migrateContractSigning() {
        String key = "contract_sign_workflow_v1";
        if (migrationApplied(key)) return;
        jdbc.update("""
                UPDATE sys_module_action SET handler_key = 'crm.contract.sign', update_time = CURRENT_TIMESTAMP
                WHERE module_id = 341201 AND action_code = 'sign' AND status <> '2'
                  AND (handler_key IS NULL OR TRIM(handler_key) = '')
                """);
        jdbc.update("INSERT INTO sys_permission_migration (migration_key) VALUES (?)", key);
    }

    private List<Map<String, Object>> actionRows(long moduleId, String condition, String order) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT a.*, b.process_design_id, b.approval_enabled, d.status AS process_status
                FROM sys_module_action a
                LEFT JOIN oa_module_action_flow b ON b.module_id = a.module_id
                  AND b.action_code = a.action_code AND b.tenant_app_id = 1
                LEFT JOIN oa_process_design d ON d.id = b.process_design_id
                WHERE a.module_id = ? AND
                """ + condition + " ORDER BY " + order, moduleId));
        for (Map<String, Object> row : rows) {
            describeBusinessAction(row);
            boolean designEnabled = enabled(row.get("flowDesignEnabled"));
            row.put("flowDesignEnabled", designEnabled);
            row.put("workflowState", !designEnabled ? "未开启"
                    : text(row.get("handlerKey")).isBlank() ? "待接入"
                    : text(row.get("processDesignId")).isBlank() ? "未配置"
                    : !"published".equals(text(row.get("processStatus"))) ? "未发布"
                    : enabled(row.get("approvalEnabled")) ? "已启用" : "已停用");
        }
        return rows;
    }

    public void requireFlowDesign(long moduleId, String actionCode) {
        Map<String, Object> action = action(moduleId, actionCode);
        if (action.isEmpty()) throw new ApiException(400, "请选择真实存在的按钮，流程只能配置在按钮级别");
        if (!enabled(action.get("flowDesignEnabled"))) {
            throw new ApiException(400, "该按钮尚未开启流程设计，请先在模块管理的按钮修改页开启");
        }
        if (!"0".equals(text(action.get("status")))) throw new ApiException(400, "该按钮已停用");
        validateConfiguration(moduleId, actionCode, action);
    }

    private void validateConfiguration(long moduleId, String actionCode, Map<String, Object> values) {
        String handlerKey = text(values.get("handlerKey"));
        ActionDefinition businessAction = MODULE_ACTIONS.getOrDefault(moduleId, List.of()).stream()
                .filter(item -> item.code().equals(actionCode)).findFirst().orElse(null);
        if (businessAction != null && businessAction.handlerKey() != null
                && !businessAction.handlerKey().isBlank() && !businessAction.handlerKey().equals(handlerKey)) {
            throw new ApiException(400, "该按钮的业务行为须使用执行器 " + businessAction.handlerKey());
        }
        if (handlerKey.isBlank()) {
            return;
        }
        WorkflowActionHandler handler = handlers.orderedStream().filter(item -> item.supports(handlerKey))
                .findFirst().orElseThrow(() -> new ApiException(400, "业务执行器未注册：" + handlerKey));
        if (!handler.supportsAction(moduleId, actionCode)) throw new ApiException(400, "所选执行器不适用于该按钮");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> config = objectMapper.readValue(normalizedConfig(values.get("configJson")), Map.class);
            handler.validateConfiguration(moduleId, actionCode, config);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new ApiException(400, "执行器配置不是有效JSON");
        }
    }

    public static boolean isManagedBusinessAction(long moduleId, String actionCode) {
        return MODULE_ACTIONS.getOrDefault(moduleId, List.of()).stream()
                .anyMatch(item -> item.code().equals(actionCode) && item.handlerKey() != null && !item.handlerKey().isBlank());
    }

    public static boolean enabled(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value))
                || "1".equals(String.valueOf(value));
    }

    private void describeBusinessAction(Map<String, Object> row) {
        ActionDefinition definition = MODULE_ACTIONS.getOrDefault(longValue(row.get("moduleId")), List.of()).stream()
                .filter(item -> item.code().equals(text(row.get("actionCode"))))
                .filter(item -> item.handlerKey() != null && !item.handlerKey().isBlank()).findFirst().orElse(null);
        row.put("handlerManaged", definition != null);
        if (definition != null) row.put("businessActionName", definition.name());
    }

    public void requireWorkflowExecution(long moduleId, String actionCode) {
        Map<String, Object> action = action(moduleId, actionCode);
        if (text(action.get("handlerKey")).isBlank()) {
            throw new ApiException(400, "该按钮尚未接入业务执行器，可先保存流程草稿，接入后再发布启用审批");
        }
        validateConfiguration(moduleId, actionCode, action);
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
        if ("page".equals(actionCode)) throw new ApiException(400, "page 是旧页面流程保留编码，不能用作按钮编码");
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
