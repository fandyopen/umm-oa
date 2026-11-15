ALTER TABLE oa_crm_contract ADD COLUMN IF NOT EXISTS deleted INT NOT NULL DEFAULT 0;
ALTER TABLE oa_crm_contract ADD COLUMN IF NOT EXISTS lifecycle_request_id BIGINT;

CREATE TABLE IF NOT EXISTS oa_crm_contract_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    payload_json TEXT,
    operator_name VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
