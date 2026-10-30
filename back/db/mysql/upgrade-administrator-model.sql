-- MySQL 8：一次性收敛管理员模型。
-- 固定唯一超级管理员为欧志锋（13610316814），其他账号保留原角色和业务数据。

START TRANSACTION;

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
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1);

DELETE FROM sys_admin_identity;
INSERT INTO sys_admin_identity
    (user_id, user_name, nick_name, email, phonenumber, sex, status, sort)
VALUES
    (1, '13610316814', '欧志锋', '13610316814@seals.com', '13610316814', '0', '0', 0);

COMMIT;
