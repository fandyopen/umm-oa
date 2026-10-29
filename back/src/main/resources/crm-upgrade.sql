ALTER TABLE oa_merchant ADD COLUMN IF NOT EXISTS industry VARCHAR(100);
ALTER TABLE oa_merchant ADD COLUMN IF NOT EXISTS company_scale VARCHAR(64);
ALTER TABLE oa_merchant ADD COLUMN IF NOT EXISTS region VARCHAR(100);
ALTER TABLE oa_merchant ADD COLUMN IF NOT EXISTS enterprise_nature VARCHAR(64);
ALTER TABLE oa_merchant ADD COLUMN IF NOT EXISTS customer_source VARCHAR(64);
ALTER TABLE oa_merchant ADD COLUMN IF NOT EXISTS cooperation_status VARCHAR(64);
ALTER TABLE oa_merchant ADD COLUMN IF NOT EXISTS owner_user_id BIGINT;
ALTER TABLE oa_merchant ADD COLUMN IF NOT EXISTS owner_user_name VARCHAR(64);
ALTER TABLE oa_merchant ADD COLUMN IF NOT EXISTS public_pool VARCHAR(1) NOT NULL DEFAULT '0';
ALTER TABLE oa_merchant ADD COLUMN IF NOT EXISTS assigned_at DATETIME;
ALTER TABLE oa_merchant ADD COLUMN IF NOT EXISTS last_followup_time DATETIME;
ALTER TABLE oa_merchant ADD COLUMN IF NOT EXISTS contract_expire_date DATE;
ALTER TABLE oa_merchant ADD COLUMN IF NOT EXISTS last_payment_date DATE;
ALTER TABLE oa_merchant ADD COLUMN IF NOT EXISTS cooperation_positions INT NOT NULL DEFAULT 0;
ALTER TABLE oa_merchant ADD COLUMN IF NOT EXISTS referral_count INT NOT NULL DEFAULT 0;
ALTER TABLE oa_merchant ADD COLUMN IF NOT EXISTS hire_count INT NOT NULL DEFAULT 0;
ALTER TABLE oa_merchant ADD COLUMN IF NOT EXISTS contract_amount DECIMAL(18, 2) NOT NULL DEFAULT 0;
ALTER TABLE oa_merchant ADD COLUMN IF NOT EXISTS received_amount DECIMAL(18, 2) NOT NULL DEFAULT 0;

ALTER TABLE oa_merchant_contact ADD COLUMN IF NOT EXISTS wechat VARCHAR(64);
ALTER TABLE oa_merchant_contact ADD COLUMN IF NOT EXISTS decision_role VARCHAR(64);
ALTER TABLE oa_merchant_contact ADD COLUMN IF NOT EXISTS primary_contact VARCHAR(1) NOT NULL DEFAULT '0';

ALTER TABLE oa_business_opportunity ADD COLUMN IF NOT EXISTS potential_cooperation VARCHAR(500);
ALTER TABLE oa_business_opportunity ADD COLUMN IF NOT EXISTS requirement TEXT;
ALTER TABLE oa_business_opportunity ADD COLUMN IF NOT EXISTS cooperation_intent VARCHAR(64);
ALTER TABLE oa_business_opportunity ADD COLUMN IF NOT EXISTS contract_amount DECIMAL(18, 2) NOT NULL DEFAULT 0;
ALTER TABLE oa_business_opportunity ADD COLUMN IF NOT EXISTS received_amount DECIMAL(18, 2) NOT NULL DEFAULT 0;
ALTER TABLE oa_business_opportunity ADD COLUMN IF NOT EXISTS last_followup_time DATETIME;

CREATE TABLE IF NOT EXISTS oa_merchant_collaborator (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(64),
    UNIQUE (merchant_id, user_id)
);

CREATE TABLE IF NOT EXISTS oa_customer_followup (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    merchant_id BIGINT NOT NULL,
    followup_time DATETIME NOT NULL,
    method VARCHAR(32),
    content TEXT,
    next_plan TEXT,
    next_followup_time DATETIME,
    owner_user_id BIGINT,
    owner_user_name VARCHAR(64),
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

UPDATE oa_merchant
SET owner_user_id = COALESCE(owner_user_id, 1),
    owner_user_name = COALESCE(owner_user_name, '方铭渴'),
    cooperation_status = COALESCE(NULLIF(cooperation_status, ''), '潜在客户'),
    customer_source = COALESCE(NULLIF(customer_source, ''), '组织分配')
WHERE owner_user_id IS NULL;

UPDATE oa_merchant
SET industry = COALESCE(NULLIF(industry, ''), '软件与信息服务'),
    company_scale = COALESCE(NULLIF(company_scale, ''), '101-500人'),
    region = COALESCE(NULLIF(region, ''), '华东'),
    enterprise_nature = COALESCE(NULLIF(enterprise_nature, ''), '民营企业'),
    customer_source = COALESCE(NULLIF(customer_source, ''), '客户转介绍'),
    cooperation_status = '合作中',
    owner_user_id = COALESCE(owner_user_id, 1),
    owner_user_name = COALESCE(NULLIF(owner_user_name, ''), '方铭渴'),
    public_pool = COALESCE(public_pool, '0'),
    assigned_at = COALESCE(assigned_at, DATEADD('DAY', -60, CURRENT_TIMESTAMP)),
    last_followup_time = COALESCE(last_followup_time, DATEADD('DAY', -12, CURRENT_TIMESTAMP)),
    contract_expire_date = COALESCE(contract_expire_date, DATEADD('DAY', 120, CURRENT_DATE)),
    last_payment_date = COALESCE(last_payment_date, DATEADD('DAY', -20, CURRENT_DATE)),
    cooperation_positions = CASE WHEN cooperation_positions = 0 THEN 4 ELSE cooperation_positions END,
    referral_count = CASE WHEN referral_count = 0 THEN 18 ELSE referral_count END,
    hire_count = CASE WHEN hire_count = 0 THEN 9 ELSE hire_count END,
    contract_amount = CASE WHEN contract_amount = 0 THEN 360000.00 ELSE contract_amount END,
    received_amount = CASE WHEN received_amount = 0 THEN 180000.00 ELSE received_amount END
WHERE id = 1;

UPDATE oa_merchant_contact
SET wechat = COALESCE(NULLIF(wechat, ''), 'zhang-manager'),
    decision_role = COALESCE(NULLIF(decision_role, ''), '最终决策'),
    primary_contact = COALESCE(primary_contact, '1')
WHERE merchant_id = 1 AND name = '张经理';

INSERT INTO oa_merchant_contact (
    merchant_id, name, phone, wechat, email, position,
    decision_role, primary_contact, remark
)
SELECT 1, '李主管', '13800000003', 'li-supervisor', 'li@example.com',
       '招聘主管', '业务执行', '0', '负责岗位需求和面试协调'
WHERE NOT EXISTS (
    SELECT 1 FROM oa_merchant_contact
    WHERE merchant_id = 1 AND name = '李主管'
);

INSERT INTO oa_merchant_collaborator (merchant_id, user_id, user_name)
SELECT 1, 9, '苏记勇'
WHERE NOT EXISTS (
    SELECT 1 FROM oa_merchant_collaborator
    WHERE merchant_id = 1 AND user_id = 9
);

INSERT INTO oa_customer_followup (
    tenant_id, merchant_id, followup_time, method, content, next_plan,
    next_followup_time, owner_user_id, owner_user_name, create_by
)
SELECT 1, 1, DATEADD('DAY', -12, CURRENT_TIMESTAMP), '会议',
       '复盘本年度招聘交付情况，确认下一年度技术岗位需求。',
       '整理年度招聘服务方案并安排下次沟通。',
       DATEADD('DAY', 5, CURRENT_TIMESTAMP), 1, '方铭渴', '17630135660'
WHERE NOT EXISTS (
    SELECT 1 FROM oa_customer_followup
    WHERE merchant_id = 1 AND content = '复盘本年度招聘交付情况，确认下一年度技术岗位需求。'
);

UPDATE biz_record
SET payload_json = REPLACE(
    REPLACE(
        payload_json,
        '{"id":',
        '{"customerId":1,"customerName":"示例客户","stage":"初步接洽","businessState":"初步接洽","potentialCooperation":"年度招聘与岗位外包服务","requirement":"客户需要补充技术岗位并优化招聘交付流程","cooperationIntent":"中","statusLabel":"推进中","ownerUserId":1,"ownerUserName":"方铭渴","id":'
    ),
    '"ownerUnit":"XX有限公司"',
    '"customerName":"示例客户"'
)
WHERE id = 5000 AND payload_json NOT LIKE '%"customerId"%';

UPDATE biz_record
SET payload_json = REPLACE(
    REPLACE(
        payload_json,
        '{"id":',
        '{"customerId":1,"customerName":"示例客户","stage":"赢单","businessState":"赢单","potentialCooperation":"智慧升级改造项目人力服务","requirement":"客户需要项目制技术团队与驻场交付","cooperationIntent":"高","statusLabel":"已赢单","ownerUserId":1,"ownerUserName":"方铭渴","id":'
    ),
    '"businessState":"转化项目"',
    '"businessState":"赢单"'
)
WHERE id = 5001 AND payload_json NOT LIKE '%"customerId"%';

UPDATE biz_record
SET payload_json = REPLACE(
    REPLACE(
        payload_json,
        '{"id":',
        '{"customerId":1,"customerName":"示例客户","stage":"方案报价","businessState":"方案报价","potentialCooperation":"教育基地建设项目人员配置","requirement":"客户需要项目管理人员和专业技术岗位","cooperationIntent":"中","statusLabel":"推进中","ownerUserId":1,"ownerUserName":"方铭渴","id":'
    ),
    '"businessState":"方案"',
    '"businessState":"方案报价"'
)
WHERE id = 5002 AND payload_json NOT LIKE '%"customerId"%';
