-- Readonly configuration overview. Configuration remains in the existing workflow tables.
INSERT INTO sys_module (
    module_id, tenant_id, parent_id, module_name, route_name, path, component,
    icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache,
    is_app, is_frame, query, redirect, status
) SELECT
    340773, 1, 340309, '审批流程', 'WorkflowManage', 'workflows',
    'system/workflow/index', 'iconfont-mokuai', 2, 0, 'sys:workflow:list', 1, '',
    0, '', '', '', '', '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 340773);
