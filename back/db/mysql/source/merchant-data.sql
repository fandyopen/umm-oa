INSERT INTO oa_merchant (
    id, tenant_id, serial, name, type, address, tax_number, taxpayer_type,
    remark, status, create_by
) VALUES (
    1, 1, 'KS-DEMO-001', '示例客户', '客户', '上海市浦东新区示例路 100 号',
    '91310000DEMO000001', '一般纳税人', '用于项目、合同和付款演示', '0', 'system'
)
ON DUPLICATE KEY UPDATE name = VALUES(name), serial = VALUES(serial),
    type = VALUES(type), address = VALUES(address), tax_number = VALUES(tax_number),
    taxpayer_type = VALUES(taxpayer_type), remark = VALUES(remark);

INSERT INTO oa_merchant_contact (
    id, merchant_id, name, phone, email, position, remark
) VALUES (
    1, 1, '张经理', '13800000000', 'contact@example.com', '项目联系人', ''
)
ON DUPLICATE KEY UPDATE name = VALUES(name), phone = VALUES(phone),
    email = VALUES(email), position = VALUES(position), remark = VALUES(remark);

INSERT INTO oa_merchant_account (
    id, merchant_id, account, account_name, bank, remark
) VALUES (
    1, 1, '6222000000000001', '示例客户有限公司', '示例银行上海分行', ''
)
ON DUPLICATE KEY UPDATE account = VALUES(account), account_name = VALUES(account_name),
    bank = VALUES(bank), remark = VALUES(remark);
