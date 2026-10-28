MERGE INTO sys_module (
    module_id, tenant_id, parent_id, module_name, route_name, path, component,
    icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache,
    is_app, is_frame, query, redirect, status
) KEY (module_id) VALUES (
    341100, 1, 340309, '使用文档', 'Docs', 'docs', 'docs/index',
    'iconfont-bangzhu', 99, 1, 'system:docs:list', 0, '', 0,
    '', '', '', '', '0'
);

INSERT INTO sys_config (
    config_id, config_name, config_key, config_value, config_type, create_time, remark
) SELECT
    100, '使用文档入口', 'sys.docs.enabled', 'true', 'Y', CURRENT_TIMESTAMP,
    '控制顶部“使用文档”入口是否显示，默认开启'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'sys.docs.enabled'
);

INSERT INTO sys_config (
    config_id, config_name, config_key, config_value, config_type, create_time, remark
) SELECT
    101, '使用文档标题', 'sys.docs.title', '使用文档', 'N', CURRENT_TIMESTAMP,
    '顶部文档入口及文档中心标题'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'sys.docs.title'
);
