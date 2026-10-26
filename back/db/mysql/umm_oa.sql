-- UMM OA MySQL 8 initialization script
-- Generated from the H2 development schema by tools/build-mysql-sql.mjs.
-- Local demo administrator: 17630135660 / admin123123

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS umm_oa
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
USE umm_oa;

DROP TABLE IF EXISTS biz_record;
DROP TABLE IF EXISTS oa_process_task;
DROP TABLE IF EXISTS oa_process_instance;
DROP TABLE IF EXISTS oa_process_definition;
DROP TABLE IF EXISTS oa_todo;
DROP TABLE IF EXISTS oa_business_opportunity;
DROP TABLE IF EXISTS oa_invoice;
DROP TABLE IF EXISTS oa_warehouse_out;
DROP TABLE IF EXISTS oa_warehouse_in;
DROP TABLE IF EXISTS oa_warehouse;
DROP TABLE IF EXISTS oa_fund_plan;
DROP TABLE IF EXISTS oa_budget_item;
DROP TABLE IF EXISTS oa_budget;
DROP TABLE IF EXISTS oa_loan;
DROP TABLE IF EXISTS oa_expense_invoice;
DROP TABLE IF EXISTS oa_expense_item;
DROP TABLE IF EXISTS oa_expense;
DROP TABLE IF EXISTS oa_purchase_payment;
DROP TABLE IF EXISTS oa_purchase_apply_item;
DROP TABLE IF EXISTS oa_purchase_apply;
DROP TABLE IF EXISTS oa_contract_item;
DROP TABLE IF EXISTS oa_contract;
DROP TABLE IF EXISTS oa_project_member;
DROP TABLE IF EXISTS oa_project;
DROP TABLE IF EXISTS sys_admin_group_member;
DROP TABLE IF EXISTS sys_admin_group;
DROP TABLE IF EXISTS sys_app_module;
DROP TABLE IF EXISTS sys_app;
DROP TABLE IF EXISTS sys_oper_log;
DROP TABLE IF EXISTS sys_logininfor;
DROP TABLE IF EXISTS sys_notice;
DROP TABLE IF EXISTS sys_config;
DROP TABLE IF EXISTS sys_dict_data;
DROP TABLE IF EXISTS sys_dict_type;
DROP TABLE IF EXISTS sys_role_menu;
DROP TABLE IF EXISTS sys_menu;
DROP TABLE IF EXISTS sys_user_post;
DROP TABLE IF EXISTS sys_post;
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS sys_dept;
DROP TABLE IF EXISTS sys_tenant;

CREATE TABLE sys_tenant (
    tenant_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_name VARCHAR(128) NOT NULL,
    contact_name VARCHAR(64),
    contact_phone VARCHAR(32),
    status VARCHAR(1) NOT NULL DEFAULT '0',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_dept (
    dept_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    parent_id BIGINT NOT NULL DEFAULT 0,
    ancestors VARCHAR(512) NOT NULL DEFAULT '0',
    dept_name VARCHAR(64) NOT NULL,
    order_num INT NOT NULL DEFAULT 0,
    leader VARCHAR(64),
    phone VARCHAR(32),
    email VARCHAR(128),
    status VARCHAR(1) NOT NULL DEFAULT '0',
    del_flag VARCHAR(1) NOT NULL DEFAULT '0',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME
);

CREATE TABLE sys_user (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    dept_id BIGINT,
    user_name VARCHAR(64) NOT NULL UNIQUE,
    nick_name VARCHAR(64) NOT NULL,
    password VARCHAR(128) NOT NULL,
    email VARCHAR(128),
    phonenumber VARCHAR(32),
    sex VARCHAR(1) NOT NULL DEFAULT '0',
    avatar VARCHAR(512),
    status VARCHAR(1) NOT NULL DEFAULT '0',
    del_flag VARCHAR(1) NOT NULL DEFAULT '0',
    login_ip VARCHAR(64),
    login_date DATETIME,
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME,
    remark VARCHAR(500)
);

CREATE TABLE sys_role (
    role_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    role_name VARCHAR(64) NOT NULL,
    role_key VARCHAR(64) NOT NULL,
    role_sort INT NOT NULL DEFAULT 0,
    data_scope VARCHAR(1) NOT NULL DEFAULT '1',
    status VARCHAR(1) NOT NULL DEFAULT '0',
    del_flag VARCHAR(1) NOT NULL DEFAULT '0',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME,
    remark VARCHAR(500)
);

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE sys_post (
    post_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_code VARCHAR(64) NOT NULL,
    post_name VARCHAR(64) NOT NULL,
    post_sort INT NOT NULL DEFAULT 0,
    status VARCHAR(1) NOT NULL DEFAULT '0'
);

CREATE TABLE sys_user_post (
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, post_id)
);

CREATE TABLE sys_menu (
    menu_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    menu_name VARCHAR(64) NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    order_num INT NOT NULL DEFAULT 0,
    path VARCHAR(200),
    component VARCHAR(255),
    query VARCHAR(255),
    is_frame INT NOT NULL DEFAULT 1,
    is_cache INT NOT NULL DEFAULT 0,
    menu_type VARCHAR(1) NOT NULL DEFAULT 'C',
    visible VARCHAR(1) NOT NULL DEFAULT '0',
    status VARCHAR(1) NOT NULL DEFAULT '0',
    perms VARCHAR(128),
    icon VARCHAR(100),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
);

CREATE TABLE sys_dict_type (
    dict_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dict_name VARCHAR(100) NOT NULL,
    dict_type VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(1) NOT NULL DEFAULT '0',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_dict_data (
    dict_code BIGINT AUTO_INCREMENT PRIMARY KEY,
    dict_sort INT NOT NULL DEFAULT 0,
    dict_label VARCHAR(100) NOT NULL,
    dict_value VARCHAR(100) NOT NULL,
    dict_type VARCHAR(100) NOT NULL,
    css_class VARCHAR(100),
    list_class VARCHAR(100),
    is_default VARCHAR(1) NOT NULL DEFAULT 'N',
    status VARCHAR(1) NOT NULL DEFAULT '0'
);

CREATE TABLE sys_config (
    config_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_name VARCHAR(100) NOT NULL,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value VARCHAR(500),
    config_type VARCHAR(1) NOT NULL DEFAULT 'N',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark VARCHAR(500)
);

CREATE TABLE sys_notice (
    notice_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    notice_title VARCHAR(200) NOT NULL,
    notice_type VARCHAR(1) NOT NULL DEFAULT '1',
    notice_content TEXT,
    status VARCHAR(1) NOT NULL DEFAULT '0',
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_logininfor (
    info_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_name VARCHAR(64),
    ipaddr VARCHAR(64),
    browser VARCHAR(100),
    os VARCHAR(100),
    status VARCHAR(1),
    msg VARCHAR(255),
    login_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_oper_log (
    oper_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(100),
    business_type INT,
    method VARCHAR(200),
    request_method VARCHAR(10),
    oper_name VARCHAR(64),
    oper_url VARCHAR(255),
    oper_ip VARCHAR(64),
    oper_param TEXT,
    json_result TEXT,
    status INT,
    error_msg VARCHAR(2000),
    oper_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_app (
    tenant_app_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    app_name VARCHAR(100) NOT NULL,
    app_icon VARCHAR(255),
    app_path VARCHAR(255),
    app_type VARCHAR(32) NOT NULL DEFAULT 'OA',
    status VARCHAR(1) NOT NULL DEFAULT '0',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_app_module (
    tenant_module_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_app_id BIGINT NOT NULL,
    module_name VARCHAR(100) NOT NULL,
    module_path VARCHAR(255) NOT NULL,
    component VARCHAR(255) NOT NULL,
    icon VARCHAR(100),
    order_num INT NOT NULL DEFAULT 0,
    status VARCHAR(1) NOT NULL DEFAULT '0'
);

CREATE TABLE sys_admin_group (
    group_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_name VARCHAR(100) NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_admin_group_member (
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (group_id, user_id)
);

CREATE TABLE oa_project (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    nick_name VARCHAR(64),
    dept_id BIGINT,
    type VARCHAR(64),
    owner_unit VARCHAR(200),
    cons_unit VARCHAR(200),
    sketch TEXT,
    status VARCHAR(32) NOT NULL DEFAULT '在建',
    process_status VARCHAR(32) NOT NULL DEFAULT 'draft',
    attachment TEXT,
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME
);

CREATE TABLE oa_project_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    user_id BIGINT,
    user_name VARCHAR(64),
    nick_name VARCHAR(64),
    role_name VARCHAR(64),
    phone VARCHAR(32),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE oa_contract (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    project_id BIGINT,
    code VARCHAR(64),
    name VARCHAR(200) NOT NULL,
    type VARCHAR(64),
    party_a VARCHAR(200),
    party_b VARCHAR(200),
    money DECIMAL(18, 2) NOT NULL DEFAULT 0,
    tax_rate DECIMAL(8, 4),
    start_date DATE,
    end_date DATE,
    efficacy VARCHAR(32) NOT NULL DEFAULT '有效',
    process_status VARCHAR(32) NOT NULL DEFAULT 'draft',
    attachment TEXT,
    remark VARCHAR(1000),
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME
);

CREATE TABLE oa_contract_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    sn INT NOT NULL DEFAULT 0,
    name VARCHAR(200) NOT NULL,
    content TEXT,
    money DECIMAL(18, 2) NOT NULL DEFAULT 0,
    percent DECIMAL(8, 4),
    end_date DATETIME
);

CREATE TABLE oa_purchase_apply (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    project_id BIGINT,
    code VARCHAR(64),
    apply_user_id BIGINT,
    apply_user_name VARCHAR(64),
    dept_id BIGINT,
    purpose VARCHAR(500),
    total_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    process_status VARCHAR(32) NOT NULL DEFAULT 'draft',
    attachment TEXT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME
);

CREATE TABLE oa_purchase_apply_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    apply_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    spec VARCHAR(200),
    unit VARCHAR(32),
    amount DECIMAL(18, 4) NOT NULL DEFAULT 0,
    price DECIMAL(18, 4) NOT NULL DEFAULT 0,
    total_price DECIMAL(18, 2) NOT NULL DEFAULT 0,
    description VARCHAR(500)
);

CREATE TABLE oa_purchase_payment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    apply_id BIGINT,
    project_id BIGINT,
    contract_id BIGINT,
    code VARCHAR(64),
    payee VARCHAR(200),
    pay_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    pay_date DATE,
    process_status VARCHAR(32) NOT NULL DEFAULT 'draft',
    remark VARCHAR(1000),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE oa_expense (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    project_id BIGINT,
    code VARCHAR(64),
    apply_user_id BIGINT,
    apply_user_name VARCHAR(64),
    dept_id BIGINT,
    expense_type VARCHAR(64),
    total_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    process_status VARCHAR(32) NOT NULL DEFAULT 'draft',
    remark VARCHAR(1000),
    attachment TEXT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME
);

CREATE TABLE oa_expense_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    expense_id BIGINT NOT NULL,
    budget_id BIGINT,
    detail VARCHAR(500),
    sum DECIMAL(18, 2) NOT NULL DEFAULT 0,
    replaced VARCHAR(1) NOT NULL DEFAULT 'N',
    remark VARCHAR(500)
);

CREATE TABLE oa_expense_invoice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    expense_id BIGINT NOT NULL,
    invoice_code VARCHAR(64),
    invoice_num VARCHAR(64),
    invoice_date DATE,
    check_code VARCHAR(64),
    machine_code VARCHAR(64),
    purchaser_name VARCHAR(200),
    purchaser_register_num VARCHAR(100),
    purchaser_bank VARCHAR(200),
    purchaser_address VARCHAR(300),
    amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    attachment TEXT
);

CREATE TABLE oa_loan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    code VARCHAR(64),
    apply_user_id BIGINT,
    apply_user_name VARCHAR(64),
    project_id BIGINT,
    amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    loan_date DATE,
    expected_repay_date DATE,
    actual_repay_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    process_status VARCHAR(32) NOT NULL DEFAULT 'draft',
    remark VARCHAR(1000),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE oa_budget (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    project_id BIGINT,
    code VARCHAR(64),
    name VARCHAR(200) NOT NULL,
    budget_year INT,
    total_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    used_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE oa_budget_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    budget_id BIGINT NOT NULL,
    subject_name VARCHAR(200) NOT NULL,
    subject_code VARCHAR(64),
    amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    used_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    remark VARCHAR(500)
);

CREATE TABLE oa_fund_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    project_id BIGINT,
    contract_id BIGINT,
    plan_type VARCHAR(32) NOT NULL DEFAULT 'out',
    plan_date DATE,
    amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    remark VARCHAR(500),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE oa_warehouse (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    code VARCHAR(64),
    name VARCHAR(200) NOT NULL,
    address VARCHAR(300),
    manager_id BIGINT,
    manager_name VARCHAR(64),
    status VARCHAR(1) NOT NULL DEFAULT '0',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE oa_warehouse_in (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    warehouse_id BIGINT,
    project_id BIGINT,
    code VARCHAR(64),
    in_date DATE,
    material_name VARCHAR(200),
    amount DECIMAL(18, 4),
    unit VARCHAR(32),
    process_status VARCHAR(32) NOT NULL DEFAULT 'draft',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE oa_warehouse_out (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    warehouse_id BIGINT,
    project_id BIGINT,
    code VARCHAR(64),
    out_date DATE,
    material_name VARCHAR(200),
    amount DECIMAL(18, 4),
    unit VARCHAR(32),
    process_status VARCHAR(32) NOT NULL DEFAULT 'draft',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE oa_invoice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    project_id BIGINT,
    contract_id BIGINT,
    invoice_code VARCHAR(64),
    invoice_num VARCHAR(64),
    invoice_type VARCHAR(32),
    invoice_date DATE,
    amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    attachment TEXT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE oa_business_opportunity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    name VARCHAR(200) NOT NULL,
    customer_name VARCHAR(200),
    owner_user_id BIGINT,
    owner_user_name VARCHAR(64),
    stage VARCHAR(64),
    expected_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    expected_close_date DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE oa_todo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    user_id BIGINT,
    title VARCHAR(200) NOT NULL,
    business_type VARCHAR(64),
    business_id BIGINT,
    process_instance_id VARCHAR(128),
    task_id VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'todo',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE oa_process_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    process_key VARCHAR(128) NOT NULL,
    process_name VARCHAR(200) NOT NULL,
    category VARCHAR(64),
    version INT NOT NULL DEFAULT 1,
    definition_json TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE oa_process_instance (
    id VARCHAR(128) PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    process_key VARCHAR(128),
    business_type VARCHAR(64),
    business_id BIGINT,
    starter_id BIGINT,
    starter_name VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'running',
    start_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_time DATETIME
);

CREATE TABLE oa_process_task (
    id VARCHAR(128) PRIMARY KEY,
    instance_id VARCHAR(128) NOT NULL,
    task_name VARCHAR(200),
    assignee_id BIGINT,
    assignee_name VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'todo',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    complete_time DATETIME
);

CREATE TABLE biz_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    module VARCHAR(100) NOT NULL,
    business_key VARCHAR(200),
    payload_json TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME
);

CREATE INDEX idx_oa_project_tenant ON oa_project (tenant_id, status);
CREATE INDEX idx_oa_contract_project ON oa_contract (project_id);
CREATE INDEX idx_oa_purchase_project ON oa_purchase_apply (project_id);
CREATE INDEX idx_oa_expense_project ON oa_expense (project_id);
CREATE INDEX idx_oa_todo_user ON oa_todo (user_id, status);
CREATE INDEX idx_biz_record_module ON biz_record (module, business_key);



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

INSERT INTO sys_user (
    user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber,
    sex, avatar, status, create_by, remark
) VALUES (
    1, 1, 101, '17630135660', '系统管理员',
    '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy',
    'admin@example.com', '17630135660', '0', '', '0', 'system', '本地演示管理员'
);

INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark)
VALUES (1, 1, '超级管理员', 'admin', 1, '1', '0', '拥有全部权限');
INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark)
VALUES (2, 1, '普通员工', 'employee', 2, '5', '0', '本人数据');

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

INSERT INTO sys_post (post_id, post_code, post_name, post_sort, status)
VALUES (1, 'ceo', '总经理', 1, '0');
INSERT INTO sys_post (post_id, post_code, post_name, post_sort, status)
VALUES (2, 'ptyg', '普通员工', 2, '0');
INSERT INTO sys_user_post (user_id, post_id) VALUES (1, 1);

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
(14, '菜单管理', 10, 4, 'menu', 'system/menu/index', 'C', '0', '0', 'system:menu:list', 'iconfont-menu');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu;

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
    128000.00, 6.0000, CURRENT_DATE, DATE_ADD(CURRENT_DATE, INTERVAL 365 DAY), '有效', 'approved', '17630135660'
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
    '需求确认', 260000.00, DATE_ADD(CURRENT_DATE, INTERVAL 60 DAY), 'running'
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


SET FOREIGN_KEY_CHECKS = 1;
