CREATE TABLE IF NOT EXISTS sys_junk_cleanup_history (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(64) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    result_json MEDIUMTEXT NOT NULL,
    released_bytes BIGINT NOT NULL DEFAULT 0,
    released_file_count BIGINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_permission_migration (
    migration_key VARCHAR(64) PRIMARY KEY,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

UPDATE sys_module SET component='Layout', path='specialFunctions', route_name='SystemSpecialFunctions', redirect='/specialFunctions/customerCleanup'
WHERE module_id=341510;

INSERT INTO sys_module(module_id,tenant_id,parent_id,module_name,route_name,path,component,icon,module_sort,hidden,perms,has_flow,no_cache,status)
SELECT 341511,1,341510,'客户数据清理','CustomerCleanup','customerCleanup','system/customerCleanup/index','iconfont-mokuai',1,0,'system:customerCleanup:list',0,1,'0'
WHERE NOT EXISTS(SELECT 1 FROM sys_module WHERE module_id=341511);
INSERT INTO sys_module(module_id,tenant_id,parent_id,module_name,route_name,path,component,icon,module_sort,hidden,perms,has_flow,no_cache,status)
SELECT 341512,1,341510,'垃圾数据清理','JunkCleanup','junkCleanup','system/junkCleanup/index','iconfont-mokuai',2,0,'system:junkCleanup:list',0,1,'0'
WHERE NOT EXISTS(SELECT 1 FROM sys_module WHERE module_id=341512);

INSERT INTO sys_role_module(role_id,module_id)
SELECT source.role_id,341511 FROM sys_role_module source
WHERE source.module_id=341510 AND NOT EXISTS(SELECT 1 FROM sys_permission_migration WHERE migration_key='special_functions_split_v1')
AND NOT EXISTS(SELECT 1 FROM sys_role_module existing WHERE existing.role_id=source.role_id AND existing.module_id=341511);
INSERT INTO sys_dept_module(dept_id,module_id)
SELECT source.dept_id,341511 FROM sys_dept_module source
WHERE source.module_id=341510 AND NOT EXISTS(SELECT 1 FROM sys_permission_migration WHERE migration_key='special_functions_split_v1')
AND NOT EXISTS(SELECT 1 FROM sys_dept_module existing WHERE existing.dept_id=source.dept_id AND existing.module_id=341511);
INSERT INTO sys_role_module_action(role_id,module_id,action_code)
SELECT source.role_id,341511,source.action_code FROM sys_role_module_action source
WHERE source.module_id=341510 AND NOT EXISTS(SELECT 1 FROM sys_permission_migration WHERE migration_key='special_functions_split_v1')
AND NOT EXISTS(SELECT 1 FROM sys_role_module_action existing WHERE existing.role_id=source.role_id AND existing.module_id=341511 AND existing.action_code=source.action_code);

INSERT INTO sys_module_action(module_id,action_code,action_name,builtin,status)
SELECT 341511,'cleanup','清理关联数据',0,'0' WHERE NOT EXISTS(SELECT 1 FROM sys_module_action WHERE module_id=341511 AND action_code='cleanup');
INSERT INTO sys_module_action(module_id,action_code,action_name,builtin,status)
SELECT 341511,'remove','彻底删除客户',0,'0' WHERE NOT EXISTS(SELECT 1 FROM sys_module_action WHERE module_id=341511 AND action_code='remove');
INSERT INTO sys_module_action(module_id,action_code,action_name,builtin,status)
SELECT 341512,'cleanup','清理垃圾文件',0,'0' WHERE NOT EXISTS(SELECT 1 FROM sys_module_action WHERE module_id=341512 AND action_code='cleanup');
INSERT INTO sys_module_action(module_id,action_code,action_name,builtin,status)
SELECT 341512,'access','访问垃圾数据清理',0,'0' WHERE NOT EXISTS(SELECT 1 FROM sys_module_action WHERE module_id=341512 AND action_code='access');
INSERT INTO sys_permission_migration(migration_key)
SELECT 'special_functions_split_v1' WHERE NOT EXISTS(SELECT 1 FROM sys_permission_migration WHERE migration_key='special_functions_split_v1');
