CREATE TABLE IF NOT EXISTS oa_crm_contract (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    customer_id BIGINT NOT NULL,
    opportunity_id BIGINT,
    parent_contract_id BIGINT,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    service_type VARCHAR(64) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    version_no INT NOT NULL DEFAULT 1,
    effective_version INT NOT NULL DEFAULT 0,
    revision INT NOT NULL DEFAULT 0,
    approval_user_id BIGINT,
    submit_user_id BIGINT,
    approval_comment VARCHAR(2000),
    terms_json TEXT NOT NULL,
    attachment TEXT,
    remark VARCHAR(2000),
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME,
    FOREIGN KEY (customer_id) REFERENCES oa_merchant(id)
);

CREATE TABLE IF NOT EXISTS oa_crm_contract_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    snapshot_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    submit_user_id BIGINT NOT NULL,
    approval_user_id BIGINT NOT NULL,
    approval_comment VARCHAR(2000),
    submit_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approval_time DATETIME,
    signed_date DATE,
    UNIQUE (contract_id, version_no),
    FOREIGN KEY (contract_id) REFERENCES oa_crm_contract(id)
);

CREATE TABLE IF NOT EXISTS oa_crm_contract_receipt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    term_key VARCHAR(64) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    receipt_date DATE NOT NULL,
    remark VARCHAR(1000),
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (contract_id) REFERENCES oa_crm_contract(id)
);

CREATE TABLE IF NOT EXISTS oa_crm_contract_reminder_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    task_id VARCHAR(180) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (contract_id) REFERENCES oa_crm_contract(id)
);
