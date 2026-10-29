MERGE INTO oa_merchant (
    id, tenant_id, serial, name, type, address, tax_number, taxpayer_type,
    industry, company_scale, region, enterprise_nature, customer_source,
    cooperation_status, owner_user_id, owner_user_name, public_pool, assigned_at,
    last_followup_time, contract_expire_date, last_payment_date,
    cooperation_positions, referral_count, hire_count, contract_amount, received_amount,
    remark, status, create_by
) KEY (id) VALUES (
    1, 1, 'KS-DEMO-001', '示例客户', '客户', '上海市浦东新区示例路 100 号',
    '91310000DEMO000001', '一般纳税人',
    '软件与信息服务', '101-500人', '华东', '民营企业', '客户转介绍',
    '合作中', 1, '方铭渴', '0', DATEADD('DAY', -60, CURRENT_TIMESTAMP),
    DATEADD('DAY', -12, CURRENT_TIMESTAMP), DATEADD('DAY', 120, CURRENT_DATE),
    DATEADD('DAY', -20, CURRENT_DATE), 4, 18, 9, 360000.00, 180000.00,
    '用于客户档案、联系人、跟进、商机和经营分析演示', '0', 'system'
);

MERGE INTO oa_merchant_contact (
    id, merchant_id, name, phone, wechat, email, position,
    decision_role, primary_contact, remark
) KEY (id) VALUES (
    1, 1, '张经理', '13800000000', 'zhang-manager', 'contact@example.com',
    '人力资源总监', '最终决策', '1', '负责年度招聘服务决策'
);

MERGE INTO oa_merchant_contact (
    id, merchant_id, name, phone, wechat, email, position,
    decision_role, primary_contact, remark
) KEY (id) VALUES (
    2, 1, '李主管', '13800000003', 'li-supervisor', 'li@example.com',
    '招聘主管', '业务执行', '0', '负责岗位需求和面试协调'
);

MERGE INTO oa_merchant_collaborator (
    id, merchant_id, user_id, user_name
) KEY (id) VALUES (
    1, 1, 9, '苏记勇'
);

MERGE INTO oa_customer_followup (
    id, tenant_id, merchant_id, followup_time, method, content, next_plan,
    next_followup_time, owner_user_id, owner_user_name, create_by
) KEY (id) VALUES (
    1, 1, 1, DATEADD('DAY', -12, CURRENT_TIMESTAMP), '会议',
    '复盘本年度招聘交付情况，确认下一年度技术岗位需求。',
    '整理年度招聘服务方案并安排下次沟通。',
    DATEADD('DAY', 5, CURRENT_TIMESTAMP), 1, '方铭渴', '17630135660'
);

MERGE INTO oa_merchant_account (
    id, merchant_id, account, account_name, bank, remark
) KEY (id) VALUES (
    1, 1, '6222000000000001', '示例客户有限公司', '示例银行上海分行', ''
);
