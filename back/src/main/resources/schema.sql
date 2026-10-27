DROP TABLE IF EXISTS sys_bootstrap_marker;
DROP TABLE IF EXISTS oa_workflow_request;
DROP TABLE IF EXISTS oa_module_action_flow;
DROP TABLE IF EXISTS biz_record;
DROP TABLE IF EXISTS oa_form_submission;
DROP TABLE IF EXISTS oa_form_definition;
DROP TABLE IF EXISTS oa_process_design;
DROP TABLE IF EXISTS oa_merchant_account;
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
    email VARCHAR(128),
    position VARCHAR(100),
    remark VARCHAR(500)
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

