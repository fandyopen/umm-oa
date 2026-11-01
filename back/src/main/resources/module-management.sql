UPDATE sys_module
SET hidden = 1, redirect = '/system/module'
WHERE module_id = 340771;

INSERT INTO sys_module (
    module_id, tenant_id, parent_id, module_name, route_name, path, component,
    icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache,
    is_app, is_frame, query, redirect, status
) SELECT
    340772, 1, 340309, '模块管理', 'ModuleManage', 'module',
    'system/module/index', 'iconfont-mokuai', 2, 0, 'sys:module', 0, '',
    0, '', '', '', '', '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 340772);
