INSERT INTO oa_merchant (
    id, tenant_id, serial, name, type, address, tax_number, taxpayer_type,
    industry, company_scale, region, enterprise_nature, customer_source,
    cooperation_status, owner_user_id, owner_user_name, public_pool, assigned_at,
    last_followup_time, contract_expire_date, last_payment_date,
    cooperation_positions, referral_count, hire_count, contract_amount, received_amount,
    remark, status, create_by
) VALUES (
    1, 1, 'KS-DEMO-001', '示例客户', '客户', '上海市浦东新区示例路 100 号',
    '91310000DEMO000001', '一般纳税人',
    '软件与信息服务', '101-500人', '华东', '民营企业', '客户转介绍',
    '合作中', 1, '方铭渴', '0', DATE_ADD(CURRENT_TIMESTAMP, INTERVAL -60 DAY),
    DATE_ADD(CURRENT_TIMESTAMP, INTERVAL -12 DAY), DATE_ADD(CURRENT_DATE, INTERVAL 120 DAY),
    DATE_ADD(CURRENT_DATE, INTERVAL -20 DAY), 4, 18, 9, 360000.00, 180000.00,
    '用于客户档案、联系人、跟进、商机和经营分析演示', '0', 'system'
)
ON DUPLICATE KEY UPDATE name = VALUES(name), serial = VALUES(serial),
    type = VALUES(type), address = VALUES(address), tax_number = VALUES(tax_number),
    taxpayer_type = VALUES(taxpayer_type), industry = VALUES(industry),
    company_scale = VALUES(company_scale), region = VALUES(region),
    enterprise_nature = VALUES(enterprise_nature),
    customer_source = VALUES(customer_source),
    cooperation_status = VALUES(cooperation_status),
    owner_user_id = VALUES(owner_user_id), owner_user_name = VALUES(owner_user_name),
    public_pool = VALUES(public_pool), assigned_at = VALUES(assigned_at),
    last_followup_time = VALUES(last_followup_time),
    contract_expire_date = VALUES(contract_expire_date),
    last_payment_date = VALUES(last_payment_date),
    cooperation_positions = VALUES(cooperation_positions),
    referral_count = VALUES(referral_count), hire_count = VALUES(hire_count),
    contract_amount = VALUES(contract_amount),
    received_amount = VALUES(received_amount), remark = VALUES(remark);

INSERT INTO oa_merchant_contact (
    id, merchant_id, name, phone, wechat, email, position,
    decision_role, primary_contact, remark
) VALUES (
    1, 1, '张经理', '13800000000', 'zhang-manager', 'contact@example.com',
    '人力资源总监', '最终决策', '1', '负责年度招聘服务决策'
)
ON DUPLICATE KEY UPDATE name = VALUES(name), phone = VALUES(phone),
    wechat = VALUES(wechat), email = VALUES(email), position = VALUES(position),
    decision_role = VALUES(decision_role),
    primary_contact = VALUES(primary_contact), remark = VALUES(remark);

INSERT INTO oa_merchant_contact (
    id, merchant_id, name, phone, wechat, email, position,
    decision_role, primary_contact, remark
) VALUES (
    2, 1, '李主管', '13800000003', 'li-supervisor', 'li@example.com',
    '招聘主管', '业务执行', '0', '负责岗位需求和面试协调'
)
ON DUPLICATE KEY UPDATE name = VALUES(name), phone = VALUES(phone),
    wechat = VALUES(wechat), email = VALUES(email), position = VALUES(position),
    decision_role = VALUES(decision_role),
    primary_contact = VALUES(primary_contact), remark = VALUES(remark);

INSERT INTO oa_merchant_collaborator (
    id, merchant_id, user_id, user_name
) VALUES (
    1, 1, 9, '苏记勇'
)
ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), user_name = VALUES(user_name);

INSERT INTO oa_customer_followup (
    id, tenant_id, merchant_id, followup_time, method, content, next_plan,
    next_followup_time, owner_user_id, owner_user_name, create_by
) VALUES (
    1, 1, 1, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL -12 DAY), '会议',
    '复盘本年度招聘交付情况，确认下一年度技术岗位需求。',
    '整理年度招聘服务方案并安排下次沟通。',
    DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 5 DAY), 1, '方铭渴', '17630135660'
)
ON DUPLICATE KEY UPDATE method = VALUES(method), content = VALUES(content),
    next_plan = VALUES(next_plan), next_followup_time = VALUES(next_followup_time),
    owner_user_id = VALUES(owner_user_id), owner_user_name = VALUES(owner_user_name);

INSERT INTO oa_merchant_account (
    id, merchant_id, account, account_name, bank, remark
) VALUES (
    1, 1, '6222000000000001', '示例客户有限公司', '示例银行上海分行', ''
)
ON DUPLICATE KEY UPDATE account = VALUES(account), account_name = VALUES(account_name),
    bank = VALUES(bank), remark = VALUES(remark);
