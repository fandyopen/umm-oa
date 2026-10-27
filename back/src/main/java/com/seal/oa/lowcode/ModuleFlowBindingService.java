package com.seal.oa.lowcode;

import com.seal.oa.common.JdbcMaps;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ModuleFlowBindingService {

    private final JdbcTemplate jdbc;

    public ModuleFlowBindingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> binding(long tenantAppId, long moduleId, String actionCode) {
        List<Map<String, Object>> rows = JdbcMaps.camelCase(jdbc.queryForList("""
                SELECT id, tenant_app_id, module_id, action_code, process_design_id,
                       approval_enabled, create_time, update_time
                FROM oa_module_action_flow
                WHERE tenant_app_id = ? AND module_id = ? AND action_code = ?
                """, tenantAppId, moduleId, actionCode));
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    public Map<String, Object> activeDesign(long tenantAppId, long moduleId, String actionCode) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT d.id, d.tenant_app_id, d.menu_id, d.menu_name, d.process_data,
                       d.update_time, b.action_code, b.approval_enabled
                FROM oa_module_action_flow b
                JOIN oa_process_design d ON d.id = b.process_design_id
                WHERE b.tenant_app_id = ? AND b.module_id = ? AND b.action_code = ?
                  AND b.approval_enabled = 1 AND d.status = 'published'
                """, tenantAppId, moduleId, actionCode);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    public Map<String, Object> boundDesign(long tenantAppId, long moduleId, String actionCode) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT d.id, d.tenant_app_id, d.menu_id, d.menu_name, d.process_data,
                       d.update_time, b.action_code, b.approval_enabled
                FROM oa_module_action_flow b
                JOIN oa_process_design d ON d.id = b.process_design_id
                WHERE b.tenant_app_id = ? AND b.module_id = ? AND b.action_code = ?
                  AND d.status = 'published'
                """, tenantAppId, moduleId, actionCode);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    @Transactional
    public void saveBinding(
            long tenantAppId,
            long moduleId,
            String actionCode,
            String processDesignId,
            boolean approvalEnabled,
            String operator
    ) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        int updated = jdbc.update("""
                UPDATE oa_module_action_flow
                SET process_design_id = ?, approval_enabled = ?, create_by = ?, update_time = ?
                WHERE tenant_app_id = ? AND module_id = ? AND action_code = ?
                """,
                processDesignId,
                approvalEnabled ? 1 : 0,
                operator,
                now,
                tenantAppId,
                moduleId,
                actionCode);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO oa_module_action_flow (
                        tenant_id, tenant_app_id, module_id, action_code, process_design_id,
                        approval_enabled, create_by, create_time, update_time
                    ) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    tenantAppId,
                    moduleId,
                    actionCode,
                    processDesignId,
                    approvalEnabled ? 1 : 0,
                    operator,
                    now,
                    now);
        }
    }
}
