INSERT INTO sys_tenant (tenant_id, tenant_name, contact_name, contact_phone, status)
VALUES (1, '示例企业', '系统管理员', '17630135660', '0');

INSERT INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, leader, phone, status)
VALUES (100, 1, 0, '0', '示例企业', 0, '系统管理员', '17630135660', '0');
INSERT INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, leader, phone, status)
VALUES (101, 1, 100, '0,100', '综合管理部', 1, '系统管理员', '17630135660', '0');
INSERT INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, leader, phone, status)
VALUES (102, 1, 100, '0,100', '财务部', 2, '财务负责人', '15685266238', '0');
INSERT INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, leader, phone, status)
VALUES (103, 1, 100, '0,100', '项目管理部', 3, '项目负责人', '15650389435', '0');
INSERT INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, leader, phone, status)
VALUES (104, 1, 100, '0,100', '采购部', 4, '采购负责人', '15609825919', '0');

INSERT INTO sys_user (
    user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber,
    sex, avatar, status, create_by, remark
) VALUES (
    1, 1, 101, '17630135660', '系统管理员',
    '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy',
    'admin@example.com', '17630135660', '0', '', '0', 'system', '本地演示管理员'
);
INSERT INTO sys_user (
    user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber,
    sex, avatar, status, create_by, remark
) VALUES
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

INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark)
VALUES (1, 1, '超级管理员', 'admin', 1, '1', '0', '拥有全部权限');
INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark)
VALUES (2, 1, '普通员工', 'employee', 2, '5', '0', '本人数据');
INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark)
VALUES (3, 1, '总经理', 'general_manager', 3, '1', '0', '高管工作台');
INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark)
VALUES (4, 1, '财务主管', 'finance_manager', 4, '1', '0', '财务主管工作台');
INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark)
VALUES (5, 1, '销售副总', 'sales_vp', 5, '1', '0', '销售主管工作台');
INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark)
VALUES (6, 1, '项目经理', 'project_manager', 6, '1', '0', '项目总管工作台');

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);
INSERT INTO sys_user_role (user_id, role_id) VALUES
    (2, 3),
    (3, 4),
    (4, 2),
    (5, 5),
    (6, 2),
    (7, 6),
    (8, 2),
    (9, 2);

INSERT INTO sys_post (post_id, post_code, post_name, post_sort, status)
VALUES (1, 'ceo', '总经理', 1, '0');
INSERT INTO sys_post (post_id, post_code, post_name, post_sort, status)
VALUES (2, 'ptyg', '普通员工', 2, '0');
INSERT INTO sys_post (post_id, post_code, post_name, post_sort, status) VALUES
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
INSERT INTO sys_user_post (user_id, post_id) VALUES (1, 1);
INSERT INTO sys_user_post (user_id, post_id) VALUES
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

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon) VALUES
(1, '工作台', 0, 1, '/todo', 'eos/todoItem/todo/index', 'C', '0', '0', 'project:todo:list', 'iconfont-shouye'),
(2, '项目管理', 0, 2, '/project', 'eos/project/index', 'C', '0', '0', 'project:project:list', 'iconfont-xiangmu'),
(3, '合同管理', 0, 3, '/contract', 'eos/contract/index', 'C', '0', '0', 'project:contract:list', 'iconfont-hetong'),
(4, '采购管理', 0, 4, '/purchase', 'eos/purchase/apply/index', 'C', '0', '0', 'project:purchase:list', 'iconfont-caigou'),
(5, '费用报销', 0, 5, '/expense', 'eos/expense/index', 'C', '0', '0', 'project:expense:list', 'iconfont-baoxiao'),
(6, '预算管理', 0, 6, '/budget', 'eos/budget/index', 'C', '0', '0', 'project:budget:list', 'iconfont-yusuan'),
(7, '资金计划', 0, 7, '/fund-plan', 'eos/fundPlan/index', 'C', '0', '0', 'project:fund:list', 'iconfont-zijin'),
(8, '发票管理', 0, 8, '/invoice', 'eos/invoice/make/index', 'C', '0', '0', 'project:invoice:list', 'iconfont-fapiao'),
(9, '仓库管理', 0, 9, '/warehouse', 'eos/warehouse/warehouse/index', 'C', '0', '0', 'project:warehouse:list', 'iconfont-cangku'),
(10, '系统管理', 0, 10, '/system', 'system/user/index', 'M', '0', '0', NULL, 'iconfont-shezhi'),
(11, '用户管理', 10, 1, 'user', 'system/user/index', 'C', '0', '0', 'system:user:list', 'iconfont-user'),
(12, '角色管理', 10, 2, 'role', 'system/role/index', 'C', '0', '0', 'system:role:list', 'iconfont-role'),
    (13, '部门管理', 10, 3, 'dept', 'system/dept/index', 'C', '0', '0', 'system:dept:list', 'iconfont-dept'),
    (14, '菜单管理', 10, 4, 'menu', 'system/menu/index', 'C', '0', '0', 'system:menu:list', 'iconfont-menu'),
    (15, '高管工作台', 0, 20, '', '', 'F', '1', '0', 'eos:gzt:gg', ''),
    (16, '项目总管工作台', 0, 21, '', '', 'F', '1', '0', 'eos:gzt:xmzg', ''),
    (17, '财务主管工作台', 0, 22, '', '', 'F', '1', '0', 'eos:gzt:cwzg', ''),
    (18, '销售主管工作台', 0, 23, '', '', 'F', '1', '0', 'eos:gzt:xszg', '');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu;
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r, sys_menu m
WHERE r.role_id IN (2, 3, 4, 5, 6) AND m.menu_id BETWEEN 1 AND 9;
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
    (3, 15),
    (4, 17),
    (5, 18),
    (6, 16);

INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status) VALUES
(1, '项目类型', 'project_type', '0'),
(2, '项目状态', 'project_status', '0'),
(3, '合同状态', 'contract_status', '0'),
(4, '报销状态', 'expense_status', '0'),
(5, '审批状态', 'process_status', '0'),
(6, '费用类型', 'expense_type', '0');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES
(1, '施工项目', '1', 'project_type', NULL, 'default', 'Y', '0'),
(2, '服务项目', '2', 'project_type', NULL, 'success', 'N', '0'),
(3, '投资项目', '3', 'project_type', NULL, 'warning', 'N', '0'),
(1, '在建', '在建', 'project_status', NULL, 'processing', 'Y', '0'),
(2, '已完成', '已完成', 'project_status', NULL, 'success', 'N', '0'),
(3, '已暂停', '已暂停', 'project_status', NULL, 'default', 'N', '0'),
(1, '草稿', 'draft', 'process_status', NULL, 'default', 'Y', '0'),
(2, '审批中', 'running', 'process_status', NULL, 'processing', 'N', '0'),
(3, '已通过', 'approved', 'process_status', NULL, 'success', 'N', '0'),
(4, '已驳回', 'rejected', 'process_status', NULL, 'error', 'N', '0'),
(1, '差旅费', 'travel', 'expense_type', NULL, 'default', 'Y', '0'),
(2, '办公费', 'office', 'expense_type', NULL, 'default', 'N', '0'),
(3, '招待费', 'entertainment', 'expense_type', NULL, 'default', 'N', '0');

INSERT INTO sys_config (config_name, config_key, config_value, config_type, remark) VALUES
('登录验证码开关', 'sys.account.captchaEnabled', 'false', 'Y', '本地开发默认关闭验证码校验'),
('系统名称', 'sys.app.name', '啓思 OA', 'Y', '页面标题'),
('用户初始密码', 'sys.user.initPassword', 'admin123123', 'Y', '仅用于本地演示');

INSERT INTO sys_notice (notice_title, notice_type, notice_content, status, create_by) VALUES
('系统初始化完成', '1', '本地 Spring Boot 后端已启动，默认管理员账号为 17630135660。', '0', 'system');

INSERT INTO sys_app (tenant_app_id, tenant_id, app_name, app_icon, app_path, app_type, status) VALUES
(1, 1, 'OA 办公', 'iconfont-bangong', '/index', 'OA', '0'),
(2, 1, 'CRM 客户', 'iconfont-kehu', '/crm', 'CRM', '0');

INSERT INTO sys_app_module (tenant_app_id, module_name, module_path, component, icon, order_num, status) VALUES
(1, '项目管理', '/project/index', 'eos/project/index', 'iconfont-xiangmu', 1, '0'),
(1, '合同管理', '/contract/index', 'eos/contract/index', 'iconfont-hetong', 2, '0'),
(1, '费用报销', '/expense/index', 'eos/expense/indexOp', 'iconfont-baoxiao', 3, '0'),
(1, '采购申请', '/purchase/index', 'eos/purchase/apply/indexOp', 'iconfont-caigou', 4, '0');

INSERT INTO sys_admin_group (group_id, group_name, tenant_id) VALUES (1, '系统管理员组', 1);
INSERT INTO sys_admin_group_member (group_id, user_id) VALUES (1, 1);

INSERT INTO oa_project (
    id, tenant_id, code, name, nick_name, dept_id, type, owner_unit, sketch, status, process_status, create_by
) VALUES (
    1, 1, 'QS-DEMO-001', '企业数字化办公平台', '系统管理员', 103, '1',
    '示例客户', '用于验证项目管理、合同、采购和报销流程的演示项目。', '在建', 'approved', '17630135660'
);

INSERT INTO oa_project_member (project_id, user_id, user_name, nick_name, role_name, phone)
VALUES (1, 1, '17630135660', '系统管理员', '项目经理', '17630135660');

INSERT INTO oa_contract (
    id, tenant_id, project_id, code, name, type, party_a, party_b, money, tax_rate,
    start_date, end_date, efficacy, process_status, create_by
) VALUES (
    1, 1, 1, 'HT-DEMO-001', '办公平台实施合同', 'service', '示例客户', '示例供应商',
    128000.00, 6.0000, CURRENT_DATE, DATEADD('DAY', 365, CURRENT_DATE), '有效', 'approved', '17630135660'
);

INSERT INTO oa_contract_item (contract_id, sn, name, content, money, percent)
VALUES (1, 1, '一期实施款', '完成基础模块上线', 76800.00, 60.0000);
INSERT INTO oa_contract_item (contract_id, sn, name, content, money, percent)
VALUES (1, 2, '验收尾款', '验收通过后支付', 51200.00, 40.0000);

INSERT INTO oa_purchase_apply (
    id, tenant_id, project_id, code, apply_user_id, apply_user_name, dept_id,
    purpose, total_amount, process_status
) VALUES (1, 1, 1, 'CG-DEMO-001', 1, '系统管理员', 103, '采购办公设备', 6800.00, 'approved');

INSERT INTO oa_purchase_apply_item (apply_id, name, spec, unit, amount, price, total_price, description)
VALUES (1, '笔记本电脑', '14 英寸/32G/1T', '台', 2, 3400.00, 6800.00, '研发办公使用');

INSERT INTO oa_expense (
    id, tenant_id, project_id, code, apply_user_id, apply_user_name, dept_id,
    expense_type, total_amount, process_status, remark
) VALUES (1, 1, 1, 'BX-DEMO-001', 1, '系统管理员', 103, 'travel', 1260.00, 'running', '项目现场差旅');

INSERT INTO oa_expense_item (expense_id, budget_id, detail, sum, replaced, remark)
VALUES (1, NULL, '高铁及住宿', 1260.00, 'N', '项目现场支持');

INSERT INTO oa_budget (id, tenant_id, project_id, code, name, budget_year, total_amount, used_amount, status)
VALUES (1, 1, 1, 'YS-DEMO-001', '数字化平台年度预算', 2026, 500000.00, 1260.00, 'approved');

INSERT INTO oa_budget_item (budget_id, subject_name, subject_code, amount, used_amount, remark)
VALUES (1, '差旅费', 'TRAVEL', 50000.00, 1260.00, '项目现场支持');
INSERT INTO oa_budget_item (budget_id, subject_name, subject_code, amount, used_amount, remark)
VALUES (1, '设备采购', 'DEVICE', 180000.00, 6800.00, '研发设备');

INSERT INTO oa_warehouse (id, tenant_id, code, name, address, manager_id, manager_name, status)
VALUES (1, 1, 'CK-DEMO-001', '项目现场仓', '项目现场 A 区', 1, '系统管理员', '0');

INSERT INTO oa_business_opportunity (
    id, tenant_id, name, customer_name, owner_user_id, owner_user_name,
    stage, expected_amount, expected_close_date, status
) VALUES (
    1, 1, '企业协同办公升级', '示例客户', 1, '系统管理员',
    '需求确认', 260000.00, DATEADD('DAY', 60, CURRENT_DATE), 'running'
);

INSERT INTO oa_todo (
    id, tenant_id, user_id, title, business_type, business_id, status
) VALUES (
    1, 1, 1, '审批：办公平台实施合同', 'contract', 1, 'todo'
);

INSERT INTO oa_process_definition (
    id, tenant_id, process_key, process_name, category, version, definition_json, status
) VALUES (
    1, 1, 'contract_approval', '合同审批流程', 'contract', 1,
    '{"nodes":["start","manager","finance","end"]}', 'published'
);
