CREATE TABLE IF NOT EXISTS sys_data_table_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    table_name VARCHAR(128) NOT NULL,
    action VARCHAR(16) NOT NULL,
    record_key TEXT NOT NULL,
    before_data MEDIUMTEXT,
    after_data MEDIUMTEXT,
    reason VARCHAR(500) NOT NULL,
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(64) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO sys_module (
    module_id,tenant_id,parent_id,module_name,route_name,path,component,icon,module_sort,
    hidden,perms,has_flow,flow_conditions,no_cache,is_app,is_frame,query,redirect,status
) SELECT 341500,t.tenant_id,340309,'数据表管理','DataTableManage','dataTables','system/dataTable/index',
    'iconfont-mokuai',99,0,'system:dataTable:list',0,'',0,'','','','','0'
FROM sys_tenant t WHERE t.tenant_id=1
AND NOT EXISTS (SELECT 1 FROM sys_module WHERE module_id=341500);
