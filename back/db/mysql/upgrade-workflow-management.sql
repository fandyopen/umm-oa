CREATE TABLE IF NOT EXISTS oa_workflow_catalog (
    id VARCHAR(128) PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    module_id BIGINT NOT NULL,
    flow_name VARCHAR(120) NOT NULL,
    approval_enabled INT NOT NULL DEFAULT 1,
    draft_data TEXT,
    draft_approval_enabled INT,
    revision BIGINT NOT NULL DEFAULT 0,
    create_by VARCHAR(64),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 两种配置入口共享原发布及按钮绑定表。
INSERT INTO sys_module (
    module_id, tenant_id, parent_id, module_name, route_name, path, component,
    icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache,
    is_app, is_frame, query, redirect, status
) SELECT
    340773, 1, 340309, '审批流程', 'WorkflowManage', 'workflows',
    'system/workflow/index', 'iconfont-mokuai', 2, 0, 'sys:workflow:list', 1, '',
    0, '', '', '', '', '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 340773);

-- ModuleActionBootstrap creates the separate design action and migrates existing
-- workflow administrators' entry/design grants once when the updated backend starts.
