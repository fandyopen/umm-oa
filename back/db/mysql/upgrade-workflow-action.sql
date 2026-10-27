-- Existing MySQL databases only. Fresh installations already include these
-- tables in umm_oa.sql.
-- Run this migration once against the existing umm_oa database.

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
    UNIQUE KEY uk_module_action_flow (tenant_app_id, module_id, action_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    result_json TEXT,
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME,
    complete_time DATETIME,
    KEY idx_oa_workflow_request_instance (process_instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE sys_module_action ADD COLUMN handler_key VARCHAR(128);
ALTER TABLE sys_module_action ADD COLUMN config_json TEXT;
ALTER TABLE sys_module_action ADD COLUMN builtin INT NOT NULL DEFAULT 0;
ALTER TABLE sys_module_action ADD COLUMN status VARCHAR(1) NOT NULL DEFAULT '0';
ALTER TABLE sys_module_action ADD COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE sys_module_action ADD COLUMN update_time DATETIME;

ALTER TABLE oa_workflow_request ADD COLUMN handler_key VARCHAR(128);
ALTER TABLE oa_workflow_request ADD COLUMN config_json TEXT;

UPDATE sys_module SET has_flow = 0 WHERE module_id = 341001;
