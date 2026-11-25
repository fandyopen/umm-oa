-- Existing MySQL 8 databases: customer sales performance menu upgrade.
INSERT INTO sys_module (
    module_id, tenant_id, parent_id, module_name, route_name, path, component,
    icon, module_sort, hidden, perms, has_flow, no_cache, status
)
SELECT 341112, 1, 340310, '部门看板', 'CrmSalesPerformance', 'sales-performance',
       'eos/crm/salesPerformance/index', 'iconfont-chengbenfenxi-', 6, 0,
       'project:salesPerformance:list', 0, 0, '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id=341112);

INSERT INTO sys_role_module(role_id,module_id)
SELECT source.role_id,341112 FROM sys_role_module source
WHERE source.module_id=340590
AND NOT EXISTS(SELECT 1 FROM sys_role_module granted WHERE granted.role_id=source.role_id AND granted.module_id=341112);
