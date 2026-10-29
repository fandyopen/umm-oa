MERGE INTO sys_module (
    module_id, tenant_id, parent_id, module_name, route_name, path, component,
    icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache,
    is_app, is_frame, query, redirect, status
) KEY (module_id) VALUES (
    340310, 1, 0, '客户管理', 'CRM', '/crm', 'Layout',
    'iconfont-xiaoshouloudou', 1, 0, '', 1, '', 0,
    '', '', '', 'noRedirect', '0'
);

MERGE INTO sys_module (
    module_id, tenant_id, parent_id, module_name, route_name, path, component,
    icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache,
    is_app, is_frame, query, redirect, status
) KEY (module_id) VALUES (
    340352, 1, 340310, '客户档案', 'Customers', 'customers', 'eos/crm/customer/index',
    'iconfont-xiaoshou', 1, 0, 'project:merchant:list', 1, '', 0,
    '', '', '', '', '0'
);

MERGE INTO sys_module (
    module_id, tenant_id, parent_id, module_name, route_name, path, component,
    icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache,
    is_app, is_frame, query, redirect, status
) KEY (module_id) VALUES (
    340590, 1, 340310, '商机管理', 'Business-mange', 'opportunities', 'eos/business/index',
    'iconfont-shuju_shangji_shangjimingcheng_huaban1', 2, 0, 'project:business:list', 1, '', 0,
    '', '', '', '', '0'
);

MERGE INTO sys_module (
    module_id, tenant_id, parent_id, module_name, route_name, path, component,
    icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache,
    is_app, is_frame, query, redirect, status
) KEY (module_id) VALUES (
    340644, 1, 340310, '客户分析', 'CrmAnalysis', 'analysis', 'eos/crm/analysis/index',
    'iconfont-chengbenfenxi-', 3, 0, 'project:analysis:list', 0, '', 0,
    '', '', '', '', '0'
);

MERGE INTO sys_module (
    module_id, tenant_id, parent_id, module_name, route_name, path, component,
    icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache,
    is_app, is_frame, query, redirect, status
) KEY (module_id) VALUES (
    341101, 1, 340310, '风险提醒', 'CrmRisks', 'risks', 'eos/crm/risk/index',
    'iconfont-yujing', 4, 0, 'project:risk:list', 0, '', 0,
    '', '', '', '', '0'
);

INSERT INTO sys_role_module (role_id, module_id)
SELECT role_id, 341101 FROM sys_role_module source
WHERE source.module_id = 340644
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_module existing
      WHERE existing.role_id = source.role_id AND existing.module_id = 341101
  );
