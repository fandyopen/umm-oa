-- Existing MySQL 8 databases: customer finance upgrade; preserves business data.
-- Contract receivables come from signed snapshots; manual charges are stored separately.
CREATE TABLE IF NOT EXISTS oa_finance_receivable (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    due_date DATE NOT NULL,
    source_event VARCHAR(200),
    status VARCHAR(24) NOT NULL DEFAULT 'draft',
    attachment TEXT,
    remark VARCHAR(1000),
    revision INT NOT NULL DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME,
    FOREIGN KEY (customer_id) REFERENCES oa_merchant(id)
);
CREATE TABLE IF NOT EXISTS oa_finance_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_type VARCHAR(24) NOT NULL,
    customer_id BIGINT NOT NULL,
    contract_id BIGINT,
    term_key VARCHAR(64),
    receivable_id BIGINT,
    job_id BIGINT,
    project_name VARCHAR(200),
    original_receipt_id BIGINT,
    original_record_id BIGINT,
    amount DECIMAL(18,2) NOT NULL,
    business_date DATE,
    category VARCHAR(100),
    payee VARCHAR(200),
    channel VARCHAR(100),
    reference_no VARCHAR(100),
    invoice_no VARCHAR(100) UNIQUE,
    invoice_title VARCHAR(200),
    tax_no VARCHAR(100),
    reason VARCHAR(1000),
    remark VARCHAR(1000),
    attachment TEXT,
    status VARCHAR(24) NOT NULL DEFAULT 'draft',
    revision INT NOT NULL DEFAULT 0,
    workflow_request_id BIGINT,
    receipt_id BIGINT UNIQUE,
    create_token VARCHAR(64) NOT NULL UNIQUE,
    create_by VARCHAR(64),
    create_user_id BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirm_by VARCHAR(64),
    confirm_time DATETIME,
    update_time DATETIME,
    FOREIGN KEY (customer_id) REFERENCES oa_merchant(id),
    FOREIGN KEY (contract_id) REFERENCES oa_crm_contract(id),
    FOREIGN KEY (receivable_id) REFERENCES oa_finance_receivable(id),
    FOREIGN KEY (receipt_id) REFERENCES oa_crm_contract_receipt(id)
);
CREATE TABLE IF NOT EXISTS oa_finance_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type VARCHAR(24) NOT NULL,
    entity_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    content TEXT NOT NULL,
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS oa_finance_collection (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    contract_id BIGINT,
    term_key VARCHAR(64),
    receivable_id BIGINT,
    owner_user_id BIGINT NOT NULL,
    next_date DATE,
    content VARCHAR(2000) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'open',
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES oa_merchant(id)
);

INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, no_cache, status)
SELECT 341400, 1, 0, '财务与回款管理', 'CustomerFinance', '/finance', 'eos/crm/finance/index', 'iconfont-caiwuguanli', 5, 0, 'finance:ledger:list', 0, 0, '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341400);

-- Promote the legacy CRM child once; keep its permissions, buttons and workflows.
UPDATE sys_module SET parent_id = 0, path = '/finance', module_sort = 5
WHERE module_id = 341400 AND parent_id = 340310;
