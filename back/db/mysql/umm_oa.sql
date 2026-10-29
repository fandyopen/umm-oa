-- UMM OA MySQL 8 initialization script
-- Generated from the H2 development schema by tools/build-mysql-sql.mjs.
-- Local demo administrator: 17630135660 / admin123123

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS umm_oa
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
USE umm_oa;

DROP TABLE IF EXISTS sys_bootstrap_marker;
DROP TABLE IF EXISTS oa_workflow_request;
DROP TABLE IF EXISTS oa_module_action_flow;
DROP TABLE IF EXISTS biz_record;
DROP TABLE IF EXISTS oa_form_submission;
DROP TABLE IF EXISTS oa_form_definition;
DROP TABLE IF EXISTS oa_process_design;
DROP TABLE IF EXISTS oa_merchant_account;
DROP TABLE IF EXISTS oa_customer_followup;
DROP TABLE IF EXISTS oa_merchant_collaborator;
DROP TABLE IF EXISTS oa_merchant_contact;
DROP TABLE IF EXISTS oa_merchant;
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
DROP TABLE IF EXISTS oa_repayment;
DROP TABLE IF EXISTS oa_loan_detail;
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
DROP TABLE IF EXISTS sys_admin_group_role;
DROP TABLE IF EXISTS sys_admin_group_dept;
DROP TABLE IF EXISTS sys_admin_group_app;
DROP TABLE IF EXISTS sys_admin_group_member;
DROP TABLE IF EXISTS sys_admin_group;
DROP TABLE IF EXISTS sys_admin_identity;
DROP TABLE IF EXISTS sys_app_module;
DROP TABLE IF EXISTS sys_app;
DROP TABLE IF EXISTS sys_oper_log;
DROP TABLE IF EXISTS sys_logininfor;
DROP TABLE IF EXISTS sys_notice;
DROP TABLE IF EXISTS sys_config;
DROP TABLE IF EXISTS sys_dict_data;
DROP TABLE IF EXISTS sys_dict_type;
DROP TABLE IF EXISTS sys_role_menu;
DROP TABLE IF EXISTS sys_role_module;
DROP TABLE IF EXISTS sys_role_module_action;
DROP TABLE IF EXISTS sys_module_action;
DROP TABLE IF EXISTS sys_dept_module;
DROP TABLE IF EXISTS sys_role_group_member;
DROP TABLE IF EXISTS sys_role_group;
DROP TABLE IF EXISTS sys_module;
DROP TABLE IF EXISTS sys_menu;
DROP TABLE IF EXISTS sys_user_post;
DROP TABLE IF EXISTS sys_post;
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS sys_dept_leader;
DROP TABLE IF EXISTS sys_dept_principal;
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

CREATE TABLE sys_dept_principal (
    dept_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (dept_id, user_id)
);

CREATE TABLE sys_dept_leader (
    dept_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (dept_id, user_id)
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
    is_admin VARCHAR(1) NOT NULL DEFAULT '0',
    is_owner VARCHAR(1) NOT NULL DEFAULT '0',
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

CREATE TABLE sys_module (
    module_id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    parent_id BIGINT NOT NULL DEFAULT 0,
    module_name VARCHAR(200) NOT NULL,
    route_name VARCHAR(128),
    path VARCHAR(255),
    component VARCHAR(255),
    icon VARCHAR(100),
    module_sort INT NOT NULL DEFAULT 0,
    hidden INT NOT NULL DEFAULT 0,
    perms VARCHAR(128),
    has_flow INT NOT NULL DEFAULT 1,
    flow_conditions TEXT,
    no_cache INT NOT NULL DEFAULT 0,
    is_app VARCHAR(1),
    is_frame VARCHAR(1),
    query VARCHAR(255),
    redirect VARCHAR(255),
    status VARCHAR(1) NOT NULL DEFAULT '0',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_module_action (
    module_id BIGINT NOT NULL,
    action_code VARCHAR(32) NOT NULL,
    action_name VARCHAR(64) NOT NULL,
    permission_code VARCHAR(128),
    handler_key VARCHAR(128),
    config_json TEXT,
    builtin INT NOT NULL DEFAULT 0,
    status VARCHAR(1) NOT NULL DEFAULT '0',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME,
    PRIMARY KEY (module_id, action_code)
);

CREATE TABLE sys_role_module_action (
    role_id BIGINT NOT NULL,
    module_id BIGINT NOT NULL,
    action_code VARCHAR(32) NOT NULL,
    PRIMARY KEY (role_id, module_id, action_code)
);

CREATE TABLE sys_role_module (
    role_id BIGINT NOT NULL,
    module_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, module_id)
);

CREATE TABLE sys_dept_module (
    dept_id BIGINT NOT NULL,
    module_id BIGINT NOT NULL,
    PRIMARY KEY (dept_id, module_id)
);

CREATE TABLE sys_role_group (
    group_id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    group_name VARCHAR(100) NOT NULL,
    sort INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_role_group_member (
    group_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (group_id, role_id)
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
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark VARCHAR(500)
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
    is_add VARCHAR(1) NOT NULL DEFAULT '0',
    role_read VARCHAR(1) NOT NULL DEFAULT '0',
    role_manage VARCHAR(1) NOT NULL DEFAULT '0',
    dept_read VARCHAR(1) NOT NULL DEFAULT '0',
    dept_manage VARCHAR(1) NOT NULL DEFAULT '0',
    sort INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_admin_group_member (
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (group_id, user_id)
);

CREATE TABLE sys_admin_group_app (
    group_id BIGINT NOT NULL,
    tenant_app_id BIGINT NOT NULL,
    PRIMARY KEY (group_id, tenant_app_id)
);

CREATE TABLE sys_admin_group_dept (
    group_id BIGINT NOT NULL,
    dept_id BIGINT NOT NULL,
    PRIMARY KEY (group_id, dept_id)
);

CREATE TABLE sys_admin_group_role (
    group_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (group_id, role_id)
);

CREATE TABLE sys_admin_identity (
    user_id BIGINT PRIMARY KEY,
    user_name VARCHAR(64) NOT NULL,
    nick_name VARCHAR(64) NOT NULL,
    email VARCHAR(128),
    phonenumber VARCHAR(32),
    sex VARCHAR(1) DEFAULT '0',
    status VARCHAR(1) DEFAULT '0',
    sort INT NOT NULL DEFAULT 0
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
    loan_type VARCHAR(64),
    amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    loan_date DATE,
    expected_repay_date DATE,
    actual_repay_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    process_status VARCHAR(32) NOT NULL DEFAULT 'draft',
    remark VARCHAR(1000),
    attachment TEXT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE oa_loan_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    loan_id BIGINT NOT NULL,
    loan_name VARCHAR(200) NOT NULL,
    sum DECIMAL(18, 2) NOT NULL DEFAULT 0,
    remark VARCHAR(500)
);

CREATE TABLE oa_repayment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    loan_id BIGINT NOT NULL,
    user_id BIGINT,
    amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    repay_date DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    remark VARCHAR(500),
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
    potential_cooperation VARCHAR(500),
    requirement TEXT,
    cooperation_intent VARCHAR(64),
    contract_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    received_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    last_followup_time DATETIME,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE oa_merchant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    serial VARCHAR(64),
    name VARCHAR(200) NOT NULL,
    type VARCHAR(64),
    address VARCHAR(500),
    tax_number VARCHAR(128),
    taxpayer_type VARCHAR(64),
    industry VARCHAR(100),
    company_scale VARCHAR(64),
    region VARCHAR(100),
    enterprise_nature VARCHAR(64),
    customer_source VARCHAR(64),
    cooperation_status VARCHAR(64),
    owner_user_id BIGINT,
    owner_user_name VARCHAR(64),
    public_pool VARCHAR(1) NOT NULL DEFAULT '0',
    assigned_at DATETIME,
    last_followup_time DATETIME,
    contract_expire_date DATE,
    last_payment_date DATE,
    cooperation_positions INT NOT NULL DEFAULT 0,
    referral_count INT NOT NULL DEFAULT 0,
    hire_count INT NOT NULL DEFAULT 0,
    contract_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    received_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    remark VARCHAR(1000),
    attachment TEXT,
    status VARCHAR(1) NOT NULL DEFAULT '0',
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME
);

CREATE TABLE oa_merchant_contact (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    name VARCHAR(100),
    phone VARCHAR(32),
    wechat VARCHAR(64),
    email VARCHAR(128),
    position VARCHAR(100),
    decision_role VARCHAR(64),
    primary_contact VARCHAR(1) NOT NULL DEFAULT '0',
    remark VARCHAR(500)
);

CREATE TABLE oa_merchant_collaborator (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(64),
    UNIQUE (merchant_id, user_id)
);

CREATE TABLE oa_customer_followup (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    merchant_id BIGINT NOT NULL,
    followup_time DATETIME NOT NULL,
    method VARCHAR(32),
    content TEXT,
    next_plan TEXT,
    next_followup_time DATETIME,
    owner_user_id BIGINT,
    owner_user_name VARCHAR(64),
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE oa_merchant_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    account VARCHAR(100),
    account_name VARCHAR(200),
    bank VARCHAR(200),
    remark VARCHAR(500)
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

CREATE TABLE oa_form_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    tenant_app_id BIGINT,
    form_key VARCHAR(100) NOT NULL UNIQUE,
    parent_key VARCHAR(100),
    form_type VARCHAR(20) NOT NULL DEFAULT 'form',
    flow_name VARCHAR(200) NOT NULL,
    icon INT NOT NULL DEFAULT 0,
    basic_setting TEXT,
    form_data TEXT,
    process_data TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'published',
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME
);

CREATE TABLE oa_form_submission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    tenant_app_id BIGINT,
    form_key VARCHAR(100) NOT NULL,
    data_json TEXT,
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE oa_process_design (
    id VARCHAR(128) PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    tenant_app_id BIGINT,
    menu_id BIGINT,
    menu_name VARCHAR(200),
    process_data TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'published',
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME
);

CREATE TABLE oa_module_action_flow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    tenant_app_id BIGINT NOT NULL DEFAULT 1,
    module_id BIGINT NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    process_design_id VARCHAR(128) NOT NULL,
    approval_enabled INT NOT NULL DEFAULT 1,
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME,
    UNIQUE (tenant_app_id, module_id, action_code)
);

CREATE TABLE oa_workflow_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    module_id BIGINT NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    payload_json TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'running',
    process_instance_id VARCHAR(128),
    process_task_id VARCHAR(128),
    process_task_name VARCHAR(200),
    handler_key VARCHAR(128),
    config_json TEXT,
    result_json TEXT,
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME,
    complete_time DATETIME
);

CREATE INDEX idx_oa_project_tenant ON oa_project (tenant_id, status);
CREATE INDEX idx_oa_contract_project ON oa_contract (project_id);
CREATE INDEX idx_oa_purchase_project ON oa_purchase_apply (project_id);
CREATE INDEX idx_oa_expense_project ON oa_expense (project_id);
CREATE INDEX idx_oa_todo_user ON oa_todo (user_id, status);
CREATE INDEX idx_biz_record_module ON biz_record (module, business_key);
CREATE INDEX idx_oa_workflow_request_instance ON oa_workflow_request (process_instance_id);



INSERT INTO sys_tenant (tenant_id, tenant_name, contact_name, contact_phone, status)
VALUES (1, '示例企业', '方铭渴', '17630135660', '0');

INSERT INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, leader, phone, status)
VALUES (100, 1, 0, '0', '示例企业', 0, '方铭渴', '17630135660', '0');
INSERT INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, leader, phone, status)
VALUES (101, 1, 100, '0,100', '综合管理部', 1, '方铭渴', '17630135660', '0');
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
    1, 1, 101, '17630135660', '方铭渴',
    '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy',
    'admin@example.com', '17630135660', '0', '', '0', 'system', '本地演示管理员'
);
INSERT INTO sys_user (
    user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber,
    sex, avatar, status, create_by, remark
) VALUES
    (2, 1, 100, '17673605591', '孟声怎',
     '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy',
     '', '17673605591', '0', '', '0', 'system', '总经理演示账号'),
    (3, 1, 102, '15685266238', '曹好疲',
     '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy',
     '', '15685266238', '0', '', '0', 'system', '财务会计/财务部经理演示账号'),
    (4, 1, 102, '17689037697', '卞在重',
     '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy',
     '', '17689037697', '0', '', '0', 'system', '财务出纳/合同归档演示账号'),
    (5, 1, 103, '15646844893', '费有感',
     '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy',
     '', '15646844893', '0', '', '0', 'system', '销售副总演示账号'),
    (6, 1, 103, '13689213154', '雷记那',
     '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy',
     '', '13689213154', '0', '', '0', 'system', '生产副总演示账号'),
    (7, 1, 103, '15650389435', '昌空紧',
     '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy',
     '', '15650389435', '0', '', '0', 'system', '项目总管演示账号'),
    (8, 1, 104, '15609825919', '吕听敢',
     '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy',
     '', '15609825919', '0', '', '0', 'system', '采购负责人演示账号'),
    (9, 1, 101, '17655872713', '苏记勇',
     '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy',
     '', '17655872713', '0', '', '0', 'system', '综合部经理/盖章确认演示账号');

UPDATE sys_user SET is_admin = '1', is_owner = '0' WHERE user_id = 1;

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

INSERT INTO sys_admin_group (
    group_id, group_name, tenant_id, is_add, role_read, role_manage,
    dept_read, dept_manage, sort
) VALUES (26, '企业管理', 1, '0', '0', '0', '0', '0', 0);
INSERT INTO sys_admin_group_member (group_id, user_id) VALUES
    (26, 17),
    (26, 14);

INSERT INTO sys_admin_identity (user_id, user_name, nick_name, email, phonenumber, sex, status, sort) VALUES
    (109960376, '13686621113', '严音泪', NULL, NULL, NULL, NULL, 0),
    (220440149, '15646021885', '张为骄', NULL, NULL, NULL, NULL, 1),
    (1756143678, '17603686635', '倪所力', NULL, NULL, NULL, NULL, 2),
    (2090571137, '13671476794', '奚哭芬', NULL, NULL, NULL, NULL, 3);

INSERT INTO oa_project (
    id, tenant_id, code, name, nick_name, dept_id, type, owner_unit, sketch, status, process_status, create_by
) VALUES (
    1, 1, 'QS-DEMO-001', '企业数字化办公平台', '方铭渴', 103, '1',
    '示例客户', '用于验证项目管理、合同、采购和报销流程的演示项目。', '在建', 'approved', '17630135660'
);

INSERT INTO oa_project_member (project_id, user_id, user_name, nick_name, role_name, phone)
VALUES (1, 1, '17630135660', '方铭渴', '项目经理', '17630135660');

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
) VALUES (1, 1, 1, 'CG-DEMO-001', 1, '方铭渴', 103, '采购办公设备', 6800.00, 'approved');

INSERT INTO oa_purchase_apply_item (apply_id, name, spec, unit, amount, price, total_price, description)
VALUES (1, '笔记本电脑', '14 英寸/32G/1T', '台', 2, 3400.00, 6800.00, '研发办公使用');

INSERT INTO oa_expense (
    id, tenant_id, project_id, code, apply_user_id, apply_user_name, dept_id,
    expense_type, total_amount, process_status, remark
) VALUES (1, 1, 1, 'BX-DEMO-001', 1, '方铭渴', 103, 'travel', 1260.00, 'running', '项目现场差旅');

INSERT INTO oa_expense_item (expense_id, budget_id, detail, sum, replaced, remark)
VALUES (1, NULL, '高铁及住宿', 1260.00, 'N', '项目现场支持');

INSERT INTO oa_loan (
    id, tenant_id, code, apply_user_id, apply_user_name, project_id, loan_type, amount,
    loan_date, expected_repay_date, actual_repay_amount, process_status, remark
) VALUES (
    1, 1, 'JK-DEMO-001', 1, '方铭渴', 1, 'cash', 5000.00,
    CURRENT_DATE, DATEADD('DAY', 30, CURRENT_DATE), 0.00, 'approved', '项目备用金'
);
INSERT INTO oa_loan_detail (loan_id, loan_name, sum, remark) VALUES
    (1, '现场交通', 1200.00, '项目现场往返'),
    (1, '临时采购', 3800.00, '办公耗材');

INSERT INTO oa_budget (id, tenant_id, project_id, code, name, budget_year, total_amount, used_amount, status)
VALUES (1, 1, 1, 'YS-DEMO-001', '数字化平台年度预算', 2026, 500000.00, 1260.00, 'approved');

INSERT INTO oa_budget_item (budget_id, subject_name, subject_code, amount, used_amount, remark)
VALUES (1, '差旅费', 'TRAVEL', 50000.00, 1260.00, '项目现场支持');
INSERT INTO oa_budget_item (budget_id, subject_name, subject_code, amount, used_amount, remark)
VALUES (1, '设备采购', 'DEVICE', 180000.00, 6800.00, '研发设备');

INSERT INTO oa_warehouse (id, tenant_id, code, name, address, manager_id, manager_name, status)
VALUES (1, 1, 'CK-DEMO-001', '项目现场仓', '项目现场 A 区', 1, '方铭渴', '0');

INSERT INTO oa_business_opportunity (
    id, tenant_id, name, customer_name, owner_user_id, owner_user_name,
    stage, expected_amount, expected_close_date, status
) VALUES (
    1, 1, '企业协同办公升级', '示例客户', 1, '方铭渴',
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


-- Generated from public reference system settings by tools/build-system-reference-data.mjs.
DELETE FROM sys_dict_data;
DELETE FROM sys_dict_type;
DELETE FROM sys_config;
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (1, '用户性别', 'sys_user_sex', '0', '2020-11-23 19:46:25', '用户性别列表') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (2, '菜单状态', 'sys_show_hide', '0', '2020-11-23 19:46:25', '菜单状态列表') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (3, '系统开关', 'sys_normal_disable', '0', '2020-11-23 19:46:25', '系统开关列表') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (4, '任务状态', 'sys_job_status', '0', '2020-11-23 19:46:25', '任务状态列表') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (5, '任务分组', 'sys_job_group', '0', '2020-11-23 19:46:25', '任务分组列表') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (6, '系统是否', 'sys_yes_no', '0', '2020-11-23 19:46:25', '系统是否列表') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (7, '通知类型', 'sys_notice_type', '0', '2020-11-23 19:46:25', '通知类型列表') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (8, '通知状态', 'sys_notice_status', '0', '2020-11-23 19:46:25', '通知状态列表') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (9, '操作类型', 'sys_oper_type', '0', '2020-11-23 19:46:25', '操作类型列表') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (10, '系统状态', 'sys_common_status', '0', '2020-11-23 19:46:25', '登录状态列表') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (11, '请假类型', 'biz_leave_type', '0', '2020-11-29 21:35:42', '') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (12, '项目类型', 'seal_project_type', '0', '2021-03-07 11:45:56', '') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (13, '项目状态', 'seal_project_status', '0', '2021-03-13 13:30:53', '') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (14, '项目所在区域', 'seal_project_region', '0', '2021-03-22 08:19:18', '') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (15, '是否招投标', 'seal_project_is_bid', '0', '2021-03-25 01:34:45', '') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (16, '甲方资金来源', 'seal_project_fund_source', '0', '2021-03-25 09:14:03', '') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (17, '资金到位情况', 'seal_project_fund_desc', '0', '2021-03-25 09:21:01', '') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (18, '人力投入情况', 'seal_project_human_input', '0', '2021-03-25 09:24:37', '') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (19, '项目紧急程度', 'seal_project_urgency_degree', '0', '2021-03-25 09:33:27', '') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (20, '垫资时长', 'seal_project_loaning_time', '0', '2021-03-25 10:01:37', '') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (21, '预算类型', 'seal_project_budget_type', '0', '2021-04-08 09:07:11', '') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (22, '合同类型', 'seal_project_contract_type', '0', '2021-04-12 13:11:52', '') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (23, '项目成员角色', 'seal_project_mem_role', '0', '2021-04-26 05:35:50', '') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (26, '单位', 'seal_project_unit', '0', '2021-05-25 08:08:34', '') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (27, '采购方式', 'seal_project_purchasing_method', '0', '2021-05-25 08:40:03', '') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (28, '直接成本支出', 'seal_project_type_of_direct', '0', '2021-05-26 01:35:08', '') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (29, '间接费用支出', 'seal_project_type_of_indirect', '0', '2021-05-26 07:07:52', '') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (30, '发票类型', 'seal_project_invoice_type', '0', '2021-05-28 05:43:07', '') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (31, '借款类型', 'seal_project_borrowings_type', '0', '2021-08-11 09:37:10', '') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (32, '合同效力', 'seal_project_contract_efficacy', '0', '2021-09-01 15:28:00', '合同效力') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (33, '保证金类型', 'seal_project_earnestMoney_type', '0', '2021-11-10 13:55:08', '保证金类型') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (34, '审批阶段', 'seal_flow_stage', '0', '2022-01-24 09:40:22', '') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (35, '直接成本支出旧', 'seal_project_type_of_direct_old', '0', '2022-08-03 10:01:07', '') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_type (dict_id, dict_name, dict_type, status, create_time, remark) VALUES (36, '间接费用支出旧', 'seal_project_type_of_indirect_old', '0', '2022-08-03 10:01:33', '') ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), dict_type = VALUES(dict_type), status = VALUES(status), remark = VALUES(remark);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (29, 0, '事假', '0', 'biz_leave_type', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (76, 0, '销售预算', '销售预算', 'seal_project_budget_type', '', 'purple', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (81, 0, '销售经理', '销售经理', 'seal_project_mem_role', '', 'blue', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (98, 0, '成都青云石智能科技有限公司', '成都青云石智能科技有限公司', 'seal_project_unit', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (99, 0, '四川卓信力加信息科技有限责任公司', '四川卓信力加信息科技有限责任公司', 'seal_project_unit', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (100, 0, '成都合力腾达科技有限公司', '成都合力腾达科技有限公司', 'seal_project_unit', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (101, 0, '西藏依普洛凌科技有限公司', '西藏依普洛凌科技有限公司', 'seal_project_unit', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (102, 0, '合同', '合同', 'seal_project_purchasing_method', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (103, 0, '合同未签', '合同未签', 'seal_project_purchasing_method', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (104, 0, '非合同/员工直接采购', '非合同/员工直接采购', 'seal_project_purchasing_method', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (114, 0, '普票', '普票', 'seal_project_invoice_type', '', 'blue', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (116, 0, '项目费用', '项目费用', 'seal_project_borrowings_type', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (119, 0, '效力待定', '效力待定', 'seal_project_contract_efficacy', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (120, 0, '有效', '有效', 'seal_project_contract_efficacy', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (121, 0, '无效', '无效', 'seal_project_contract_efficacy', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (123, 0, '投标保证金', '投标保证金', 'seal_project_earnestMoney_type', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (127, 0, '财务部经理', '财务部经理', 'seal_flow_stage', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (131, 0, '重庆千旭企业管理咨询中心（有限合伙）', '重庆千旭企业管理咨询中心（有限合伙）', 'seal_project_unit', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (132, 0, '重庆盛奕企业管理咨询中心（有限合伙）', '重庆盛奕企业管理咨询中心（有限合伙）', 'seal_project_unit', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (133, 0, '昌都市卡若区利扬电子经营部', '昌都市卡若区利扬电子经营部', 'seal_project_unit', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (134, 0, '工程/服务外包采购', '工程/服务外包采购', 'seal_project_type_of_direct', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (135, 0, '会议/培训费', '会议/培训费', 'seal_project_type_of_indirect', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (136, 0, '差旅费', '差旅费', 'seal_project_type_of_indirect', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (137, 0, '其他', '其他', 'seal_project_type_of_indirect', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (139, 0, '运费', '运费', 'seal_project_type_of_indirect', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (141, 0, '业务招待费', '业务招待费', 'seal_project_type_of_indirect', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (142, 0, '租赁费', '租赁费', 'seal_project_type_of_indirect', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (143, 0, '交通费（生产）', '交通费（生产）', 'seal_project_type_of_indirect', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (144, 0, '车辆、燃油费', '车辆、燃油费', 'seal_project_type_of_indirect', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (148, 0, '设备、材料采购（无合同）', '设备、材料采购（无合同）', 'seal_project_type_of_direct', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (149, 0, '临时、外聘人员费用', '临时、外聘人员费用', 'seal_project_type_of_direct', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (150, 0, '公司人员费用', '公司人员费用', 'seal_project_type_of_direct', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (151, 0, '设备、材料采购（合同）', '设备、材料采购（合同）', 'seal_project_type_of_direct', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (152, 0, '项目风险金', '项目风险金', 'seal_project_type_of_indirect', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (153, 0, '货款', '货款', 'seal_project_type_of_direct_old', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (154, 0, '零星材料', '零星材料', 'seal_project_type_of_direct_old', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (155, 0, '运费', '运费', 'seal_project_type_of_direct_old', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (156, 0, '汽车使用费', '汽车使用费', 'seal_project_type_of_direct_old', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (157, 0, '劳务费', '劳务费', 'seal_project_type_of_direct_old', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (158, 0, '其他直接费用', '其他直接费用', 'seal_project_type_of_direct_old', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (159, 0, '现场经费', '现场经费', 'seal_project_type_of_indirect_old', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (160, 0, '部门费用', '部门费用', 'seal_project_borrowings_type', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (1, 1, '男', '0', 'sys_user_sex', '', 'default', 'Y', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (4, 1, '显示', '0', 'sys_show_hide', '', 'primary', 'Y', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (6, 1, '正常', '0', 'sys_normal_disable', '', 'primary', 'Y', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (8, 1, '正常', '0', 'sys_job_status', '', 'primary', 'Y', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (10, 1, '默认', 'DEFAULT', 'sys_job_group', '', 'default', 'Y', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (12, 1, '是', 'Y', 'sys_yes_no', '', 'primary', 'Y', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (14, 1, '通知', '1', 'sys_notice_type', '', 'warning', 'Y', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (16, 1, '正常', '0', 'sys_notice_status', '', 'primary', 'Y', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (18, 1, '新增', '1', 'sys_oper_type', '', 'info', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (27, 1, '成功', '0', 'sys_common_status', '', 'primary', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (30, 1, '年假', '1', 'biz_leave_type', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (32, 1, '综合', '综合', 'seal_project_type', '', 'pink', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (40, 1, '售前', '售前', 'seal_project_status', '', '#108ee9', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (43, 1, '四川及周边', '四川及周边', 'seal_project_region', '', 'blue', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (47, 1, '是', '是', 'seal_project_is_bid', '', 'green', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (50, 1, '客户自筹资金', '客户自筹资金', 'seal_project_fund_source', '', 'blue', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (57, 1, '已到位', '已到位', 'seal_project_fund_desc', '', 'green', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (61, 1, '小规模(1-3人)', '小规模(1-3人)', 'seal_project_human_input', '', 'orange', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (66, 1, '紧急', '紧急', 'seal_project_urgency_degree', '', 'red', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (69, 1, '1月', '1月', 'seal_project_loaning_time', '', 'purple', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (77, 1, '生产预算', '生产预算', 'seal_project_budget_type', '', 'orange', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (79, 1, '收入合同', '收入合同', 'seal_project_contract_type', '', 'green', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (82, 1, '项目经理', '项目经理', 'seal_project_mem_role', '', 'cyan', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (115, 1, '专票', '专票', 'seal_project_invoice_type', '', 'orange', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (117, 1, '商务备用金类', '商务备用金类', 'seal_project_borrowings_type', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (124, 1, '履约保证金', '履约保证金', 'seal_project_earnestMoney_type', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (128, 1, '财务出纳', '财务出纳', 'seal_flow_stage', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (2, 2, '女', '1', 'sys_user_sex', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (5, 2, '隐藏', '1', 'sys_show_hide', '', 'danger', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (7, 2, '停用', '1', 'sys_normal_disable', '', 'danger', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (9, 2, '暂停', '1', 'sys_job_status', '', 'danger', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (11, 2, '系统', 'SYSTEM', 'sys_job_group', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (13, 2, '否', 'N', 'sys_yes_no', '', 'danger', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (15, 2, '公告', '2', 'sys_notice_type', '', 'success', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (17, 2, '关闭', '1', 'sys_notice_status', '', 'danger', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (19, 2, '修改', '2', 'sys_oper_type', '', 'info', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (28, 2, '失败', '1', 'sys_common_status', '', 'danger', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (31, 2, '病假', '2', 'biz_leave_type', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (33, 2, '工程施工', '工程施工', 'seal_project_type', '', 'red', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (41, 2, '在建', '在建', 'seal_project_status', '', '#87d068', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (44, 2, '昌都地区', '昌都地区', 'seal_project_region', '', 'cyan', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (48, 2, '否', '否', 'seal_project_is_bid', '', 'red', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (51, 2, '中央财政预算投资', '中央财政预算投资', 'seal_project_fund_source', '', 'cyan', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (58, 2, '落实并筹集中', '落实并筹集中', 'seal_project_fund_desc', '', 'blue', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (62, 2, '一般规模(3-5人)', '一般规模(3-5人)', 'seal_project_human_input', '', 'cyan', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (67, 2, '正常进度', '正常进度', 'seal_project_urgency_degree', '', 'blue', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (70, 2, '1季度', '1季度', 'seal_project_loaning_time', '', 'blue', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (78, 2, '管理预算', '管理预算', 'seal_project_budget_type', '', 'blue', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (80, 2, '支出合同', '支出合同', 'seal_project_contract_type', '', 'orange', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (83, 2, '负责人', '负责人', 'seal_project_mem_role', '', 'purple', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (118, 2, '来往及垫资类', '往来及垫资类', 'seal_project_borrowings_type', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (3, 3, '未知', '2', 'sys_user_sex', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (20, 3, '删除', '3', 'sys_oper_type', '', 'danger', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (34, 3, '系统集成', '系统集成', 'seal_project_type', '', 'orange', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (42, 3, '关闭', '关闭', 'seal_project_status', '', '#8c8c8c', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (45, 3, '拉萨地区', '拉萨地区', 'seal_project_region', '', 'green', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (49, 3, '不确定', '不确定', 'seal_project_is_bid', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (52, 3, '地方财政预算投资', '地方财政预算投资', 'seal_project_fund_source', '', 'green', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (59, 3, '未落实', '未落实', 'seal_project_fund_desc', '', 'orange', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (63, 3, '中等规模(5-10人)', '中等规模(5-10人)', 'seal_project_human_input', '', 'blue', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (68, 3, '不紧急', '不紧急', 'seal_project_urgency_degree', '', 'cyan', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (71, 3, '半年', '半年', 'seal_project_loaning_time', '', 'cyan', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (84, 3, '普通成员', '普通成员', 'seal_project_mem_role', '', 'green', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (130, 3, '已结束', '已结束', 'seal_flow_stage', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (21, 4, '授权', '4', 'sys_oper_type', '', 'primary', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (35, 4, '技术服务', '技术服务', 'seal_project_type', '', 'green', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (46, 4, '软件开发', '软件开发', 'seal_project_region', '', 'purple', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (53, 4, '银行贷款投资', '银行贷款投资', 'seal_project_fund_source', '', 'purple', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (60, 4, '待确定', '待确定', 'seal_project_fund_desc', '', 'red', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (64, 4, '较大规模(10-20人)', '较大规模(10-20人)', 'seal_project_human_input', '', 'purple', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (72, 4, '1年', '1年', 'seal_project_loaning_time', '', 'green', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (22, 5, '导出', '5', 'sys_oper_type', '', 'warning', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (36, 5, '软件研发', '软件研发', 'seal_project_type', '', 'cyan', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (54, 5, '利用外资', '利用外资', 'seal_project_fund_source', '', 'orange', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (65, 5, '大规模(20人以上)', '大规模(20人以上)', 'seal_project_human_input', '', 'green', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (73, 5, '1年半', '1年半', 'seal_project_loaning_time', '', 'orange', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (122, 5, '其他', '其他', 'seal_project_region', '', '#bfbfbf', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (23, 6, '导入', '6', 'sys_oper_type', '', 'warning', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (37, 6, '产品销售', '产品销售', 'seal_project_type', '', 'blue', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (55, 6, '社会融资', '社会融资', 'seal_project_fund_source', '', 'pink', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (74, 6, '2年', '2年', 'seal_project_loaning_time', '', 'red', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (24, 7, '强退', '7', 'sys_oper_type', '', 'danger', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (38, 7, '运营（销售、生产）', '运营（销售、生产）', 'seal_project_type', '', 'purple', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (56, 7, '其他', '其他', 'seal_project_fund_source', '', 'default', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (75, 7, '2年以上', '2年以上', 'seal_project_loaning_time', '', 'pink', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (25, 8, '生成代码', '8', 'sys_oper_type', '', 'warning', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (39, 8, '运营（综合、财务、研发）', '运营（综合、财务、研发）', 'seal_project_type', '', 'purple', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status) VALUES (26, 9, '清空数据', '9', 'sys_oper_type', '', 'danger', 'N', '0') ON DUPLICATE KEY UPDATE dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value), dict_type = VALUES(dict_type), list_class = VALUES(list_class), status = VALUES(status);
INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type, create_time, remark) VALUES (1, 'dingUrl', 'dingUrl', 'http://eos.drotion-lega.com:8001', 'Y', '2022-07-07 08:32:18', '') ON DUPLICATE KEY UPDATE config_name = VALUES(config_name), config_key = VALUES(config_key), config_value = VALUES(config_value), config_type = VALUES(config_type), remark = VALUES(remark);
INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type, create_time, remark) VALUES (2, 'corpId', 'corpId', 'ding9435debeee63556c', 'Y', '2022-07-07 08:32:48', '') ON DUPLICATE KEY UPDATE config_name = VALUES(config_name), config_key = VALUES(config_key), config_value = VALUES(config_value), config_type = VALUES(config_type), remark = VALUES(remark);
INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type, create_time, remark) VALUES (3, 'corpSecret', 'corpSecret', 'MaMzkhJqfhS0f_1rlQSRuniLVfLGMPDBr_CDbOXNZTACSYPc2S1zf-T-B8U9U8Yb', 'Y', '2022-07-07 08:33:15', '') ON DUPLICATE KEY UPDATE config_name = VALUES(config_name), config_key = VALUES(config_key), config_value = VALUES(config_value), config_type = VALUES(config_type), remark = VALUES(remark);
INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type, create_time, remark) VALUES (4, 'appKey', 'appKey', 'dingnmvxkvi5ojokuooo', 'Y', '2022-07-07 08:33:32', '') ON DUPLICATE KEY UPDATE config_name = VALUES(config_name), config_key = VALUES(config_key), config_value = VALUES(config_value), config_type = VALUES(config_type), remark = VALUES(remark);
INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type, create_time, remark) VALUES (5, 'appSecret', 'appSecret', 'ZJGRUQwc6YqwWyY1aPDZwPOzvtlkQLj5dOllDfk2bJaLw0fZrDytluK22f06Iol4', 'Y', '2022-07-07 08:33:45', '') ON DUPLICATE KEY UPDATE config_name = VALUES(config_name), config_key = VALUES(config_key), config_value = VALUES(config_value), config_type = VALUES(config_type), remark = VALUES(remark);
INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type, create_time, remark) VALUES (6, 'agentId', 'agentId', '1245169866', 'Y', '2022-07-07 08:33:58', '') ON DUPLICATE KEY UPDATE config_name = VALUES(config_name), config_key = VALUES(config_key), config_value = VALUES(config_value), config_type = VALUES(config_type), remark = VALUES(remark);
INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type, create_time, remark) VALUES (100, '使用文档入口', 'sys.docs.enabled', 'true', 'Y', '2026-10-28 21:40:00', '控制顶部“使用文档”入口是否显示，默认开启') ON DUPLICATE KEY UPDATE config_name = VALUES(config_name), config_key = VALUES(config_key), config_value = VALUES(config_value), config_type = VALUES(config_type), remark = VALUES(remark);
INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type, create_time, remark) VALUES (101, '使用文档标题', 'sys.docs.title', '使用文档', 'N', '2026-10-28 21:40:00', '顶部文档入口及文档中心标题') ON DUPLICATE KEY UPDATE config_name = VALUES(config_name), config_key = VALUES(config_key), config_value = VALUES(config_value), config_type = VALUES(config_type), remark = VALUES(remark);


UPDATE sys_menu SET perms = '' WHERE menu_id BETWEEN 1 AND 14;
INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark) VALUES (7, 1, '财务会计', 'finance_accountant', 7, '1', '0', '财务流程和查询协同') ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), role_key = VALUES(role_key), role_sort = VALUES(role_sort), remark = VALUES(remark);
INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark) VALUES (8, 1, '财务出纳', 'cashier', 8, '1', '0', '收付款与资金确认协同') ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), role_key = VALUES(role_key), role_sort = VALUES(role_sort), remark = VALUES(remark);
INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark) VALUES (9, 1, '合同归档', 'contract_archive', 9, '1', '0', '文档归档和审批协同') ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), role_key = VALUES(role_key), role_sort = VALUES(role_sort), remark = VALUES(remark);
INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark) VALUES (10, 1, '副总-生产', 'production_vp', 10, '1', '0', '生产协同和审批') ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), role_key = VALUES(role_key), role_sort = VALUES(role_sort), remark = VALUES(remark);
INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark) VALUES (11, 1, '采购', 'purchasing', 11, '1', '0', '客商和采购审批协同') ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), role_key = VALUES(role_key), role_sort = VALUES(role_sort), remark = VALUES(remark);
INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark) VALUES (12, 1, '综合部经理', 'admin_manager', 12, '1', '0', '组织协同、通知公告和公共审批') ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), role_key = VALUES(role_key), role_sort = VALUES(role_sort), remark = VALUES(remark);
INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark) VALUES (13, 1, '盖章确认', 'seal_confirm', 13, '1', '0', '审批盖章确认') ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), role_key = VALUES(role_key), role_sort = VALUES(role_sort), remark = VALUES(remark);
INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark) VALUES (14, 1, '回收作废', 'void_recovery', 14, '1', '0', '审批回收与作废') ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), role_key = VALUES(role_key), role_sort = VALUES(role_sort), remark = VALUES(remark);
INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark) VALUES (15, 1, '销售', 'sales', 15, '1', '0', '销售流程和客户协同') ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), role_key = VALUES(role_key), role_sort = VALUES(role_sort), remark = VALUES(remark);
INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark) VALUES (16, 1, '执行董事', 'executive_director', 16, '1', '0', '全局经营查看和审批') ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), role_key = VALUES(role_key), role_sort = VALUES(role_sort), remark = VALUES(remark);
INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark) VALUES (17, 1, '党建-项目总管', 'party_project_manager', 17, '1', '0', '党建审批协同') ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), role_key = VALUES(role_key), role_sort = VALUES(role_sort), remark = VALUES(remark);
INSERT INTO sys_role (role_id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark) VALUES (18, 1, '党建-副总', 'party_vice_president', 18, '1', '0', '党建协同与审批') ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), role_key = VALUES(role_key), role_sort = VALUES(role_sort), remark = VALUES(remark);
DELETE FROM sys_user_role WHERE user_id IN (1, 2, 3, 4, 5, 6, 7, 8, 9);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (2, 3);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (3, 4);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (3, 7);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (4, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (4, 8);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (4, 9);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (5, 5);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (6, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (6, 10);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (7, 6);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (8, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (8, 11);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (9, 12);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (9, 13);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (9, 14);
DELETE FROM sys_role_module;
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (3, 340341);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (3, 340342);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (3, 340527);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (3, 340785);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (3, 340352);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (3, 340786);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (3, 340590);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (3, 340644);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (3, 341101);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (4, 340341);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (4, 340342);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (4, 340527);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (4, 340785);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (4, 340352);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (4, 340786);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (5, 340341);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (5, 340342);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (5, 340527);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (5, 340785);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (5, 340590);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (5, 340644);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (5, 340352);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (5, 340786);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (5, 341101);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (6, 340341);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (6, 340342);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (6, 340527);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (6, 340785);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (6, 340590);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (6, 340644);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (6, 340352);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (6, 340786);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (6, 341101);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (7, 340341);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (7, 340342);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (7, 340527);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (7, 340785);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (7, 340352);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (7, 340786);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (8, 340341);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (8, 340342);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (8, 340527);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (8, 340785);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (8, 340352);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (8, 340786);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (9, 340341);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (9, 340342);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (9, 340527);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (9, 340785);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (9, 340352);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (9, 340786);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (10, 340341);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (10, 340342);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (10, 340527);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (10, 340785);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (10, 340590);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (10, 340644);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (10, 340352);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (10, 340786);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (10, 341101);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (11, 340341);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (11, 340342);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (11, 340527);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (11, 340785);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (11, 340352);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (11, 340786);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (12, 340341);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (12, 340342);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (12, 340527);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (12, 340785);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (12, 340782);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (12, 340316);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (12, 340786);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (13, 340341);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (13, 340342);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (13, 340527);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (13, 340785);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (13, 340782);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (13, 340316);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (13, 340786);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (14, 340341);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (14, 340342);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (14, 340527);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (14, 340785);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (14, 340782);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (14, 340316);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (14, 340786);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (15, 340341);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (15, 340342);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (15, 340527);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (15, 340785);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (15, 340590);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (15, 340644);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (15, 340352);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (15, 340786);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (15, 341101);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (16, 340341);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (16, 340342);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (16, 340527);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (16, 340785);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (16, 340352);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (16, 340786);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (16, 340590);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (16, 340644);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (16, 341101);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (17, 340341);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (17, 340342);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (17, 340527);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (17, 340785);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (17, 340590);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (17, 340644);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (17, 340352);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (17, 340786);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (17, 341101);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (18, 340341);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (18, 340342);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (18, 340527);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (18, 340785);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (18, 340352);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (18, 340786);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (18, 340590);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (18, 340644);
INSERT IGNORE INTO sys_role_module (role_id, module_id) VALUES (18, 341101);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (7, 1);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (7, 2);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (7, 3);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (7, 4);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (7, 5);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (7, 6);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (7, 7);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (7, 8);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (7, 9);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 1);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 2);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 3);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 4);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 5);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 6);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 7);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 8);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 9);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 1);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 2);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 3);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 4);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 5);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 6);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 7);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 8);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 9);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 1);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 2);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 3);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 4);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 5);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 6);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 7);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 8);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 9);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 1);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 2);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 3);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 4);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 5);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 6);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 7);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 8);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 9);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (12, 1);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (12, 2);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (12, 3);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (12, 4);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (12, 5);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (12, 6);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (12, 7);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (12, 8);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (12, 9);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (13, 1);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (13, 2);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (13, 3);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (13, 4);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (13, 5);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (13, 6);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (13, 7);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (13, 8);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (13, 9);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (14, 1);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (14, 2);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (14, 3);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (14, 4);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (14, 5);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (14, 6);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (14, 7);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (14, 8);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (14, 9);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (15, 1);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (15, 2);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (15, 3);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (15, 4);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (15, 5);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (15, 6);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (15, 7);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (15, 8);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (15, 9);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (16, 1);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (16, 2);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (16, 3);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (16, 4);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (16, 5);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (16, 6);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (16, 7);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (16, 8);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (16, 9);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (17, 1);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (17, 2);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (17, 3);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (17, 4);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (17, 5);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (17, 6);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (17, 7);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (17, 8);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (17, 9);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (18, 1);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (18, 2);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (18, 3);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (18, 4);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (18, 5);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (18, 6);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (18, 7);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (18, 8);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (18, 9);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10000, 'eos:budget:integratedQuery', 0, 0, '', '', 1, 0, 'F', '1', '0', 'eos:budget:integratedQuery', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10001, 'eos:gzt:cwzg', 0, 0, '', '', 1, 0, 'F', '1', '0', 'eos:gzt:cwzg', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10002, 'eos:gzt:gg', 0, 0, '', '', 1, 0, 'F', '1', '0', 'eos:gzt:gg', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10003, 'eos:gzt:gy', 0, 0, '', '', 1, 0, 'F', '1', '0', 'eos:gzt:gy', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10004, 'eos:gzt:xmzg', 0, 0, '', '', 1, 0, 'F', '1', '0', 'eos:gzt:xmzg', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10005, 'eos:gzt:xszg', 0, 0, '', '', 1, 0, 'F', '1', '0', 'eos:gzt:xszg', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10006, 'eos:pay:integratedQuery', 0, 0, '', '', 1, 0, 'F', '1', '0', 'eos:pay:integratedQuery', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10007, 'eos:porject:integratedQuery', 0, 0, '', '', 1, 0, 'F', '1', '0', 'eos:porject:integratedQuery', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10008, 'monitor:logininfor:export', 0, 0, '', '', 1, 0, 'F', '1', '0', 'monitor:logininfor:export', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10009, 'monitor:logininfor:list', 0, 0, '', '', 1, 0, 'F', '1', '0', 'monitor:logininfor:list', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10010, 'monitor:logininfor:query', 0, 0, '', '', 1, 0, 'F', '1', '0', 'monitor:logininfor:query', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10011, 'monitor:logininfor:remove', 0, 0, '', '', 1, 0, 'F', '1', '0', 'monitor:logininfor:remove', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10012, 'monitor:operlog:export', 0, 0, '', '', 1, 0, 'F', '1', '0', 'monitor:operlog:export', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10013, 'monitor:operlog:list', 0, 0, '', '', 1, 0, 'F', '1', '0', 'monitor:operlog:list', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10014, 'monitor:operlog:query', 0, 0, '', '', 1, 0, 'F', '1', '0', 'monitor:operlog:query', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10015, 'monitor:operlog:remove', 0, 0, '', '', 1, 0, 'F', '1', '0', 'monitor:operlog:remove', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10016, 'project:bid:add', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:bid:add', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10017, 'project:bid:edit', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:bid:edit', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10018, 'project:bid:export', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:bid:export', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10019, 'project:bid:list', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:bid:list', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10020, 'project:bid:query', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:bid:query', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10021, 'project:bid:remove', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:bid:remove', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10022, 'project:business:add', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:business:add', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10023, 'project:business:edit', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:business:edit', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10024, 'project:business:export', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:business:export', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10025, 'project:business:list', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:business:list', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10026, 'project:business:query', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:business:query', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10027, 'project:business:remove', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:business:remove', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10028, 'project:income:add', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:income:add', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10029, 'project:income:edit', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:income:edit', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10030, 'project:income:export', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:income:export', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10031, 'project:income:list', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:income:list', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10032, 'project:income:query', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:income:query', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10033, 'project:income:remove', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:income:remove', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10034, 'project:merchant:add', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:merchant:add', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10035, 'project:merchant:edit', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:merchant:edit', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10036, 'project:merchant:export', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:merchant:export', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10037, 'project:merchant:list', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:merchant:list', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10038, 'project:merchant:query', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:merchant:query', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10039, 'project:merchant:remove', 0, 0, '', '', 1, 0, 'F', '1', '0', 'project:merchant:remove', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10040, 'sys:module', 0, 0, '', '', 1, 0, 'F', '1', '0', 'sys:module', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10041, 'system:config:add', 0, 0, '', '', 1, 0, 'F', '1', '0', 'system:config:add', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10042, 'system:config:edit', 0, 0, '', '', 1, 0, 'F', '1', '0', 'system:config:edit', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10043, 'system:config:export', 0, 0, '', '', 1, 0, 'F', '1', '0', 'system:config:export', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10044, 'system:config:list', 0, 0, '', '', 1, 0, 'F', '1', '0', 'system:config:list', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10045, 'system:config:query', 0, 0, '', '', 1, 0, 'F', '1', '0', 'system:config:query', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10046, 'system:config:remove', 0, 0, '', '', 1, 0, 'F', '1', '0', 'system:config:remove', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10047, 'system:dict:add', 0, 0, '', '', 1, 0, 'F', '1', '0', 'system:dict:add', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10048, 'system:dict:edit', 0, 0, '', '', 1, 0, 'F', '1', '0', 'system:dict:edit', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10049, 'system:dict:export', 0, 0, '', '', 1, 0, 'F', '1', '0', 'system:dict:export', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10050, 'system:dict:list', 0, 0, '', '', 1, 0, 'F', '1', '0', 'system:dict:list', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10051, 'system:dict:query', 0, 0, '', '', 1, 0, 'F', '1', '0', 'system:dict:query', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10052, 'system:dict:remove', 0, 0, '', '', 1, 0, 'F', '1', '0', 'system:dict:remove', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10053, 'system:notice:add', 0, 0, '', '', 1, 0, 'F', '1', '0', 'system:notice:add', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10054, 'system:notice:edit', 0, 0, '', '', 1, 0, 'F', '1', '0', 'system:notice:edit', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10055, 'system:notice:list', 0, 0, '', '', 1, 0, 'F', '1', '0', 'system:notice:list', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10056, 'system:notice:query', 0, 0, '', '', 1, 0, 'F', '1', '0', 'system:notice:query', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10057, 'system:notice:remove', 0, 0, '', '', 1, 0, 'F', '1', '0', 'system:notice:remove', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10058, 'todoItem:done:list', 0, 0, '', '', 1, 0, 'F', '1', '0', 'todoItem:done:list', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon) VALUES (10059, 'todoItem:todo:list', 0, 0, '', '', 1, 0, 'F', '1', '0', 'todoItem:todo:list', '') ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10035);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10021);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10022);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10009);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10000);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10017);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10044);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10058);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10040);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10018);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10032);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10015);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10047);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10059);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10012);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10001);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10057);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10050);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10055);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10025);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10049);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10039);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10043);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10013);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10008);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10036);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10033);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10046);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10030);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10048);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10011);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10002);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10003);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10020);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10051);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10054);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10023);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10042);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10038);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10007);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10004);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10052);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10045);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10031);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10010);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10026);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10041);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10028);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10034);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10024);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10056);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10037);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10019);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10005);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10006);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10014);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10029);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10053);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10027);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 10016);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 10035);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 10030);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 10003);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 10038);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 10058);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 10031);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 10032);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 10059);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 10028);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 10034);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 10037);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 10039);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 10029);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 10036);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (8, 10033);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 10035);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 10030);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 10003);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 10038);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 10058);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 10031);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 10032);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 10059);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 10028);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 10034);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 10037);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 10039);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 10029);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 10036);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (9, 10033);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10035);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10021);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10022);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10017);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10058);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10018);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10032);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10059);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10025);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10039);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10036);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10033);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10030);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10002);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10020);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10023);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10038);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10031);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10026);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10028);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10034);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10024);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10037);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10019);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10029);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10027);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (5, 10016);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10035);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10021);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10022);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10017);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10058);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10018);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10032);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10059);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10025);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10039);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10036);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10033);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10030);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10002);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10003);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10020);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10023);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10038);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10031);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10026);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10028);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10034);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10024);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10037);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10019);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10029);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10027);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 10016);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 10035);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 10030);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 10003);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 10038);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 10058);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 10031);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 10032);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 10059);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 10028);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 10034);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 10037);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 10039);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 10029);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 10036);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 10033);


INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (340340, 1, 0, '待办', 'TodoItem', '/todoItem', 'Layout', 'iconfont-daibanshixiang9', 0, 0, '', 1, '', 0, '', '', '', 'noRedirect', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (340341, 1, 340340, '我的待办', 'Todo', 'todo', 'eos/todoItem/todo/index', 'iconfont-daibanshixiang7', 1, 0, 'todoItem:todo:list', 1, '', 0, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (340342, 1, 340340, '我的已办', 'Done', 'done', 'eos/todoItem/done/index', 'iconfont-daiban3', 2, 0, 'todoItem:done:list', 1, '', 0, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (340527, 1, 340340, '我的发起', 'My-process-start', 'my-process-start', 'eos/todoItem/todo/processStartedPage', 'iconfont-daibanshixiang8', 3, 0, 'todoItem:done:list', 1, '', 0, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (340785, 1, 340340, '抄送我的', 'Copy', 'copy', 'eos/todoItem/copy/index', 'iconfont-tijiao', 4, 0, '', 1, '', 0, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (340310, 1, 0, '客户管理', 'CRM', '/crm', 'Layout', 'iconfont-xiaoshouloudou', 1, 0, '', 1, '', 0, '', '', '', 'noRedirect', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (340590, 1, 340310, '商机管理', 'Business-mange', 'opportunities', 'eos/business/index', 'iconfont-shuju_shangji_shangjimingcheng_huaban1', 2, 0, 'project:business:list', 1, '', 0, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (340644, 1, 340310, '客户分析', 'CrmAnalysis', 'analysis', 'eos/crm/analysis/index', 'iconfont-chengbenfenxi-', 3, 0, 'project:analysis:list', 0, '', 0, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (340352, 1, 340310, '客户档案', 'Customers', 'customers', 'eos/crm/customer/index', 'iconfont-xiaoshou', 1, 0, 'project:merchant:list', 1, '', 0, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (340309, 1, 0, '系统', 'System', '/system', 'Layout', 'iconfont-xitongguanli1', 8, 0, '', 1, '', 0, '', '', '', 'noRedirect', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (340782, 1, 340309, '组织机构', 'Zzjg', 'zzjg', 'enterprise/Contacts/contactsOp', 'iconfont-bumen3', 0, 0, '', 1, '', 0, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (340597, 1, 340309, '角色权限', 'Menu-perms', 'menu-perms', 'enterprise/modulePerms/index', 'iconfont-mokuai', 1, 0, 'sys:module', 1, '', 0, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (340314, 1, 340309, '基础数据', 'Dict', 'dict', 'system/dict/index', 'iconfont-xiangmuzidian', 3, 0, 'system:dict:list', 1, '', 0, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (340771, 1, 340309, '流程设置', 'Module_manage', 'module_manage', 'system/menu/index', 'iconfont-mokuai', 4, 0, 'sys:module', 1, '', 0, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (340315, 1, 340309, '参数设置', 'Config', 'config', 'system/config/index', 'iconfont-canshupeizhi', 7, 0, 'system:config:list', 1, '', 0, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (340316, 1, 340309, '通知公告', 'Notice', 'notice', 'system/notice/index', 'iconfont-tongzhigonggao2', 8, 0, 'system:notice:list', 1, '', 0, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (340317, 1, 340309, '日志管理', 'Log', 'log', 'ParentView', 'iconfont-rizhi2', 9, 0, '', 1, '', 0, '', '', '', 'noRedirect', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (340318, 1, 340317, '操作日志', 'Operlog', 'operlog', 'system/log/operlog/index', 'iconfont-caozuorizhi1', 1, 0, 'monitor:operlog:list', 1, '', 0, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (340319, 1, 340317, '登录日志', 'Logininfor', 'logininfor', 'system/log/logininfor/index', 'iconfont-guanlidenglurizhi', 2, 0, 'monitor:logininfor:list', 1, '', 0, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (340786, 1, 0, '工作台', 'Gzt', '/gzt', 'Layout', '', 9, 1, '', 1, '', 0, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (343098, 1, 0, '综合查询权限', '#', '/#', 'Layout', '#', 99, 1, '', 1, '', 0, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (341000, 1, 0, '人事管理', 'HR', '/hr', 'Layout', 'iconfont-tuandui4', 3, 0, '', 1, '', 0, '', '', '', 'noRedirect', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (341001, 1, 341000, '成员管理', 'HrMembers', 'members', 'enterprise/hr/index', 'iconfont-tuandui4', 1, 0, 'hr:member:list', 1, '', 0, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (341100, 1, 340309, '使用文档', 'Docs', 'docs', 'docs/index', 'iconfont-bangzhu', 99, 1, 'system:docs:list', 0, '', 0, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (341101, 1, 340310, '风险提醒', 'CrmRisks', 'risks', 'eos/crm/risk/index', 'iconfont-yujing', 4, 0, 'project:risk:list', 0, '', 0, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);


INSERT INTO sys_role_group (group_id, tenant_id, group_name, sort) VALUES (49, 1, '财务角色组', 1) ON DUPLICATE KEY UPDATE group_name = VALUES(group_name), sort = VALUES(sort);
INSERT INTO sys_role_group (group_id, tenant_id, group_name, sort) VALUES (50, 1, '其他', 5) ON DUPLICATE KEY UPDATE group_name = VALUES(group_name), sort = VALUES(sort);
INSERT INTO sys_role_group (group_id, tenant_id, group_name, sort) VALUES (53, 1, '流程设计', 3) ON DUPLICATE KEY UPDATE group_name = VALUES(group_name), sort = VALUES(sort);
INSERT INTO sys_role_group (group_id, tenant_id, group_name, sort) VALUES (56, 1, '测试角色组', 0) ON DUPLICATE KEY UPDATE group_name = VALUES(group_name), sort = VALUES(sort);
DELETE FROM sys_role_group_member;
INSERT IGNORE INTO sys_role_group_member (group_id, role_id) VALUES (49, 7);
INSERT IGNORE INTO sys_role_group_member (group_id, role_id) VALUES (49, 8);
INSERT IGNORE INTO sys_role_group_member (group_id, role_id) VALUES (50, 1);
INSERT IGNORE INTO sys_role_group_member (group_id, role_id) VALUES (50, 2);
INSERT IGNORE INTO sys_role_group_member (group_id, role_id) VALUES (50, 11);
INSERT IGNORE INTO sys_role_group_member (group_id, role_id) VALUES (50, 14);
INSERT IGNORE INTO sys_role_group_member (group_id, role_id) VALUES (50, 15);
INSERT IGNORE INTO sys_role_group_member (group_id, role_id) VALUES (53, 3);
INSERT IGNORE INTO sys_role_group_member (group_id, role_id) VALUES (53, 4);
INSERT IGNORE INTO sys_role_group_member (group_id, role_id) VALUES (53, 5);
INSERT IGNORE INTO sys_role_group_member (group_id, role_id) VALUES (53, 6);
INSERT IGNORE INTO sys_role_group_member (group_id, role_id) VALUES (53, 9);
INSERT IGNORE INTO sys_role_group_member (group_id, role_id) VALUES (53, 10);
INSERT IGNORE INTO sys_role_group_member (group_id, role_id) VALUES (53, 12);
INSERT IGNORE INTO sys_role_group_member (group_id, role_id) VALUES (53, 13);
INSERT IGNORE INTO sys_role_group_member (group_id, role_id) VALUES (53, 16);
INSERT IGNORE INTO sys_role_group_member (group_id, role_id) VALUES (53, 17);
INSERT IGNORE INTO sys_role_group_member (group_id, role_id) VALUES (53, 18);
INSERT INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, status, del_flag) VALUES (322, 1, 0, '0', '海豹科技有限公司', 0, '0', '0') ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), dept_name = VALUES(dept_name), order_num = VALUES(order_num), del_flag = VALUES(del_flag);
INSERT INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, status, del_flag) VALUES (323, 1, 322, '0,322', '总经办', 0, '0', '0') ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), dept_name = VALUES(dept_name), order_num = VALUES(order_num), del_flag = VALUES(del_flag);
INSERT INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, status, del_flag) VALUES (324, 1, 322, '0,322', '综合部', 1, '0', '0') ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), dept_name = VALUES(dept_name), order_num = VALUES(order_num), del_flag = VALUES(del_flag);
INSERT INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, status, del_flag) VALUES (325, 1, 322, '0,322', '财务部', 2, '0', '0') ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), dept_name = VALUES(dept_name), order_num = VALUES(order_num), del_flag = VALUES(del_flag);
INSERT INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, status, del_flag) VALUES (326, 1, 322, '0,322', '销售部', 3, '0', '0') ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), dept_name = VALUES(dept_name), order_num = VALUES(order_num), del_flag = VALUES(del_flag);
INSERT INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, status, del_flag) VALUES (327, 1, 322, '0,322', '北京分公司', 4, '0', '0') ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), dept_name = VALUES(dept_name), order_num = VALUES(order_num), del_flag = VALUES(del_flag);
INSERT INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, status, del_flag) VALUES (328, 1, 322, '0,322', '上海分公司', 5, '0', '0') ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), dept_name = VALUES(dept_name), order_num = VALUES(order_num), del_flag = VALUES(del_flag);
INSERT INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, status, del_flag) VALUES (329, 1, 322, '0,322', '软件开发部', 6, '0', '0') ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), dept_name = VALUES(dept_name), order_num = VALUES(order_num), del_flag = VALUES(del_flag);
INSERT INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, status, del_flag) VALUES (330, 1, 322, '0,322', '项目管理部', 7, '0', '0') ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), dept_name = VALUES(dept_name), order_num = VALUES(order_num), del_flag = VALUES(del_flag);
INSERT INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, status, del_flag) VALUES (332, 1, 330, '0,322,330', '四川项目组', 0, '0', '0') ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), dept_name = VALUES(dept_name), order_num = VALUES(order_num), del_flag = VALUES(del_flag);
INSERT INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, status, del_flag) VALUES (333, 1, 330, '0,322,330', '云南项目组', 1, '0', '0') ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), dept_name = VALUES(dept_name), order_num = VALUES(order_num), del_flag = VALUES(del_flag);
INSERT INTO sys_dept (dept_id, tenant_id, parent_id, ancestors, dept_name, order_num, status, del_flag) VALUES (334, 1, 330, '0,322,330', '湖北项目组', 2, '0', '0') ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), dept_name = VALUES(dept_name), order_num = VALUES(order_num), del_flag = VALUES(del_flag);
UPDATE sys_user SET dept_id = 329, nick_name = '方铭渴', is_admin = '1', is_owner = '0' WHERE user_id = 1;
UPDATE sys_user SET dept_id = 323, nick_name = '孟声怎', is_admin = '1', is_owner = '1' WHERE user_id = 2;
UPDATE sys_user SET dept_id = 325, nick_name = '曹好疲', is_admin = '1', is_owner = '1' WHERE user_id = 3;
UPDATE sys_user SET dept_id = 325, nick_name = '卞在重', is_admin = '1', is_owner = '1' WHERE user_id = 4;
UPDATE sys_user SET dept_id = 323, nick_name = '费有感', is_admin = '1', is_owner = '1' WHERE user_id = 5;
UPDATE sys_user SET dept_id = 323, nick_name = '雷记那', is_admin = '1', is_owner = '1' WHERE user_id = 6;
UPDATE sys_user SET dept_id = 330, nick_name = '昌空紧', is_admin = '1', is_owner = '1' WHERE user_id = 7;
UPDATE sys_user SET dept_id = 324, nick_name = '吕听敢', is_admin = '1', is_owner = '1' WHERE user_id = 8;
UPDATE sys_user SET dept_id = 324, nick_name = '苏记勇', is_admin = '1', is_owner = '1' WHERE user_id = 9;
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (10, 1, 327, '17694407532', '金么算', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '17694407532@seals.com', '17694407532', '0', '', '0', '0', '1', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (11, 1, 329, '15622302041', '潘遇个', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '15622302041@seals.com', '15622302041', '0', '', '0', '0', '1', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (12, 1, 329, '17696380742', '余遇样', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '17696380742@seals.com', '17696380742', '1', '', '0', '0', '1', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (13, 1, 329, '15691679382', '费刻千', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '15691679382@seals.com', '15691679382', '0', '', '0', '0', '1', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (14, 1, 329, '15631729231', '奚追最', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '15631729231@seals.com', '15631729231', '0', '', '0', '0', '1', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (15, 1, 329, '15607343553', '卞是钧', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '15607343553@seals.com', '15607343553', '0', '', '0', '0', '1', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (16, 1, 329, '17601531494', '严听时', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '17601531494@seals.com', '17601531494', '0', '', '0', '0', '1', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (17, 1, 329, '13642991601', '昌远把', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '13642991601@seals.com', '13642991601', '0', '', '0', '0', '1', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (18, 1, 329, '15663619300', '钱谁能', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '15663619300@seals.com', '15663619300', '0', '', '0', '0', '1', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (19, 1, 334, '17608125060', '魏真光', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '17608125060@seals.com', '17608125060', '0', '', '0', '0', '1', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (20, 1, 329, '13671476794', '奚哭芬', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '13671476794@seals.com', '13671476794', '0', '', '0', '0', '0', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (21, 1, 332, '13650889224', '彭听闭', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '13650889224@seals.com', '13650889224', '0', '', '0', '0', '1', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (22, 1, 326, '15658845789', '韦了着', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '15658845789@seals.com', '15658845789', '0', '', '0', '0', '1', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (23, 1, 323, '15644767938', '谢落感', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '15644767938@seals.com', '15644767938', '0', '', '0', '0', '1', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (24, 1, 334, '17647290204', '钱中风', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '17647290204@seals.com', '17647290204', '0', '', '0', '0', '1', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (25, 1, 324, '13686621113', '严音泪', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '13686621113@seals.com', '13686621113', '1', '', '0', '0', '0', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (26, 1, 333, '17611505677', '鲍好隐', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '17611505677@seals.com', '17611505677', '0', '', '0', '0', '1', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (27, 1, 326, '15646021885', '张为骄', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '15646021885@seals.com', '15646021885', '0', '', '0', '0', '0', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (28, 1, 323, '17682811526', '云在坠', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '17682811526@seals.com', '17682811526', '0', '', '0', '0', '1', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (29, 1, 332, '17686455840', '岑响对', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '17686455840@seals.com', '17686455840', '0', '', '0', '0', '1', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (30, 1, 326, '15602834120', '凤不握', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '15602834120@seals.com', '15602834120', '0', '', '0', '0', '1', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (31, 1, 333, '15697080775', '谢雨上', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '15697080775@seals.com', '15697080775', '0', '', '0', '0', '1', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (32, 1, 324, '13640084343', '彭课在', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '13640084343@seals.com', '13640084343', '0', '', '0', '0', '1', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (33, 1, 333, '13656619176', '康还个', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '13656619176@seals.com', '13656619176', '0', '', '0', '0', '1', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, password, email, phonenumber, sex, avatar, status, del_flag, is_admin, is_owner, create_by, remark) VALUES (34, 1, 333, '17661061682', '安听天', '$2b$12$nopR.IbdlsiAu1H9PE9MbeCgKM2Px8B87NDujHm1Fst/VYPwl6Ivy', '17661061682@seals.com', '17661061682', '0', '', '0', '0', '1', '1', 'reference', '参考站演示成员') ON DUPLICATE KEY UPDATE dept_id = VALUES(dept_id), nick_name = VALUES(nick_name), email = VALUES(email), phonenumber = VALUES(phonenumber), status = VALUES(status), is_admin = VALUES(is_admin), is_owner = VALUES(is_owner);
DELETE FROM sys_user_role WHERE user_id BETWEEN 10 AND 34;
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (10, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (11, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (13, 18);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (14, 4);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (15, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (15, 17);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (16, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (17, 5);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (17, 6);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (17, 10);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (17, 4);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (18, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (19, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (21, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (22, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (23, 16);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (23, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (24, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (25, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (25, 15);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (26, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (27, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (28, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (29, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (30, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (31, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (32, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (33, 2);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (34, 2);
DELETE FROM sys_dept WHERE dept_id BETWEEN 100 AND 104;
UPDATE sys_tenant SET tenant_name = '海豹科技有限公司' WHERE tenant_id = 1;
DELETE FROM sys_dept_module;
INSERT INTO sys_dept_module (dept_id, module_id)
SELECT DISTINCT u.dept_id, rm.module_id
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.user_id
JOIN sys_role_module rm ON rm.role_id = ur.role_id
WHERE u.del_flag = '0';
INSERT INTO sys_dept_module (dept_id, module_id)
SELECT 329, m.module_id FROM sys_module m
WHERE m.status = '0' AND NOT EXISTS (
  SELECT 1 FROM sys_dept_module dm WHERE dm.dept_id = 329 AND dm.module_id = m.module_id
);


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


INSERT INTO oa_form_definition (
    id, tenant_id, tenant_app_id, form_key, parent_key, form_type, flow_name,
    icon, basic_setting, form_data, process_data, status, create_by
) VALUES (
    1, 1, 1, 'demo-flow-form', NULL, 'flow', '演示审批流程', 0,
    '{"flowName":"演示审批流程","flowImg":0}',
    '{"fields":[[{"type":"input","label":"申请金额","vModel":"amount"}],[{"type":"textarea","label":"申请说明","vModel":"reason"}]]}',
    '{"nodes":[{"id":"start","type":"start","name":"发起人"},{"id":"approval_1","type":"approval","name":"部门负责人","approver":"部门负责人","approvalMode":"any"},{"id":"end","type":"end","name":"流程结束"}],"edges":[{"source":"start","target":"approval_1"},{"source":"approval_1","target":"end"}]}',
    'published', 'system'
) ON DUPLICATE KEY UPDATE flow_name = VALUES(flow_name), basic_setting = VALUES(basic_setting),
    form_data = VALUES(form_data), process_data = VALUES(process_data);

INSERT INTO oa_form_definition (
    id, tenant_id, tenant_app_id, form_key, parent_key, form_type, flow_name,
    icon, basic_setting, form_data, process_data, status, create_by
) VALUES (
    2, 1, 1, 'demo-basic-form', NULL, 'form', '演示登记表单', 1,
    '{"flowName":"演示登记表单","flowImg":1}',
    '{"fields":[[{"type":"input","label":"名称","vModel":"name"}],[{"type":"textarea","label":"备注","vModel":"remark"}]]}',
    '{}',
    'published', 'system'
) ON DUPLICATE KEY UPDATE flow_name = VALUES(flow_name), basic_setting = VALUES(basic_setting),
    form_data = VALUES(form_data), process_data = VALUES(process_data);


-- Generated from public reference list API responses by tools/build-parity-data.mjs.
DELETE FROM biz_record WHERE id BETWEEN 5000 AND 5999;
INSERT INTO biz_record (id, tenant_id, module, business_key, payload_json, status, create_by, create_time) VALUES (5000, 1, '/project/business/list', '/project/business/list:1', '{"id":3,"delFlag":null,"createBy":null,"createTime":null,"updateBy":null,"updateTime":null,"remark":"","version":null,"tenantId_":"8a28d7d2dbb74a6e857ca016c9b4efcd","params":{},"name":"test_jjt","code":"20240125110507","attachment":null,"amount":null,"ownerUnit":"XX有限公司","fundSource":null,"loaningScale":null,"loaningTime":null,"bid":null,"profitRate":null,"sysUser":null,"sealBusinessStages":null,"sealBusinessUsers":null,"businessState":"初步接洽","customerId":1,"customerName":"示例客户","stage":"初步接洽","potentialCooperation":"test_jjt","requirement":"客户需要补充技术岗位并优化招聘交付流程","cooperationIntent":"中","expectedAmount":0,"ownerUserId":1,"ownerUserName":"方铭渴","statusLabel":"推进中"}', 'approved', '17630135660', '2026-10-26 18:00:00') ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json);
INSERT INTO biz_record (id, tenant_id, module, business_key, payload_json, status, create_by, create_time) VALUES (5001, 1, '/project/business/list', '/project/business/list:2', '{"id":2,"delFlag":null,"createBy":null,"createTime":null,"updateBy":null,"updateTime":null,"remark":"XX西高速南段35公里路段监控、信息发布及雾天公路诱导系统施工，光缆线路及供配电工程","version":null,"tenantId_":"8a28d7d2dbb74a6e857ca016c9b4efcd","params":{},"name":"2022年XX平安智慧升级改造项目","code":"20220804144043","attachment":null,"amount":10000000,"ownerUnit":"XX有限公司","fundSource":"客户自筹资金","loaningScale":"3000000","loaningTime":"3个月","bid":"是","profitRate":"20%","sysUser":null,"sealBusinessStages":null,"sealBusinessUsers":null,"businessState":"赢单","customerId":1,"customerName":"示例客户","stage":"赢单","potentialCooperation":"XX西高速南段35公里路段监控、信息发布及雾天公路诱导系统施工，光缆线路及供配电工程","requirement":"客户需要补充技术岗位并优化招聘交付流程","cooperationIntent":"中","expectedAmount":10000000,"ownerUserId":1,"ownerUserName":"方铭渴","statusLabel":"已赢单"}', 'approved', '17630135660', '2026-10-26 18:00:00') ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json);
INSERT INTO biz_record (id, tenant_id, module, business_key, payload_json, status, create_by, create_time) VALUES (5002, 1, '/project/business/list', '/project/business/list:3', '{"id":1,"delFlag":null,"createBy":null,"createTime":null,"updateBy":null,"updateTime":null,"remark":"XX县党建板块中华民族共同体意识教育基地、新时代文明实践中心、青少年活动中心等一基地两中心项目建设","version":null,"tenantId_":"8a28d7d2dbb74a6e857ca016c9b4efcd","params":{},"name":"XX县中华民族共同体意识教育基地建设","code":"20220803171053","attachment":null,"amount":null,"ownerUnit":null,"fundSource":null,"loaningScale":null,"loaningTime":null,"bid":null,"profitRate":null,"sysUser":null,"sealBusinessStages":null,"sealBusinessUsers":null,"businessState":"方案报价","customerId":1,"customerName":"示例客户","stage":"方案报价","potentialCooperation":"XX县党建板块中华民族共同体意识教育基地、新时代文明实践中心、青少年活动中心等一基地两中心项目建设","requirement":"客户需要补充技术岗位并优化招聘交付流程","cooperationIntent":"中","expectedAmount":0,"ownerUserId":1,"ownerUserName":"方铭渴","statusLabel":"推进中"}', 'approved', '17630135660', '2026-10-26 18:00:00') ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json);
INSERT INTO biz_record (id, tenant_id, module, business_key, payload_json, status, create_by, create_time) VALUES (5003, 1, '/project/bid/list', '/project/bid/list:1', '{"id":28,"delFlag":null,"createBy":"13981757840","createTime":null,"updateBy":null,"updateTime":null,"remark":"","version":null,"tenantId_":null,"params":{},"name":"test_jjt","code":"10086","businessId":null,"description":null,"openDate":"2024-01-26 11:02:05","agency":"XXX有限公司","attachment":null,"user":null,"business":null}', 'approved', '13981757840', '2026-10-26 18:00:00') ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json);
INSERT INTO biz_record (id, tenant_id, module, business_key, payload_json, status, create_by, create_time) VALUES (5004, 1, '/project/bid/list', '/project/bid/list:2', '{"id":27,"delFlag":null,"createBy":"18180495112","createTime":null,"updateBy":null,"updateTime":null,"remark":"","version":null,"tenantId_":null,"params":{},"name":"XX中烟工业有限责任公司XX卷烟厂XXXX年办公楼自动灭火系统升级改造项目","code":"CDQY-ZY-202300025","businessId":null,"description":null,"openDate":"2023-11-20 09:17:33","agency":"XXX有限公司","attachment":null,"user":null,"business":null}', 'approved', '18180495112', '2026-10-26 18:00:00') ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json);
INSERT INTO biz_record (id, tenant_id, module, business_key, payload_json, status, create_by, create_time) VALUES (5005, 1, '/project/bid/list', '/project/bid/list:3', '{"id":26,"delFlag":null,"createBy":"13761958967","createTime":null,"updateBy":null,"updateTime":null,"remark":"","version":null,"tenantId_":null,"params":{},"name":"XXXX某部安防设施设备采购项目（第二次）","code":"2022-JWSCMY-W1001","businessId":null,"description":null,"openDate":"2023-09-23 10:32:51","agency":"XXX有限公司","attachment":null,"user":null,"business":null}', 'approved', '13761958967', '2026-10-26 18:00:00') ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json);
INSERT INTO biz_record (id, tenant_id, module, business_key, payload_json, status, create_by, create_time) VALUES (5006, 1, '/project/bid/list', '/project/bid/list:4', '{"id":25,"delFlag":null,"createBy":"18180495112","createTime":null,"updateBy":null,"updateTime":null,"remark":"","version":null,"tenantId_":null,"params":{},"name":"XX爱上网络有限责任公司XXIPTV+业务管理和EPG平台硬件维保及巡检服务采购项目","code":"I53A00923001373","businessId":null,"description":null,"openDate":"2023-09-18 11:15:07","agency":"XXX有限公司","attachment":null,"user":null,"business":null}', 'approved', '18180495112', '2026-10-26 18:00:00') ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json);
INSERT INTO biz_record (id, tenant_id, module, business_key, payload_json, status, create_by, create_time) VALUES (5007, 1, '/project/bid/list', '/project/bid/list:5', '{"id":24,"delFlag":null,"createBy":"18180495112","createTime":null,"updateBy":null,"updateTime":null,"remark":"","version":null,"tenantId_":null,"params":{},"name":"XX中烟工业有限责任公司XX卷烟厂XXXX年增加坠落防护装置项目","code":"0730-236012CD0479","businessId":null,"description":null,"openDate":"2023-07-24 13:44:44","agency":"XXX有限公司","attachment":null,"user":null,"business":null}', 'approved', '18180495112', '2026-10-26 18:00:00') ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json);
INSERT INTO biz_record (id, tenant_id, module, business_key, payload_json, status, create_by, create_time) VALUES (5008, 1, '/project/bid/list', '/project/bid/list:6', '{"id":23,"delFlag":null,"createBy":"18180495112","createTime":null,"updateBy":null,"updateTime":null,"remark":"","version":null,"tenantId_":null,"params":{},"name":"“碧水云天”项目三期建筑智能化（弱电） 工程施工","code":"/","businessId":null,"description":null,"openDate":"2023-07-13 15:46:29","agency":"XXX有限公司","attachment":null,"user":null,"business":null}', 'approved', '18180495112', '2026-10-26 18:00:00') ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json);
INSERT INTO biz_record (id, tenant_id, module, business_key, payload_json, status, create_by, create_time) VALUES (5009, 1, '/project/bid/list', '/project/bid/list:7', '{"id":22,"delFlag":null,"createBy":"18180495112","createTime":null,"updateBy":null,"updateTime":null,"remark":"","version":null,"tenantId_":null,"params":{},"name":"XX中烟工业有限责任公司XX卷烟厂XXXX年成龙库区门禁系统维修","code":"ZYHT-ZY2305001-E","businessId":null,"description":null,"openDate":"2023-07-18 11:02:24","agency":"XXX有限公司","attachment":null,"user":null,"business":null}', 'approved', '18180495112', '2026-10-26 18:00:00') ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json);
INSERT INTO biz_record (id, tenant_id, module, business_key, payload_json, status, create_by, create_time) VALUES (5010, 1, '/project/bid/list', '/project/bid/list:8', '{"id":21,"delFlag":null,"createBy":"13761958967","createTime":null,"updateBy":null,"updateTime":null,"remark":"","version":null,"tenantId_":null,"params":{},"name":"西XX创新创业中心（一期）项目智能化弱电工程舞台音频设备采购项目","code":"/","businessId":null,"description":null,"openDate":"2023-06-19 13:32:17","agency":"XXX有限公司","attachment":null,"user":null,"business":null}', 'approved', '13761958967', '2026-10-26 18:00:00') ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json);
INSERT INTO biz_record (id, tenant_id, module, business_key, payload_json, status, create_by, create_time) VALUES (5011, 1, '/project/bid/list', '/project/bid/list:9', '{"id":20,"delFlag":null,"createBy":"15928863499","createTime":null,"updateBy":null,"updateTime":null,"remark":"","version":null,"tenantId_":null,"params":{},"name":"【XX市XX县GD乡廓琼岗日冰川公园通讯网络建设项目维保服务项目】","code":"TXZB-XZJC2023-055","businessId":null,"description":null,"openDate":"2023-06-08 11:07:40","agency":"XXX有限公司","attachment":null,"user":null,"business":null}', 'approved', '15928863499', '2026-10-26 18:00:00') ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json);
INSERT INTO biz_record (id, tenant_id, module, business_key, payload_json, status, create_by, create_time) VALUES (5012, 1, '/project/bid/list', '/project/bid/list:10', '{"id":19,"delFlag":null,"createBy":"18143807913","createTime":null,"updateBy":null,"updateTime":null,"remark":"","version":null,"tenantId_":null,"params":{},"name":"XX中烟工业有限责任公司长城雪茄烟厂XXXX年安防视频监控系统扩容项目","code":"【2023】02号","businessId":null,"description":null,"openDate":"2023-05-08 08:51:24","agency":"XXX有限公司","attachment":null,"user":null,"business":null}', 'approved', '18143807913', '2026-10-26 18:00:00') ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json);


-- Generated from public reference todo data by tools/build-todo-parity-data.mjs.
DELETE FROM biz_record WHERE id BETWEEN 6000 AND 6999;


-- Retired top-level modules: 项目, 合同与采购, 钱票收支.
-- Idempotent cleanup for existing databases and generated initialization scripts.

DELETE FROM sys_role_menu WHERE menu_id IN (77777,340343,340350,340351,340359,340360,340367,340369,340382,340389,340396,340403,340410,340417,340431,340445,340459,340466,340474,340475,340480,340481,340483,340492,340494,340496,340503,340504,340516,340517,340531,340539,340548,340549,340556,340563,340567,340568,340569,340570,340598,340605,340606,340613,340620,340627,340634,340635,340636,340687,340695,340703,340710,340711,340746,340748,340762,340774,340775,340776,340777,340778,340780,340792);
DELETE FROM sys_role_menu WHERE menu_id IN (
    SELECT menu_id FROM sys_menu WHERE perms LIKE 'eos:contract:%'
   OR perms LIKE 'eos:earnest:%'
   OR perms LIKE 'eos:expense:%'
   OR perms LIKE 'eos:fund:%'
   OR perms LIKE 'eos:invoice:%'
   OR perms LIKE 'eos:loan:%'
   OR perms LIKE 'eos:payment:%'
   OR perms LIKE 'eos:purchase:%'
   OR perms LIKE 'eos:repay:%'
   OR perms LIKE 'eos:warehouse:%'
   OR perms LIKE 'project:apply:%'
   OR perms LIKE 'project:budget:%'
   OR perms LIKE 'project:contract:%'
   OR perms LIKE 'project:contractChange:%'
   OR perms LIKE 'project:cost:%'
   OR perms LIKE 'project:detail:%'
   OR perms LIKE 'project:earnestMoney:%'
   OR perms LIKE 'project:earnestReturn:%'
   OR perms LIKE 'project:employee:%'
   OR perms LIKE 'project:exec:%'
   OR perms LIKE 'project:expense:%'
   OR perms LIKE 'project:fund:%'
   OR perms LIKE 'project:get:%'
   OR perms LIKE 'project:history:%'
   OR perms LIKE 'project:invoice:%'
   OR perms LIKE 'project:labor:%'
   OR perms LIKE 'project:loan:%'
   OR perms LIKE 'project:make:%'
   OR perms LIKE 'project:mem:%'
   OR perms LIKE 'project:onbus:%'
   OR perms LIKE 'project:outcome:%'
   OR perms LIKE 'project:payment:%'
   OR perms LIKE 'project:paymentApply:%'
   OR perms LIKE 'project:process:%'
   OR perms LIKE 'project:processWrite:%'
   OR perms LIKE 'project:project:%'
   OR perms LIKE 'project:quantities:%'
   OR perms LIKE 'project:real:%'
   OR perms LIKE 'project:repay:%'
   OR perms LIKE 'project:schedule:%'
   OR perms LIKE 'project:stage:%'
   OR perms LIKE 'project:type:%'
   OR perms LIKE 'project:warehouse:%'
   OR perms LIKE 'project:warehouseInApply:%'
   OR perms LIKE 'project:warehouseOutapply:%'
   OR perms LIKE 'project:warehouseTransferApply:%'
);
DELETE FROM sys_menu WHERE menu_id IN (77777,340343,340350,340351,340359,340360,340367,340369,340382,340389,340396,340403,340410,340417,340431,340445,340459,340466,340474,340475,340480,340481,340483,340492,340494,340496,340503,340504,340516,340517,340531,340539,340548,340549,340556,340563,340567,340568,340569,340570,340598,340605,340606,340613,340620,340627,340634,340635,340636,340687,340695,340703,340710,340711,340746,340748,340762,340774,340775,340776,340777,340778,340780,340792);
DELETE FROM sys_menu WHERE perms LIKE 'eos:contract:%'
   OR perms LIKE 'eos:earnest:%'
   OR perms LIKE 'eos:expense:%'
   OR perms LIKE 'eos:fund:%'
   OR perms LIKE 'eos:invoice:%'
   OR perms LIKE 'eos:loan:%'
   OR perms LIKE 'eos:payment:%'
   OR perms LIKE 'eos:purchase:%'
   OR perms LIKE 'eos:repay:%'
   OR perms LIKE 'eos:warehouse:%'
   OR perms LIKE 'project:apply:%'
   OR perms LIKE 'project:budget:%'
   OR perms LIKE 'project:contract:%'
   OR perms LIKE 'project:contractChange:%'
   OR perms LIKE 'project:cost:%'
   OR perms LIKE 'project:detail:%'
   OR perms LIKE 'project:earnestMoney:%'
   OR perms LIKE 'project:earnestReturn:%'
   OR perms LIKE 'project:employee:%'
   OR perms LIKE 'project:exec:%'
   OR perms LIKE 'project:expense:%'
   OR perms LIKE 'project:fund:%'
   OR perms LIKE 'project:get:%'
   OR perms LIKE 'project:history:%'
   OR perms LIKE 'project:invoice:%'
   OR perms LIKE 'project:labor:%'
   OR perms LIKE 'project:loan:%'
   OR perms LIKE 'project:make:%'
   OR perms LIKE 'project:mem:%'
   OR perms LIKE 'project:onbus:%'
   OR perms LIKE 'project:outcome:%'
   OR perms LIKE 'project:payment:%'
   OR perms LIKE 'project:paymentApply:%'
   OR perms LIKE 'project:process:%'
   OR perms LIKE 'project:processWrite:%'
   OR perms LIKE 'project:project:%'
   OR perms LIKE 'project:quantities:%'
   OR perms LIKE 'project:real:%'
   OR perms LIKE 'project:repay:%'
   OR perms LIKE 'project:schedule:%'
   OR perms LIKE 'project:stage:%'
   OR perms LIKE 'project:type:%'
   OR perms LIKE 'project:warehouse:%'
   OR perms LIKE 'project:warehouseInApply:%'
   OR perms LIKE 'project:warehouseOutapply:%'
   OR perms LIKE 'project:warehouseTransferApply:%';
DELETE FROM sys_role_module_action WHERE module_id IN (77777,340343,340350,340351,340359,340360,340367,340369,340382,340389,340396,340403,340410,340417,340431,340445,340459,340466,340474,340475,340480,340481,340483,340492,340494,340496,340503,340504,340516,340517,340531,340539,340548,340549,340556,340563,340567,340568,340569,340570,340598,340605,340606,340613,340620,340627,340634,340635,340636,340687,340695,340703,340710,340711,340746,340748,340762,340774,340775,340776,340777,340778,340780,340792);
DELETE FROM sys_module_action WHERE module_id IN (77777,340343,340350,340351,340359,340360,340367,340369,340382,340389,340396,340403,340410,340417,340431,340445,340459,340466,340474,340475,340480,340481,340483,340492,340494,340496,340503,340504,340516,340517,340531,340539,340548,340549,340556,340563,340567,340568,340569,340570,340598,340605,340606,340613,340620,340627,340634,340635,340636,340687,340695,340703,340710,340711,340746,340748,340762,340774,340775,340776,340777,340778,340780,340792);
DELETE FROM sys_role_module WHERE module_id IN (77777,340343,340350,340351,340359,340360,340367,340369,340382,340389,340396,340403,340410,340417,340431,340445,340459,340466,340474,340475,340480,340481,340483,340492,340494,340496,340503,340504,340516,340517,340531,340539,340548,340549,340556,340563,340567,340568,340569,340570,340598,340605,340606,340613,340620,340627,340634,340635,340636,340687,340695,340703,340710,340711,340746,340748,340762,340774,340775,340776,340777,340778,340780,340792);
DELETE FROM sys_dept_module WHERE module_id IN (77777,340343,340350,340351,340359,340360,340367,340369,340382,340389,340396,340403,340410,340417,340431,340445,340459,340466,340474,340475,340480,340481,340483,340492,340494,340496,340503,340504,340516,340517,340531,340539,340548,340549,340556,340563,340567,340568,340569,340570,340598,340605,340606,340613,340620,340627,340634,340635,340636,340687,340695,340703,340710,340711,340746,340748,340762,340774,340775,340776,340777,340778,340780,340792);
DELETE FROM oa_module_action_flow WHERE module_id IN (77777,340343,340350,340351,340359,340360,340367,340369,340382,340389,340396,340403,340410,340417,340431,340445,340459,340466,340474,340475,340480,340481,340483,340492,340494,340496,340503,340504,340516,340517,340531,340539,340548,340549,340556,340563,340567,340568,340569,340570,340598,340605,340606,340613,340620,340627,340634,340635,340636,340687,340695,340703,340710,340711,340746,340748,340762,340774,340775,340776,340777,340778,340780,340792);
DELETE FROM oa_workflow_request WHERE module_id IN (77777,340343,340350,340351,340359,340360,340367,340369,340382,340389,340396,340403,340410,340417,340431,340445,340459,340466,340474,340475,340480,340481,340483,340492,340494,340496,340503,340504,340516,340517,340531,340539,340548,340549,340556,340563,340567,340568,340569,340570,340598,340605,340606,340613,340620,340627,340634,340635,340636,340687,340695,340703,340710,340711,340746,340748,340762,340774,340775,340776,340777,340778,340780,340792);
DELETE FROM oa_process_design WHERE menu_id IN (77777,340343,340350,340351,340359,340360,340367,340369,340382,340389,340396,340403,340410,340417,340431,340445,340459,340466,340474,340475,340480,340481,340483,340492,340494,340496,340503,340504,340516,340517,340531,340539,340548,340549,340556,340563,340567,340568,340569,340570,340598,340605,340606,340613,340620,340627,340634,340635,340636,340687,340695,340703,340710,340711,340746,340748,340762,340774,340775,340776,340777,340778,340780,340792);
DELETE FROM sys_app_module WHERE module_path IN ('/project/index','/contract/index','/expense/index','/purchase/index');

DELETE FROM oa_todo WHERE process_instance_id IN (
    SELECT id FROM oa_process_instance WHERE process_key LIKE '%77777%'
   OR process_key LIKE '%340343%'
   OR process_key LIKE '%340350%'
   OR process_key LIKE '%340351%'
   OR process_key LIKE '%340359%'
   OR process_key LIKE '%340360%'
   OR process_key LIKE '%340367%'
   OR process_key LIKE '%340369%'
   OR process_key LIKE '%340382%'
   OR process_key LIKE '%340389%'
   OR process_key LIKE '%340396%'
   OR process_key LIKE '%340403%'
   OR process_key LIKE '%340410%'
   OR process_key LIKE '%340417%'
   OR process_key LIKE '%340431%'
   OR process_key LIKE '%340445%'
   OR process_key LIKE '%340459%'
   OR process_key LIKE '%340466%'
   OR process_key LIKE '%340474%'
   OR process_key LIKE '%340475%'
   OR process_key LIKE '%340480%'
   OR process_key LIKE '%340481%'
   OR process_key LIKE '%340483%'
   OR process_key LIKE '%340492%'
   OR process_key LIKE '%340494%'
   OR process_key LIKE '%340496%'
   OR process_key LIKE '%340503%'
   OR process_key LIKE '%340504%'
   OR process_key LIKE '%340516%'
   OR process_key LIKE '%340517%'
   OR process_key LIKE '%340531%'
   OR process_key LIKE '%340539%'
   OR process_key LIKE '%340548%'
   OR process_key LIKE '%340549%'
   OR process_key LIKE '%340556%'
   OR process_key LIKE '%340563%'
   OR process_key LIKE '%340567%'
   OR process_key LIKE '%340568%'
   OR process_key LIKE '%340569%'
   OR process_key LIKE '%340570%'
   OR process_key LIKE '%340598%'
   OR process_key LIKE '%340605%'
   OR process_key LIKE '%340606%'
   OR process_key LIKE '%340613%'
   OR process_key LIKE '%340620%'
   OR process_key LIKE '%340627%'
   OR process_key LIKE '%340634%'
   OR process_key LIKE '%340635%'
   OR process_key LIKE '%340636%'
   OR process_key LIKE '%340687%'
   OR process_key LIKE '%340695%'
   OR process_key LIKE '%340703%'
   OR process_key LIKE '%340710%'
   OR process_key LIKE '%340711%'
   OR process_key LIKE '%340746%'
   OR process_key LIKE '%340748%'
   OR process_key LIKE '%340762%'
   OR process_key LIKE '%340774%'
   OR process_key LIKE '%340775%'
   OR process_key LIKE '%340776%'
   OR process_key LIKE '%340777%'
   OR process_key LIKE '%340778%'
   OR process_key LIKE '%340780%'
   OR process_key LIKE '%340792%'
);
DELETE FROM oa_todo WHERE task_id IN ('2575088','2562524');
DELETE FROM oa_process_task WHERE instance_id IN (
    SELECT id FROM oa_process_instance WHERE process_key LIKE '%77777%'
   OR process_key LIKE '%340343%'
   OR process_key LIKE '%340350%'
   OR process_key LIKE '%340351%'
   OR process_key LIKE '%340359%'
   OR process_key LIKE '%340360%'
   OR process_key LIKE '%340367%'
   OR process_key LIKE '%340369%'
   OR process_key LIKE '%340382%'
   OR process_key LIKE '%340389%'
   OR process_key LIKE '%340396%'
   OR process_key LIKE '%340403%'
   OR process_key LIKE '%340410%'
   OR process_key LIKE '%340417%'
   OR process_key LIKE '%340431%'
   OR process_key LIKE '%340445%'
   OR process_key LIKE '%340459%'
   OR process_key LIKE '%340466%'
   OR process_key LIKE '%340474%'
   OR process_key LIKE '%340475%'
   OR process_key LIKE '%340480%'
   OR process_key LIKE '%340481%'
   OR process_key LIKE '%340483%'
   OR process_key LIKE '%340492%'
   OR process_key LIKE '%340494%'
   OR process_key LIKE '%340496%'
   OR process_key LIKE '%340503%'
   OR process_key LIKE '%340504%'
   OR process_key LIKE '%340516%'
   OR process_key LIKE '%340517%'
   OR process_key LIKE '%340531%'
   OR process_key LIKE '%340539%'
   OR process_key LIKE '%340548%'
   OR process_key LIKE '%340549%'
   OR process_key LIKE '%340556%'
   OR process_key LIKE '%340563%'
   OR process_key LIKE '%340567%'
   OR process_key LIKE '%340568%'
   OR process_key LIKE '%340569%'
   OR process_key LIKE '%340570%'
   OR process_key LIKE '%340598%'
   OR process_key LIKE '%340605%'
   OR process_key LIKE '%340606%'
   OR process_key LIKE '%340613%'
   OR process_key LIKE '%340620%'
   OR process_key LIKE '%340627%'
   OR process_key LIKE '%340634%'
   OR process_key LIKE '%340635%'
   OR process_key LIKE '%340636%'
   OR process_key LIKE '%340687%'
   OR process_key LIKE '%340695%'
   OR process_key LIKE '%340703%'
   OR process_key LIKE '%340710%'
   OR process_key LIKE '%340711%'
   OR process_key LIKE '%340746%'
   OR process_key LIKE '%340748%'
   OR process_key LIKE '%340762%'
   OR process_key LIKE '%340774%'
   OR process_key LIKE '%340775%'
   OR process_key LIKE '%340776%'
   OR process_key LIKE '%340777%'
   OR process_key LIKE '%340778%'
   OR process_key LIKE '%340780%'
   OR process_key LIKE '%340792%'
);
DELETE FROM oa_process_instance WHERE process_key LIKE '%77777%'
   OR process_key LIKE '%340343%'
   OR process_key LIKE '%340350%'
   OR process_key LIKE '%340351%'
   OR process_key LIKE '%340359%'
   OR process_key LIKE '%340360%'
   OR process_key LIKE '%340367%'
   OR process_key LIKE '%340369%'
   OR process_key LIKE '%340382%'
   OR process_key LIKE '%340389%'
   OR process_key LIKE '%340396%'
   OR process_key LIKE '%340403%'
   OR process_key LIKE '%340410%'
   OR process_key LIKE '%340417%'
   OR process_key LIKE '%340431%'
   OR process_key LIKE '%340445%'
   OR process_key LIKE '%340459%'
   OR process_key LIKE '%340466%'
   OR process_key LIKE '%340474%'
   OR process_key LIKE '%340475%'
   OR process_key LIKE '%340480%'
   OR process_key LIKE '%340481%'
   OR process_key LIKE '%340483%'
   OR process_key LIKE '%340492%'
   OR process_key LIKE '%340494%'
   OR process_key LIKE '%340496%'
   OR process_key LIKE '%340503%'
   OR process_key LIKE '%340504%'
   OR process_key LIKE '%340516%'
   OR process_key LIKE '%340517%'
   OR process_key LIKE '%340531%'
   OR process_key LIKE '%340539%'
   OR process_key LIKE '%340548%'
   OR process_key LIKE '%340549%'
   OR process_key LIKE '%340556%'
   OR process_key LIKE '%340563%'
   OR process_key LIKE '%340567%'
   OR process_key LIKE '%340568%'
   OR process_key LIKE '%340569%'
   OR process_key LIKE '%340570%'
   OR process_key LIKE '%340598%'
   OR process_key LIKE '%340605%'
   OR process_key LIKE '%340606%'
   OR process_key LIKE '%340613%'
   OR process_key LIKE '%340620%'
   OR process_key LIKE '%340627%'
   OR process_key LIKE '%340634%'
   OR process_key LIKE '%340635%'
   OR process_key LIKE '%340636%'
   OR process_key LIKE '%340687%'
   OR process_key LIKE '%340695%'
   OR process_key LIKE '%340703%'
   OR process_key LIKE '%340710%'
   OR process_key LIKE '%340711%'
   OR process_key LIKE '%340746%'
   OR process_key LIKE '%340748%'
   OR process_key LIKE '%340762%'
   OR process_key LIKE '%340774%'
   OR process_key LIKE '%340775%'
   OR process_key LIKE '%340776%'
   OR process_key LIKE '%340777%'
   OR process_key LIKE '%340778%'
   OR process_key LIKE '%340780%'
   OR process_key LIKE '%340792%';
DELETE FROM oa_process_definition WHERE process_key LIKE '%77777%'
   OR process_key LIKE '%340343%'
   OR process_key LIKE '%340350%'
   OR process_key LIKE '%340351%'
   OR process_key LIKE '%340359%'
   OR process_key LIKE '%340360%'
   OR process_key LIKE '%340367%'
   OR process_key LIKE '%340369%'
   OR process_key LIKE '%340382%'
   OR process_key LIKE '%340389%'
   OR process_key LIKE '%340396%'
   OR process_key LIKE '%340403%'
   OR process_key LIKE '%340410%'
   OR process_key LIKE '%340417%'
   OR process_key LIKE '%340431%'
   OR process_key LIKE '%340445%'
   OR process_key LIKE '%340459%'
   OR process_key LIKE '%340466%'
   OR process_key LIKE '%340474%'
   OR process_key LIKE '%340475%'
   OR process_key LIKE '%340480%'
   OR process_key LIKE '%340481%'
   OR process_key LIKE '%340483%'
   OR process_key LIKE '%340492%'
   OR process_key LIKE '%340494%'
   OR process_key LIKE '%340496%'
   OR process_key LIKE '%340503%'
   OR process_key LIKE '%340504%'
   OR process_key LIKE '%340516%'
   OR process_key LIKE '%340517%'
   OR process_key LIKE '%340531%'
   OR process_key LIKE '%340539%'
   OR process_key LIKE '%340548%'
   OR process_key LIKE '%340549%'
   OR process_key LIKE '%340556%'
   OR process_key LIKE '%340563%'
   OR process_key LIKE '%340567%'
   OR process_key LIKE '%340568%'
   OR process_key LIKE '%340569%'
   OR process_key LIKE '%340570%'
   OR process_key LIKE '%340598%'
   OR process_key LIKE '%340605%'
   OR process_key LIKE '%340606%'
   OR process_key LIKE '%340613%'
   OR process_key LIKE '%340620%'
   OR process_key LIKE '%340627%'
   OR process_key LIKE '%340634%'
   OR process_key LIKE '%340635%'
   OR process_key LIKE '%340636%'
   OR process_key LIKE '%340687%'
   OR process_key LIKE '%340695%'
   OR process_key LIKE '%340703%'
   OR process_key LIKE '%340710%'
   OR process_key LIKE '%340711%'
   OR process_key LIKE '%340746%'
   OR process_key LIKE '%340748%'
   OR process_key LIKE '%340762%'
   OR process_key LIKE '%340774%'
   OR process_key LIKE '%340775%'
   OR process_key LIKE '%340776%'
   OR process_key LIKE '%340777%'
   OR process_key LIKE '%340778%'
   OR process_key LIKE '%340780%'
   OR process_key LIKE '%340792%';

DELETE FROM sys_dict_data WHERE dict_type LIKE 'seal_project_%';
DELETE FROM sys_dict_type WHERE dict_type LIKE 'seal_project_%';

DELETE FROM biz_record WHERE module LIKE '/project/project%'
   OR module LIKE '/project/contract%'
   OR module LIKE '/project/apply%'
   OR module LIKE '/project/expense%'
   OR module LIKE '/project/payment%'
   OR module LIKE '/project/make%'
   OR module LIKE '/project/get%'
   OR module LIKE '/project/invoice%'
   OR module LIKE '/project/real%'
   OR module LIKE '/project/earnestMoney%'
   OR module LIKE '/project/earnestReturn%'
   OR module LIKE '/project/loan%'
   OR module LIKE '/project/repay%'
   OR module LIKE '/project/budget%'
   OR module LIKE '/project/fund%'
   OR module LIKE '/project/warehouse%'
   OR module LIKE '/project/goods%'
   OR module LIKE '/project/cost%'
   OR module LIKE '/project/onbus%'
   OR module LIKE '/project/labor%'
   OR module LIKE '/project/schedule%'
   OR module LIKE '/project/processWrite%'
   OR module LIKE '/project/quantities%'
   OR module LIKE '/project/exec%'
   OR module LIKE '/project/employee%'
   OR module LIKE '/project/mem%'
   OR module LIKE '/project/plane%'
   OR module LIKE '/project/type%'
   OR module LIKE '/project/history%';
DELETE FROM biz_record WHERE business_key IN ('2575088','2562524');
DELETE FROM biz_record
WHERE module LIKE 'workflow:%'
  AND (payload_json LIKE '%77777%'
   OR payload_json LIKE '%340343%'
   OR payload_json LIKE '%340350%'
   OR payload_json LIKE '%340351%'
   OR payload_json LIKE '%340359%'
   OR payload_json LIKE '%340360%'
   OR payload_json LIKE '%340367%'
   OR payload_json LIKE '%340369%'
   OR payload_json LIKE '%340382%'
   OR payload_json LIKE '%340389%'
   OR payload_json LIKE '%340396%'
   OR payload_json LIKE '%340403%'
   OR payload_json LIKE '%340410%'
   OR payload_json LIKE '%340417%'
   OR payload_json LIKE '%340431%'
   OR payload_json LIKE '%340445%'
   OR payload_json LIKE '%340459%'
   OR payload_json LIKE '%340466%'
   OR payload_json LIKE '%340474%'
   OR payload_json LIKE '%340475%'
   OR payload_json LIKE '%340480%'
   OR payload_json LIKE '%340481%'
   OR payload_json LIKE '%340483%'
   OR payload_json LIKE '%340492%'
   OR payload_json LIKE '%340494%'
   OR payload_json LIKE '%340496%'
   OR payload_json LIKE '%340503%'
   OR payload_json LIKE '%340504%'
   OR payload_json LIKE '%340516%'
   OR payload_json LIKE '%340517%'
   OR payload_json LIKE '%340531%'
   OR payload_json LIKE '%340539%'
   OR payload_json LIKE '%340548%'
   OR payload_json LIKE '%340549%'
   OR payload_json LIKE '%340556%'
   OR payload_json LIKE '%340563%'
   OR payload_json LIKE '%340567%'
   OR payload_json LIKE '%340568%'
   OR payload_json LIKE '%340569%'
   OR payload_json LIKE '%340570%'
   OR payload_json LIKE '%340598%'
   OR payload_json LIKE '%340605%'
   OR payload_json LIKE '%340606%'
   OR payload_json LIKE '%340613%'
   OR payload_json LIKE '%340620%'
   OR payload_json LIKE '%340627%'
   OR payload_json LIKE '%340634%'
   OR payload_json LIKE '%340635%'
   OR payload_json LIKE '%340636%'
   OR payload_json LIKE '%340687%'
   OR payload_json LIKE '%340695%'
   OR payload_json LIKE '%340703%'
   OR payload_json LIKE '%340710%'
   OR payload_json LIKE '%340711%'
   OR payload_json LIKE '%340746%'
   OR payload_json LIKE '%340748%'
   OR payload_json LIKE '%340762%'
   OR payload_json LIKE '%340774%'
   OR payload_json LIKE '%340775%'
   OR payload_json LIKE '%340776%'
   OR payload_json LIKE '%340777%'
   OR payload_json LIKE '%340778%'
   OR payload_json LIKE '%340780%'
   OR payload_json LIKE '%340792%');

DELETE FROM oa_project_member;
DELETE FROM oa_project;
DELETE FROM oa_contract_item;
DELETE FROM oa_contract;
DELETE FROM oa_purchase_apply_item;
DELETE FROM oa_purchase_apply;
DELETE FROM oa_purchase_payment;
DELETE FROM oa_expense_item;
DELETE FROM oa_expense_invoice;
DELETE FROM oa_expense;
DELETE FROM oa_loan_detail;
DELETE FROM oa_loan;
DELETE FROM oa_repayment;
DELETE FROM oa_budget_item;
DELETE FROM oa_budget;
DELETE FROM oa_fund_plan;
DELETE FROM oa_warehouse_in;
DELETE FROM oa_warehouse_out;
DELETE FROM oa_warehouse;
DELETE FROM oa_invoice;

DELETE FROM sys_module WHERE module_id IN (77777,340343,340350,340351,340359,340360,340367,340369,340382,340389,340396,340403,340410,340417,340431,340445,340459,340466,340474,340475,340480,340481,340483,340492,340494,340496,340503,340504,340516,340517,340531,340539,340548,340549,340556,340563,340567,340568,340569,340570,340598,340605,340606,340613,340620,340627,340634,340635,340636,340687,340695,340703,340710,340711,340746,340748,340762,340774,340775,340776,340777,340778,340780,340792);


SET FOREIGN_KEY_CHECKS = 1;
