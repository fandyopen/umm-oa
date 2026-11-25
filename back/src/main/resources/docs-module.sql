-- One-time documentation workspace migration. Preserve later menu edits and revoked grants.
CREATE TABLE IF NOT EXISTS sys_permission_migration (
    migration_key VARCHAR(64) PRIMARY KEY,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, no_cache, redirect, status)
SELECT 341100, 1, 0, '使用文档', 'Docs', '/docs', 'Layout', 'iconfont-daimawendang', 7, 1, '', 0, 0, '', '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission_migration WHERE migration_key = 'docs_workspace_v1')
  AND NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341100);

UPDATE sys_module SET parent_id = 0, path = '/docs', component = 'Layout',
    hidden = 1, perms = '', module_sort = 7, redirect = ''
WHERE module_id = 341100 AND parent_id = 340309 AND component = 'docs/index'
  AND NOT EXISTS (SELECT 1 FROM sys_permission_migration WHERE migration_key = 'docs_workspace_v1');

INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, no_cache, redirect, status)
SELECT 341701, 1, 341100, '路由地图', 'DocsRouteMap', 'route-map', 'docs/pages/RouteMap', 'iconfont-daimawendang', 1, 0, 'docs:route:list', 0, 0, '', '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission_migration WHERE migration_key = 'docs_workspace_v1')
  AND NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341701);

INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, no_cache, redirect, status)
SELECT 341702, 1, 341100, 'API 文档', 'DocsApiReference', 'api-docs', 'docs/pages/ApiDocs', 'iconfont-daimawendang', 2, 0, 'docs:api:list', 0, 0, '', '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission_migration WHERE migration_key = 'docs_workspace_v1')
  AND NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341702);

INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, no_cache, redirect, status)
SELECT 341703, 1, 341100, '模块说明', 'DocsModuleGuide', 'module-docs', 'docs/pages/ModuleDocs', 'iconfont-daimawendang', 3, 0, 'docs:module:list', 0, 0, '', '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission_migration WHERE migration_key = 'docs_workspace_v1')
  AND NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341703);

INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, no_cache, redirect, status)
SELECT 341704, 1, 341100, '提成核算规则', 'DocsCommissionGuide', 'commission-rules', 'docs/pages/CommissionRules', 'iconfont-daimawendang', 4, 0, 'docs:commission:list', 0, 0, '', '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission_migration WHERE migration_key = 'docs_workspace_v1')
  AND NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341704);

INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, no_cache, redirect, status)
SELECT 341705, 1, 341100, '招聘流程规则', 'DocsRecruitmentGuide', 'recruitment-rules', 'docs/pages/RecruitmentRules', 'iconfont-daimawendang', 5, 0, 'docs:recruitment:list', 0, 0, '', '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission_migration WHERE migration_key = 'docs_workspace_v1')
  AND NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341705);

INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, no_cache, redirect, status)
SELECT 341706, 1, 341100, '客户与合同规则', 'DocsCustomerContractGuide', 'customer-contract-rules', 'docs/pages/CustomerContractRules', 'iconfont-daimawendang', 6, 0, 'docs:customerContract:list', 0, 0, '', '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission_migration WHERE migration_key = 'docs_workspace_v1')
  AND NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341706);

-- Keep legacy documentation access only for its existing grantees.
INSERT INTO sys_role_module (role_id, module_id)
SELECT source.role_id, page.module_id FROM sys_role_module source
CROSS JOIN sys_module page
WHERE source.module_id = 341100 AND page.parent_id = 341100
  AND NOT EXISTS (SELECT 1 FROM sys_permission_migration WHERE migration_key = 'docs_workspace_v1')
  AND NOT EXISTS (SELECT 1 FROM sys_role_module existing WHERE existing.role_id = source.role_id AND existing.module_id = page.module_id);

-- Keep legacy documentation access only for its existing grantees.
INSERT INTO sys_dept_module (dept_id, module_id)
SELECT source.dept_id, page.module_id FROM sys_dept_module source
CROSS JOIN sys_module page
WHERE source.module_id = 341100 AND page.parent_id = 341100
  AND NOT EXISTS (SELECT 1 FROM sys_permission_migration WHERE migration_key = 'docs_workspace_v1')
  AND NOT EXISTS (SELECT 1 FROM sys_dept_module existing WHERE existing.dept_id = source.dept_id AND existing.module_id = page.module_id);

INSERT INTO sys_permission_migration (migration_key)
SELECT 'docs_workspace_v1' WHERE NOT EXISTS (SELECT 1 FROM sys_permission_migration WHERE migration_key = 'docs_workspace_v1');
