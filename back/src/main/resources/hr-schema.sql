-- Personnel extension: additive and restart-safe; never infer employment dates from account creation.
CREATE TABLE IF NOT EXISTS oa_hr_employee (
    user_id BIGINT PRIMARY KEY,
    employee_no VARCHAR(64) NOT NULL UNIQUE,
    dingtalk_user_id VARCHAR(128) UNIQUE,
    entry_date DATE,
    regular_date DATE,
    exit_date DATE,
    employment_status VARCHAR(32) NOT NULL DEFAULT 'active',
    employment_type VARCHAR(64),
    emergency_contact VARCHAR(100),
    emergency_phone VARCHAR(32),
    contract_start DATE,
    contract_end DATE,
    account_name VARCHAR(100),
    bank_name VARCHAR(200),
    bank_account VARCHAR(100),
    revision INT NOT NULL DEFAULT 0,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO oa_hr_employee (user_id, employee_no, employment_status)
SELECT user_id, CONCAT('EMP', user_id), CASE WHEN del_flag = '2' THEN 'departed' ELSE 'active' END
FROM sys_user u WHERE del_flag IN ('0','2')
AND NOT EXISTS (SELECT 1 FROM oa_hr_employee e WHERE e.user_id = u.user_id);

CREATE TABLE IF NOT EXISTS oa_hr_employment_period (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    entry_date DATE NOT NULL,
    exit_date DATE,
    UNIQUE (user_id, entry_date)
);
INSERT INTO oa_hr_employment_period(user_id,entry_date,exit_date)
SELECT user_id,entry_date,exit_date FROM oa_hr_employee e WHERE e.entry_date IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM oa_hr_employment_period p WHERE p.user_id=e.user_id AND p.entry_date=e.entry_date);

CREATE TABLE IF NOT EXISTS oa_hr_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    revision INT NOT NULL DEFAULT 0,
    workflow_request_id BIGINT,
    effective_date DATE NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    before_json TEXT,
    after_json TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'effective',
    operator_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS oa_hr_salary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    effective_date DATE NOT NULL,
    pay_type VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'draft',
    revision INT NOT NULL DEFAULT 0,
    workflow_request_id BIGINT,
    base_amount DECIMAL(18,2) NOT NULL,
    allowance DECIMAL(18,2) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    operator_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, effective_date)
);
CREATE TABLE IF NOT EXISTS oa_hr_attachment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    payroll_id BIGINT,
    file_name VARCHAR(255) NOT NULL,
    content_base64 MEDIUMTEXT NOT NULL,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS oa_hr_attendance_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    period VARCHAR(7) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'draft',
    file_id BIGINT NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    revision INT NOT NULL DEFAULT 0,
    confirmed_by BIGINT,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (period, kind, source_hash)
);
CREATE TABLE IF NOT EXISTS oa_hr_attendance_row (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    work_date DATE,
    data_json TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS oa_hr_payroll (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    period VARCHAR(7) NOT NULL,
    batch_type VARCHAR(16) NOT NULL,
    regular_key VARCHAR(7) UNIQUE,
    attendance_id BIGINT,
    rules_json TEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'draft',
    revision INT NOT NULL DEFAULT 0,
    workflow_request_id BIGINT,
    published INT NOT NULL DEFAULT 0,
    reason VARCHAR(1000),
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS oa_hr_payroll_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payroll_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    snapshot_json TEXT NOT NULL,
    verified INT NOT NULL DEFAULT 0,
    base_amount DECIMAL(18,2) NOT NULL,
    allowance DECIMAL(18,2) NOT NULL,
    overtime DECIMAL(18,2) NOT NULL DEFAULT 0,
    bonus DECIMAL(18,2) NOT NULL DEFAULT 0,
    commission DECIMAL(18,2) NOT NULL DEFAULT 0,
    adjustment DECIMAL(18,2) NOT NULL DEFAULT 0,
    deduction DECIMAL(18,2) NOT NULL DEFAULT 0,
    tax DECIMAL(18,2) NOT NULL DEFAULT 0,
    contribution DECIMAL(18,2) NOT NULL DEFAULT 0,
    gross_amount DECIMAL(18,2) NOT NULL,
    net_amount DECIMAL(18,2) NOT NULL,
    reason VARCHAR(1000),
    UNIQUE (payroll_id, user_id)
);
CREATE TABLE IF NOT EXISTS oa_hr_payment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id BIGINT NOT NULL,
    request_token VARCHAR(64) NOT NULL UNIQUE,
    amount DECIMAL(18,2) NOT NULL,
    payment_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL,
    reference_no VARCHAR(128) NOT NULL,
    success_reference VARCHAR(128) UNIQUE,
    attachment_id BIGINT,
    reason VARCHAR(1000),
    operator_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS oa_hr_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type VARCHAR(24) NOT NULL,
    entity_id BIGINT NOT NULL,
    user_id BIGINT,
    action VARCHAR(64) NOT NULL,
    detail_json TEXT,
    operator_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS oa_hr_handover (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    business_type VARCHAR(32) NOT NULL,
    business_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'todo',
    target_user_id BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, business_type, business_id)
);
