-- Approval inbox and actual decision actors; preserves business data and existing grants.
CREATE TABLE IF NOT EXISTS oa_approval_decision (
    task_id VARCHAR(128) PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    instance_id VARCHAR(128) NOT NULL,
    assignee_id BIGINT,
    assignee_name VARCHAR(64),
    assignee_dept_id BIGINT,
    assignee_dept_name VARCHAR(64),
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(64),
    operator_dept_name VARCHAR(64),
    decision VARCHAR(16) NOT NULL,
    comment VARCHAR(2000),
    delegated INT NOT NULL DEFAULT 0,
    complete_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_approval_decision_instance (instance_id)
);
INSERT INTO sys_module (
    module_id, tenant_id, parent_id, module_name, route_name, path, component,
    icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache,
    is_app, is_frame, query, redirect, status
) SELECT
    341600, 1, 340340, '代审批', 'ApprovalInbox', 'approvals',
    'eos/todoItem/approvals/index', 'iconfont-daibanshixiang7', 0, 0, 'todoItem:approval:list', 0, '',
    1, '', '', '', '', '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341600);

UPDATE sys_module SET module_name = '代审批'
WHERE module_id = 341600 AND module_name = '待审批';
