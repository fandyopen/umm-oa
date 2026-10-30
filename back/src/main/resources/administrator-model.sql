-- 固定唯一超级管理员，并清理历史数据中误标记的管理员字段。
-- 本脚本可重复执行：超级管理员固定为 user_id=1 / 13610316814。

UPDATE sys_user SET is_admin = '0', is_owner = '0';

UPDATE sys_user
SET user_name = '13610316814',
    nick_name = '欧志锋',
    email = '13610316814@seals.com',
    phonenumber = '13610316814',
    status = '0',
    del_flag = '0',
    is_admin = '1',
    is_owner = '0',
    remark = '固定超级管理员'
WHERE user_id = 1;

UPDATE sys_tenant
SET contact_name = '欧志锋', contact_phone = '13610316814'
WHERE tenant_id = 1;

UPDATE sys_role
SET role_name = '超级管理员', role_key = 'admin', status = '0', del_flag = '0',
    data_scope = '1', remark = '固定超级管理员，拥有全部权限'
WHERE role_id = 1;

DELETE FROM sys_user_role WHERE role_id = 1 AND user_id <> 1;
MERGE INTO sys_user_role (user_id, role_id) KEY (user_id, role_id) VALUES (1, 1);

DELETE FROM sys_admin_identity;
MERGE INTO sys_admin_identity
    (user_id, user_name, nick_name, email, phonenumber, sex, status, sort)
KEY (user_id)
VALUES (1, '13610316814', '欧志锋', '13610316814@seals.com', '13610316814', '0', '0', 0);
