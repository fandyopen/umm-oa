ALTER TABLE oa_loan ADD COLUMN IF NOT EXISTS loan_type VARCHAR(64);
ALTER TABLE oa_loan ADD COLUMN IF NOT EXISTS attachment TEXT;

CREATE TABLE IF NOT EXISTS oa_loan_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    loan_id BIGINT NOT NULL,
    loan_name VARCHAR(200) NOT NULL,
    sum DECIMAL(18, 2) NOT NULL DEFAULT 0,
    remark VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS oa_repayment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    loan_id BIGINT NOT NULL,
    user_id BIGINT,
    amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    repay_date DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    remark VARCHAR(500),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

MERGE INTO oa_loan (
    id, tenant_id, code, apply_user_id, apply_user_name, project_id, loan_type,
    amount, loan_date, expected_repay_date, actual_repay_amount, process_status,
    remark
) KEY (id) VALUES (
    1, 1, 'JK-DEMO-001', 1, '系统管理员', 1, 'cash',
    5000.00, CURRENT_DATE, DATEADD('DAY', 30, CURRENT_DATE), 0.00, 'approved',
    '项目备用金'
);

MERGE INTO oa_loan_detail (id, loan_id, loan_name, sum, remark) KEY (id) VALUES
    (1, 1, '现场交通', 1200.00, '项目现场往返'),
    (2, 1, '临时采购', 3800.00, '办公耗材');
