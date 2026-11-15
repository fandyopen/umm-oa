-- Customer cleanup menu and audit table; does not delete business data.
CREATE TABLE IF NOT EXISTS sys_customer_cleanup_history (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(200) NOT NULL,
    operation VARCHAR(24) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(64) NOT NULL,
    backup_name VARCHAR(100) NOT NULL,
    result_json MEDIUMTEXT NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO sys_module(module_id,tenant_id,parent_id,module_name,route_name,path,component,icon,module_sort,hidden,perms,has_flow,no_cache,status)
SELECT 341510,1,340309,'特殊功能','SystemSpecialFunctions','specialFunctions','system/specialFunctions/index','iconfont-mokuai',100,0,'system:specialFunctions:list',0,1,'0'
WHERE NOT EXISTS(SELECT 1 FROM sys_module WHERE module_id=341510);
INSERT INTO sys_module_action(module_id,action_code,action_name,builtin,status)
SELECT 341510,'cleanup','清理关联数据',0,'0'
WHERE NOT EXISTS(SELECT 1 FROM sys_module_action WHERE module_id=341510 AND action_code='cleanup');
INSERT INTO sys_module_action(module_id,action_code,action_name,builtin,status)
SELECT 341510,'remove','彻底删除客户',0,'0'
WHERE NOT EXISTS(SELECT 1 FROM sys_module_action WHERE module_id=341510 AND action_code='remove');
