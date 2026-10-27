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
