-- Incremental customer and contract upgrade; preserves existing business data and role grants.
CREATE TABLE IF NOT EXISTS oa_crm_contract (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    customer_id BIGINT NOT NULL,
    opportunity_id BIGINT,
    parent_contract_id BIGINT,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    service_type VARCHAR(64) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    version_no INT NOT NULL DEFAULT 1,
    effective_version INT NOT NULL DEFAULT 0,
    revision INT NOT NULL DEFAULT 0,
    approval_user_id BIGINT,
    submit_user_id BIGINT,
    approval_comment VARCHAR(2000),
    terms_json TEXT NOT NULL,
    attachment TEXT,
    remark VARCHAR(2000),
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME,
    FOREIGN KEY (customer_id) REFERENCES oa_merchant(id)
);

CREATE TABLE IF NOT EXISTS oa_crm_contract_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    snapshot_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    submit_user_id BIGINT NOT NULL,
    approval_user_id BIGINT NOT NULL,
    approval_comment VARCHAR(2000),
    submit_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approval_time DATETIME,
    signed_date DATE,
    UNIQUE (contract_id, version_no),
    FOREIGN KEY (contract_id) REFERENCES oa_crm_contract(id)
);

CREATE TABLE IF NOT EXISTS oa_crm_contract_receipt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    term_key VARCHAR(64) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    receipt_date DATE NOT NULL,
    remark VARCHAR(1000),
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (contract_id) REFERENCES oa_crm_contract(id)
);

CREATE TABLE IF NOT EXISTS oa_crm_contract_reminder_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    task_id VARCHAR(180) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    create_by VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (contract_id) REFERENCES oa_crm_contract(id)
);

INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (340310, 1, 0, '客户与合同管理', 'CRM', '/crm', 'Layout', 'iconfont-xiaoshouloudou', 1, 0, '', 1, '', 0, '', '', '', 'noRedirect', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (340644, 1, 340310, '客户分析', 'CrmAnalysis', 'analysis', 'eos/crm/analysis/index', 'iconfont-chengbenfenxi-', 4, 0, 'project:analysis:list', 0, '', 0, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (341101, 1, 340310, '风险提醒', 'CrmRisks', 'risks', 'eos/crm/risk/index', 'iconfont-jinggao', 5, 0, 'project:risk:list', 0, '', 0, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (341200, 1, 340310, '合同管理', 'CrmContracts', 'contracts', 'Layout', 'iconfont-hetong', 3, 1, '', 0, '', 1, '', '', '', '/crm/contracts/list', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (341201, 1, 340310, '合同列表', 'CrmContractList', 'contracts/list', 'eos/crm/contract/index', 'iconfont-hetong', 3, 0, 'crm:contract:list', 0, '', 1, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (341202, 1, 341200, '合同审批', 'CrmContractApprovals', 'approvals', 'eos/crm/contract/approvals', 'iconfont-hetong', 2, 1, 'crm:approval:list', 0, '', 1, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (341203, 1, 341200, '收费条款', 'CrmContractFees', 'fees', 'eos/crm/contract/fees', 'iconfont-hetong', 3, 1, 'crm:fee:list', 0, '', 1, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (341204, 1, 341200, '合同版本', 'CrmContractVersions', 'versions', 'eos/crm/contract/versions', 'iconfont-hetong', 4, 1, 'crm:version:list', 0, '', 1, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, flow_conditions, no_cache, is_app, is_frame, query, redirect, status) VALUES (341205, 1, 341200, '合同提醒', 'CrmContractReminders', 'reminders', 'eos/crm/contract/reminders', 'iconfont-hetong', 5, 1, 'crm:reminder:list', 0, '', 1, '', '', '', '', '0')
ON DUPLICATE KEY UPDATE module_name = VALUES(module_name), parent_id = VALUES(parent_id), route_name = VALUES(route_name), path = VALUES(path), component = VALUES(component), icon = VALUES(icon), module_sort = VALUES(module_sort), hidden = VALUES(hidden), perms = VALUES(perms), has_flow = VALUES(has_flow), flow_conditions = VALUES(flow_conditions), no_cache = VALUES(no_cache), is_app = VALUES(is_app), is_frame = VALUES(is_frame), query = VALUES(query), redirect = VALUES(redirect), status = VALUES(status);
