INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, no_cache, redirect, status)
SELECT 341400, 1, 0, '财务与回款管理', 'CustomerFinance', '/finance', 'eos/crm/finance/index', 'iconfont-caiwuguanli', 5, 0, 'finance:ledger:list', 0, 0, '/finance/ledger', '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341400);

-- Promote the legacy CRM child once; keep its permissions, buttons and workflows.
UPDATE sys_module SET parent_id = 0, path = '/finance', module_sort = 5
WHERE module_id = 341400 AND parent_id = 340310;

-- Existing roles keep access when a workspace menu is first introduced.
-- Later restarts preserve administrator edits to individual menu grants.
INSERT INTO sys_role_module (role_id, module_id)
SELECT source.role_id, 341401 FROM sys_role_module source
WHERE source.module_id = 341400
  AND NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341401)
  AND NOT EXISTS (SELECT 1 FROM sys_role_module existing WHERE existing.role_id = source.role_id AND existing.module_id = 341401);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, no_cache, status)
SELECT 341401, 1, 341400, '应收台账', 'FinanceLedger', 'ledger', 'eos/crm/finance/ledger', 'iconfont-caiwuguanli', 1, 0, 'finance:ledger:list', 0, 0, '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341401);

INSERT INTO sys_role_module (role_id, module_id)
SELECT source.role_id, 341402 FROM sys_role_module source
WHERE source.module_id = 341400
  AND NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341402)
  AND NOT EXISTS (SELECT 1 FROM sys_role_module existing WHERE existing.role_id = source.role_id AND existing.module_id = 341402);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, no_cache, status)
SELECT 341402, 1, 341400, '开票管理', 'FinanceInvoices', 'invoice', 'eos/crm/finance/invoice', 'iconfont-caiwuguanli', 2, 0, 'finance:ledger:list', 0, 0, '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341402);

INSERT INTO sys_role_module (role_id, module_id)
SELECT source.role_id, 341403 FROM sys_role_module source
WHERE source.module_id = 341400
  AND NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341403)
  AND NOT EXISTS (SELECT 1 FROM sys_role_module existing WHERE existing.role_id = source.role_id AND existing.module_id = 341403);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, no_cache, status)
SELECT 341403, 1, 341400, '回款登记', 'FinanceReceipts', 'receipt', 'eos/crm/finance/receipt', 'iconfont-caiwuguanli', 3, 0, 'finance:ledger:list', 0, 0, '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341403);

INSERT INTO sys_role_module (role_id, module_id)
SELECT source.role_id, 341404 FROM sys_role_module source
WHERE source.module_id = 341400
  AND NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341404)
  AND NOT EXISTS (SELECT 1 FROM sys_role_module existing WHERE existing.role_id = source.role_id AND existing.module_id = 341404);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, no_cache, status)
SELECT 341404, 1, 341400, '退款管理', 'FinanceRefunds', 'refund', 'eos/crm/finance/refund', 'iconfont-caiwuguanli', 4, 0, 'finance:ledger:list', 0, 0, '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341404);

INSERT INTO sys_role_module (role_id, module_id)
SELECT source.role_id, 341405 FROM sys_role_module source
WHERE source.module_id = 341400
  AND NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341405)
  AND NOT EXISTS (SELECT 1 FROM sys_role_module existing WHERE existing.role_id = source.role_id AND existing.module_id = 341405);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, no_cache, status)
SELECT 341405, 1, 341400, '核销与冲销', 'FinanceAdjustments', 'adjustment', 'eos/crm/finance/adjustment', 'iconfont-caiwuguanli', 5, 0, 'finance:ledger:list', 0, 0, '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341405);

INSERT INTO sys_role_module (role_id, module_id)
SELECT source.role_id, 341406 FROM sys_role_module source
WHERE source.module_id = 341400
  AND NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341406)
  AND NOT EXISTS (SELECT 1 FROM sys_role_module existing WHERE existing.role_id = source.role_id AND existing.module_id = 341406);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, no_cache, status)
SELECT 341406, 1, 341400, '成本记录', 'FinanceCosts', 'cost', 'eos/crm/finance/cost', 'iconfont-caiwuguanli', 6, 0, 'finance:ledger:list', 0, 0, '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341406);

INSERT INTO sys_role_module (role_id, module_id)
SELECT source.role_id, 341407 FROM sys_role_module source
WHERE source.module_id = 341400
  AND NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341407)
  AND NOT EXISTS (SELECT 1 FROM sys_role_module existing WHERE existing.role_id = source.role_id AND existing.module_id = 341407);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, no_cache, status)
SELECT 341407, 1, 341400, '逾期催收', 'FinanceCollectionTasks', 'collections', 'eos/crm/finance/collections', 'iconfont-caiwuguanli', 7, 0, 'finance:ledger:list', 0, 0, '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341407);

INSERT INTO sys_role_module (role_id, module_id)
SELECT source.role_id, 341408 FROM sys_role_module source
WHERE source.module_id = 341400
  AND NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341408)
  AND NOT EXISTS (SELECT 1 FROM sys_role_module existing WHERE existing.role_id = source.role_id AND existing.module_id = 341408);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, no_cache, status)
SELECT 341408, 1, 341400, '对账报表', 'FinanceReports', 'reconcile', 'eos/crm/finance/reconcile', 'iconfont-caiwuguanli', 8, 0, 'finance:ledger:list', 0, 0, '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341408);

UPDATE sys_module SET redirect = '/finance/ledger'
WHERE module_id = 341400 AND (redirect IS NULL OR redirect = '');
