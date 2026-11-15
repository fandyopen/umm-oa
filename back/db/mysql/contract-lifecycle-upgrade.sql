-- Contract retirement upgrade; preserves existing business data.
SET @contract_sql = (SELECT IF(COUNT(*)=0,'ALTER TABLE oa_crm_contract ADD COLUMN deleted INT NOT NULL DEFAULT 0','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='oa_crm_contract' AND column_name='deleted');
PREPARE contract_stmt FROM @contract_sql;
EXECUTE contract_stmt;
DEALLOCATE PREPARE contract_stmt;
SET @contract_sql = (SELECT IF(COUNT(*)=0,'ALTER TABLE oa_crm_contract ADD COLUMN lifecycle_request_id BIGINT','SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='oa_crm_contract' AND column_name='lifecycle_request_id');
PREPARE contract_stmt FROM @contract_sql;
EXECUTE contract_stmt;
DEALLOCATE PREPARE contract_stmt;

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
