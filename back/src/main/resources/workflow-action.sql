CREATE TABLE IF NOT EXISTS oa_module_action_flow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    tenant_app_id BIGINT NOT NULL DEFAULT 1,
    module_id BIGINT NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    process_design_id VARCHAR(128) NOT NULL,
    approval_enabled INT NOT NULL DEFAULT 1,
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME,
    UNIQUE (tenant_app_id, module_id, action_code)
);

CREATE TABLE IF NOT EXISTS oa_workflow_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    module_id BIGINT NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    payload_json TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'running',
    process_instance_id VARCHAR(128),
    process_task_id VARCHAR(128),
    process_task_name VARCHAR(200),
    handler_key VARCHAR(128),
    config_json TEXT,
    result_json TEXT,
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME,
    complete_time DATETIME
);

ALTER TABLE sys_module_action ADD COLUMN IF NOT EXISTS handler_key VARCHAR(128);
ALTER TABLE sys_module_action ADD COLUMN IF NOT EXISTS config_json TEXT;
ALTER TABLE sys_module_action ADD COLUMN IF NOT EXISTS builtin INT NOT NULL DEFAULT 0;
ALTER TABLE sys_module_action ADD COLUMN IF NOT EXISTS status VARCHAR(1) NOT NULL DEFAULT '0';
ALTER TABLE sys_module_action ADD COLUMN IF NOT EXISTS create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE sys_module_action ADD COLUMN IF NOT EXISTS update_time DATETIME;

ALTER TABLE oa_workflow_request ADD COLUMN IF NOT EXISTS handler_key VARCHAR(128);
ALTER TABLE oa_workflow_request ADD COLUMN IF NOT EXISTS config_json TEXT;

CREATE INDEX IF NOT EXISTS idx_oa_workflow_request_instance
    ON oa_workflow_request (process_instance_id);
