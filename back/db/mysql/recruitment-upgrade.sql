-- Existing MySQL 8 databases: recruitment upgrade; preserves business data.
CREATE TABLE IF NOT EXISTS oa_recruit_job (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    customer_id BIGINT NOT NULL,
    contract_id BIGINT,
    owner_user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    code VARCHAR(64) NOT NULL UNIQUE,
    city VARCHAR(100),
    industry VARCHAR(100),
    experience VARCHAR(100),
    salary_min DECIMAL(18,2) NOT NULL DEFAULT 0,
    salary_max DECIMAL(18,2) NOT NULL DEFAULT 0,
    headcount INT NOT NULL DEFAULT 1,
    requirements TEXT,
    fee_terms VARCHAR(2000),
    collaborators_json TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    demand_status VARCHAR(32) NOT NULL DEFAULT 'draft',
    target_date DATE,
    cycle_days INT NOT NULL DEFAULT 30,
    min_recommendations INT NOT NULL DEFAULT 3,
    feedback_days INT NOT NULL DEFAULT 3,
    version_no INT NOT NULL DEFAULT 1,
    revision INT NOT NULL DEFAULT 0,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES oa_merchant(id),
    FOREIGN KEY (contract_id) REFERENCES oa_crm_contract(id)
);
CREATE TABLE IF NOT EXISTS oa_recruit_talent (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    owner_user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(40),
    email VARCHAR(200),
    city VARCHAR(100),
    industry VARCHAR(100),
    skills VARCHAR(1000),
    experience_years INT NOT NULL DEFAULT 0,
    expected_salary DECIMAL(18,2) NOT NULL DEFAULT 0,
    source VARCHAR(100),
    remark VARCHAR(2000),
    tags_json TEXT,
    collaborators_json TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    merged_into BIGINT,
    last_contact_time DATETIME,
    revision INT NOT NULL DEFAULT 0,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS oa_recruit_application (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    job_id BIGINT NOT NULL,
    talent_id BIGINT NOT NULL,
    consultant_id BIGINT NOT NULL,
    stage VARCHAR(32) NOT NULL DEFAULT 'draft',
    recommendation VARCHAR(2000),
    submitted_date DATE,
    feedback_due_date DATE,
    feedback_status VARCHAR(32),
    offer_status VARCHAR(32),
    offer_salary DECIMAL(18,2),
    offer_date DATE,
    offer_attachment VARCHAR(500),
    expected_entry_date DATE,
    actual_entry_date DATE,
    entry_attachment VARCHAR(500),
    guarantee_days INT,
    guarantee_term_key VARCHAR(64),
    guarantee_snapshot_json TEXT,
    guarantee_start DATE,
    guarantee_end DATE,
    guarantee_status VARCHAR(32),
    revision INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (job_id, talent_id),
    FOREIGN KEY (job_id) REFERENCES oa_recruit_job(id),
    FOREIGN KEY (talent_id) REFERENCES oa_recruit_talent(id)
);
CREATE TABLE IF NOT EXISTS oa_recruit_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type VARCHAR(32) NOT NULL,
    entity_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    content VARCHAR(4000),
    payload_json TEXT,
    operator_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS oa_recruit_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    snapshot_json TEXT NOT NULL,
    reason VARCHAR(2000),
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (job_id, version_no)
);
CREATE TABLE IF NOT EXISTS oa_recruit_approval (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_request_id BIGINT NOT NULL UNIQUE,
    entity_type VARCHAR(32) NOT NULL,
    entity_id BIGINT NOT NULL,
    related_id BIGINT,
    action_code VARCHAR(32) NOT NULL,
    revision INT NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    instance_id VARCHAR(128),
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS oa_recruit_resume (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    talent_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    file_key VARCHAR(100) NOT NULL UNIQUE,
    original_name VARCHAR(200) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (talent_id, version_no),
    FOREIGN KEY (talent_id) REFERENCES oa_recruit_talent(id)
);
CREATE TABLE IF NOT EXISTS oa_recruit_interview (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    round_no INT NOT NULL,
    interview_time DATETIME NOT NULL,
    location VARCHAR(500),
    interviewer VARCHAR(200),
    status VARCHAR(32) NOT NULL DEFAULT 'scheduled',
    notified INT NOT NULL DEFAULT 0,
    evaluation VARCHAR(2000),
    result VARCHAR(32),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (application_id) REFERENCES oa_recruit_application(id)
);
CREATE TABLE IF NOT EXISTS oa_recruit_exception (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    exception_type VARCHAR(64) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'open',
    result VARCHAR(2000),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    close_time DATETIME,
    FOREIGN KEY (application_id) REFERENCES oa_recruit_application(id)
);
CREATE TABLE IF NOT EXISTS oa_recruit_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    talent_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'todo',
    result VARCHAR(2000),
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    complete_time DATETIME
);
CREATE TABLE IF NOT EXISTS oa_recruit_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL DEFAULT 'active'
);
CREATE TABLE IF NOT EXISTS oa_recruit_search (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    query_json TEXT NOT NULL,
    UNIQUE (user_id, name)
);
CREATE TABLE IF NOT EXISTS oa_recruit_attachment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    file_key VARCHAR(100) NOT NULL UNIQUE,
    original_name VARCHAR(200) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (application_id) REFERENCES oa_recruit_application(id)
);

CREATE TABLE IF NOT EXISTS oa_recruit_dispute (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    talent_id BIGINT NOT NULL,
    proposed_owner_id BIGINT NOT NULL,
    handler_user_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'open',
    result VARCHAR(4000),
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    close_time DATETIME,
    FOREIGN KEY (talent_id) REFERENCES oa_recruit_talent(id)
);
CREATE TABLE IF NOT EXISTS oa_recruit_replacement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_application_id BIGINT NOT NULL,
    replacement_application_id BIGINT NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    complete_time DATETIME,
    FOREIGN KEY (original_application_id) REFERENCES oa_recruit_application(id),
    FOREIGN KEY (replacement_application_id) REFERENCES oa_recruit_application(id)
);
CREATE TABLE IF NOT EXISTS oa_recruit_guarantee_handling (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    operation VARCHAR(32) NOT NULL,
    event_date DATE NOT NULL,
    amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    attachment_id BIGINT,
    reason VARCHAR(2000) NOT NULL,
    operator_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (application_id) REFERENCES oa_recruit_application(id)
);

-- Insert once: module and button edits remain authoritative on subsequent restarts.
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, no_cache, status)
SELECT 341300, 1, 0, '招聘管理', 'Recruitment', '/recruitment', 'Layout', 'iconfont-tuanduiguanli', 4, 0, '', 0, 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341300);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, no_cache, status)
SELECT 341301, 1, 341300, '职位管理', 'RecruitmentJobs', 'jobs', 'eos/recruitment/jobs/index', 'iconfont-zhiwei', 1, 0, 'recruit:job:list', 0, 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341301);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, no_cache, status)
SELECT 341302, 1, 341300, '人才库', 'RecruitmentTalents', 'talents', 'eos/recruitment/talents/index', 'iconfont-tuandui', 2, 0, 'recruit:talent:list', 0, 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341302);
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, no_cache, status)
SELECT 341303, 1, 341300, '招聘流程', 'RecruitmentPipeline', 'pipeline', 'eos/recruitment/pipeline/index', 'iconfont-zixiangmukanban', 3, 0, 'recruit:pipeline:list', 0, 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id = 341303);

-- Repair legacy Ant icon names without overwriting administrator-customized icons.
UPDATE sys_module SET icon = 'iconfont-tuanduiguanli' WHERE module_id = 341300 AND (icon IS NULL OR icon = '' OR icon = 'team');
UPDATE sys_module SET icon = 'iconfont-zhiwei' WHERE module_id = 341301 AND (icon IS NULL OR icon = '' OR icon = 'solution');
UPDATE sys_module SET icon = 'iconfont-tuandui' WHERE module_id = 341302 AND (icon IS NULL OR icon = '' OR icon = 'contacts');
UPDATE sys_module SET icon = 'iconfont-zixiangmukanban' WHERE module_id = 341303 AND (icon IS NULL OR icon = '' OR icon = 'branches');
