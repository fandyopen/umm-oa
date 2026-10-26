MERGE INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, leader, phone, status)
KEY (dept_id) VALUES
    (104, 1, 100, '0,100', '采购部', 4, '采购负责人', '15609825919', '0');

MERGE INTO sys_user (
    user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber,
    sex, avatar, status, create_by, remark
) KEY (user_id) VALUES
    (2, 1, 100, '17673605591', '总经理',
     '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy',
     '', '17673605591', '0', '', '0', 'system', '总经理演示账号'),
    (3, 1, 102, '15685266238', '财务会计',
     '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy',
     '', '15685266238', '0', '', '0', 'system', '财务会计/财务部经理演示账号'),
    (4, 1, 102, '17689037697', '财务出纳',
     '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy',
     '', '17689037697', '0', '', '0', 'system', '财务出纳/合同归档演示账号'),
    (5, 1, 103, '15646844893', '副总（销售）',
     '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy',
     '', '15646844893', '0', '', '0', 'system', '销售副总演示账号'),
    (6, 1, 103, '13689213154', '副总（生产）',
     '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy',
     '', '13689213154', '0', '', '0', 'system', '生产副总演示账号'),
    (7, 1, 103, '15650389435', '项目总管',
     '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy',
     '', '15650389435', '0', '', '0', 'system', '项目总管演示账号'),
    (8, 1, 104, '15609825919', '采购负责人',
     '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy',
     '', '15609825919', '0', '', '0', 'system', '采购负责人演示账号'),
    (9, 1, 101, '17655872713', '综合部经理',
     '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy',
     '', '17655872713', '0', '', '0', 'system', '综合部经理/盖章确认演示账号');

MERGE INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark)
KEY (role_id) VALUES
    (3, 1, '总经理', 'general_manager', 3, '1', '0', '高管工作台'),
    (4, 1, '财务主管', 'finance_manager', 4, '1', '0', '财务主管工作台'),
    (5, 1, '销售副总', 'sales_vp', 5, '1', '0', '销售主管工作台'),
    (6, 1, '项目经理', 'project_manager', 6, '1', '0', '项目总管工作台');

MERGE INTO sys_user_role (user_id, role_id) KEY (user_id, role_id) VALUES
    (2, 3),
    (3, 4),
    (4, 2),
    (5, 5),
    (6, 2),
    (7, 6),
    (8, 2),
    (9, 2);

MERGE INTO sys_post (post_id, post_code, post_name, post_sort, status) KEY (post_id) VALUES
    (3, 'finance_accountant', '财务会计', 3, '0'),
    (4, 'cashier', '财务出纳', 4, '0'),
    (5, 'sales_vp', '副总（销售）', 5, '0'),
    (6, 'production_vp', '副总（生产）', 6, '0'),
    (7, 'project_manager', '项目总管', 7, '0'),
    (8, 'purchasing_manager', '采购负责人', 8, '0'),
    (9, 'admin_manager', '综合部经理', 9, '0'),
    (10, 'finance_manager', '财务部经理', 10, '0'),
    (11, 'seal_confirm', '盖章确认', 11, '0'),
    (12, 'contract_archive', '合同归档', 12, '0');

MERGE INTO sys_user_post (user_id, post_id) KEY (user_id, post_id) VALUES
    (2, 1),
    (3, 3),
    (3, 10),
    (4, 4),
    (4, 12),
    (5, 5),
    (6, 6),
    (7, 7),
    (8, 8),
    (9, 9),
    (9, 11);

MERGE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, menu_type,
    visible, status, perms, icon
) KEY (menu_id) VALUES
    (15, '高管工作台', 0, 20, '', '', 'F', '1', '0', 'eos:gzt:gg', ''),
    (16, '项目总管工作台', 0, 21, '', '', 'F', '1', '0', 'eos:gzt:xmzg', ''),
    (17, '财务主管工作台', 0, 22, '', '', 'F', '1', '0', 'eos:gzt:cwzg', ''),
    (18, '销售主管工作台', 0, 23, '', '', 'F', '1', '0', 'eos:gzt:xszg', '');

MERGE INTO sys_role_menu (role_id, menu_id)
KEY (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r, sys_menu m
WHERE r.role_id IN (2, 3, 4, 5, 6) AND m.menu_id BETWEEN 1 AND 9;

MERGE INTO sys_role_menu (role_id, menu_id) KEY (role_id, menu_id) VALUES
    (3, 15),
    (4, 17),
    (5, 18),
    (6, 16);
