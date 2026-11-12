CREATE TABLE IF NOT EXISTS oa_commission_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL, business_kind VARCHAR(16) NOT NULL,
    effective_date DATE NOT NULL, pool_rate DECIMAL(9,4) NOT NULL,
    development_share DECIMAL(9,4) NOT NULL, initial_release DECIMAL(9,4) NOT NULL,
    enabled INT NOT NULL DEFAULT 1, create_by BIGINT NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS oa_commission_attribution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL UNIQUE, customer_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL, contract_id BIGINT NOT NULL, term_key VARCHAR(64),
    rule_id BIGINT NOT NULL, rule_json TEXT NOT NULL, participants_json TEXT NOT NULL,
    business_kind VARCHAR(16) NOT NULL, basis_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'draft', revision INT NOT NULL DEFAULT 0,
    reason VARCHAR(1000) NOT NULL, create_by BIGINT NOT NULL,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS oa_commission_allocation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL,
    receipt_id BIGINT NOT NULL, attribution_id BIGINT NOT NULL,
    amount DECIMAL(18,2) NOT NULL, allocation_date DATE NOT NULL,
    UNIQUE(receipt_id, attribution_id)
);
CREATE TABLE IF NOT EXISTS oa_commission_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL,
    period VARCHAR(7) NOT NULL, cutoff_date DATE NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'draft', revision INT NOT NULL DEFAULT 0,
    signature VARCHAR(64) NOT NULL, scope_json TEXT NOT NULL, workflow_request_id BIGINT,
    payroll_id BIGINT UNIQUE, reason VARCHAR(1000), create_by BIGINT NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP
);
CREATE TABLE IF NOT EXISTS oa_commission_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, batch_id BIGINT NOT NULL,
    attribution_id BIGINT NOT NULL, user_id BIGINT NOT NULL, side VARCHAR(16) NOT NULL,
    amount DECIMAL(18,2) NOT NULL, snapshot_json TEXT NOT NULL,
    payroll_item_id BIGINT,
    UNIQUE(batch_id, attribution_id, user_id, side)
);
CREATE TABLE IF NOT EXISTS oa_commission_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL,
    entity_type VARCHAR(24) NOT NULL, entity_id BIGINT NOT NULL,
    action VARCHAR(100) NOT NULL, detail_json TEXT NOT NULL,
    operator_id BIGINT NOT NULL, create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO sys_module(module_id,tenant_id,parent_id,module_name,route_name,path,component,icon,module_sort,hidden,perms,has_flow,no_cache,status)
SELECT 341410,1,341400,'提成核算','FinanceCommission','commission','eos/crm/finance/commission','iconfont-caiwuguanli',10,0,'finance:commission:list',0,0,'0'
WHERE NOT EXISTS(SELECT 1 FROM sys_module WHERE module_id=341410);

CREATE INDEX IF NOT EXISTS idx_commission_attribution_tenant ON oa_commission_attribution(tenant_id,status);
CREATE INDEX IF NOT EXISTS idx_commission_allocation_attribution ON oa_commission_allocation(attribution_id);
CREATE INDEX IF NOT EXISTS idx_commission_batch_tenant ON oa_commission_batch(tenant_id,status);
CREATE INDEX IF NOT EXISTS idx_commission_line_attribution ON oa_commission_line(attribution_id,user_id,side);
CREATE INDEX IF NOT EXISTS idx_commission_line_payroll ON oa_commission_line(payroll_item_id);
