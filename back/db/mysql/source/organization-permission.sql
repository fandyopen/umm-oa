-- ============================================================================
-- 组织模块授权升级脚本（MySQL 8）
--
-- 唯一授权来源：
--   sys_role_module
--   sys_role_module_action
--   sys_role.data_scope
--
-- 本脚本不再写入 sys_menu / sys_role_menu。
-- ============================================================================

INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status)
VALUES
(341000, 1, 0, '人事管理', 'HR', '/hr', 'Layout', 'iconfont-tuandui4', 3, 0, '', 1, '', 0, '', '', '', 'noRedirect', '0'),
(341001, 1, 341000, '成员管理', 'HrMembers', 'members', 'enterprise/hr/index', 'iconfont-tuandui4', 1, 0, 'hr:member:list', 0, '', 0, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    module_name = VALUES(module_name),
    route_name = VALUES(route_name),
    path = VALUES(path),
    component = VALUES(component),
    icon = VALUES(icon),
    module_sort = VALUES(module_sort),
    hidden = VALUES(hidden),
    perms = VALUES(perms),
    has_flow = VALUES(has_flow),
    flow_conditions = VALUES(flow_conditions),
    no_cache = VALUES(no_cache),
    query = VALUES(query),
    redirect = VALUES(redirect),
    status = VALUES(status);

INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark)
VALUES (19, 1, '人事经理', 'hr_manager', 10, '1', '0', '人事管理、组织架构、角色只读')
ON DUPLICATE KEY UPDATE
    role_name = '人事经理',
    role_key = 'hr_manager',
    data_scope = '1',
    status = '0',
    remark = '人事管理、组织架构、角色只读';

INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES
(19, 341001),
(19, 340782),
(19, 340316),
(19, 340597);

UPDATE sys_role SET data_scope = '4' WHERE role_id = 12;

DELETE FROM sys_role_module WHERE role_id IN (13, 14) AND module_id IN (340782, 340597);

UPDATE sys_user
SET user_name = '17694407532', nick_name = '金么算', dept_id = 327,
    email = '17694407532@seals.com', phonenumber = '17694407532', remark = '参考站演示成员'
WHERE user_id = 10 AND user_name = '17600000001';

INSERT INTO sys_user
    (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark)
VALUES
(35, 1, 324, '17600000001', '人事经理',
 '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy',
 '', '17600000001', '0', '', '0', '0', '0', '0', 'system', '人事经理演示账号')
ON DUPLICATE KEY UPDATE nick_name = '人事经理', dept_id = 324;

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (35, 19);

INSERT IGNORE INTO sys_post (post_id, post_code, post_name, post_sort, status)
VALUES (13, 'hr_manager', '人事经理', 13, '0');
INSERT IGNORE INTO sys_user_post (user_id, post_id) VALUES (35, 13);
