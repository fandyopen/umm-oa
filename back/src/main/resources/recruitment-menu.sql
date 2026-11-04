-- Insert once: module and button edits remain authoritative on subsequent restarts.
INSERT INTO sys_module (module_id, tenant_id, parent_id, module_name, route_name, path, component, icon, module_sort, hidden, perms, has_flow, no_cache, status)
SELECT 341300, 1, 0, '招聘管理', 'Recruitment', '/recruitment', 'Layout', 'iconfont-zhiwei', 4, 0, '', 0, 1, '0'
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

-- Replace known default icons without overwriting administrator-customized icons.
UPDATE sys_module SET icon = 'iconfont-zhiwei' WHERE module_id = 341300
  AND (icon IS NULL OR icon = '' OR icon IN ('team', 'iconfont-tuanduiguanli', 'iconfont-zhiwei1'));
UPDATE sys_module SET icon = 'iconfont-zhiwei' WHERE module_id = 341301 AND (icon IS NULL OR icon = '' OR icon = 'solution');
UPDATE sys_module SET icon = 'iconfont-tuandui' WHERE module_id = 341302 AND (icon IS NULL OR icon = '' OR icon = 'contacts');
UPDATE sys_module SET icon = 'iconfont-zixiangmukanban' WHERE module_id = 341303 AND (icon IS NULL OR icon = '' OR icon = 'branches');
