INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, no_cache, status)
SELECT 341400, 1, 0, '财务与回款管理', 'CustomerFinance', '/finance', 'eos/crm/finance/index', 'iconfont-caiwuguanli', 5, 0, 'finance:ledger:list', 0, 0, '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341400);

-- Promote the legacy CRM child once; keep its permissions, buttons and workflows.
UPDATE sys_module SET parent_id = 0, path = '/finance', module_sort = 5
WHERE module_id = 341400 AND parent_id = 340310;
