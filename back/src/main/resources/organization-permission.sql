-- ============================================================================
-- 组织模块授权升级脚本
--
-- 唯一授权来源：
--   sys_role_module         角色可访问模块
--   sys_role_module_action  角色模块动作
--   sys_role.data_scope     数据范围
--
-- 本脚本不再写入 sys_menu / sys_role_menu。
-- ============================================================================

-- 人事管理独立菜单：一级模块 + 成员管理
MERGE INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) KEY (module_id)
VALUES (341000, 1, 0, '人事管理', 'HR', '/hr', 'Layout', 'iconfont-tuandui4', 3, 0, '', 1, '', 0, '', '', '', 'noRedirect', '0');
MERGE INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) KEY (module_id)
VALUES (341001, 1, 341000, '成员管理', 'HrMembers', 'members', 'enterprise/hr/index', 'iconfont-tuandui4', 1, 0, 'hr:member:list', 0, '', 0, '', '', '', '', '0');

-- 人事经理：全部成员和离职成员统一管理
MERGE INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark) KEY (role_id)
VALUES (19, 1, '人事经理', 'hr_manager', 10, '1', '0', '人事管理、组织架构、角色只读');

-- 人事管理(341001) + 组织机构(340782) + 通知公告(340316) + 角色权限(340597)
MERGE INTO sys_role_module (role_id, module_id) KEY (role_id, module_id) VALUES (19, 341001);
MERGE INTO sys_role_module (role_id, module_id) KEY (role_id, module_id) VALUES (19, 340782);
MERGE INTO sys_role_module (role_id, module_id) KEY (role_id, module_id) VALUES (19, 340316);
MERGE INTO sys_role_module (role_id, module_id) KEY (role_id, module_id) VALUES (19, 340597);

-- 综合部经理：组织协同范围为本部门及以下
UPDATE sys_role SET data_scope = '4' WHERE role_id = 12;

-- 盖章确认 / 回收作废只做流程确认
DELETE FROM sys_role_module WHERE role_id IN (13, 14) AND module_id IN (340782, 340597);

-- 兼容早期误用 user_id=10 的版本
UPDATE sys_user
SET user_name = '17694407532', nick_name = '金么算', dept_id = 327,
    email = '17694407532@seals.com', phonenumber = '17694407532', remark = '参考站演示成员'
WHERE user_id = 10 AND user_name = '17600000001';

MERGE INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) KEY (user_id)
VALUES (35, 1, 324, '17600000001', '人事经理',
        '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy',
        '', '17600000001', '0', '', '0', '0', '0', '0', 'system', '人事经理演示账号');

MERGE INTO sys_user_role (user_id, role_id) KEY (user_id, role_id) VALUES (35, 19);

MERGE INTO sys_post (post_id, post_code, post_name, post_sort, status) KEY (post_id)
VALUES (13, 'hr_manager', '人事经理', 13, '0');
MERGE INTO sys_user_post (user_id, post_id) KEY (user_id, post_id) VALUES (35, 13);
