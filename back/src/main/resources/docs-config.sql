-- Legacy configuration values remain readable for older clients.
INSERT INTO sys_config (
    config_id, config_name, config_key, config_value, config_type, create_time, remark
) SELECT
    100, '使用文档入口', 'sys.docs.enabled', 'true', 'Y', CURRENT_TIMESTAMP,
    '控制顶部“使用文档”入口是否显示，默认开启'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'sys.docs.enabled'
);

INSERT INTO sys_config (
    config_id, config_name, config_key, config_value, config_type, create_time, remark
) SELECT
    101, '使用文档标题', 'sys.docs.title', '使用文档', 'N', CURRENT_TIMESTAMP,
    '顶部文档入口及文档中心标题'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_config WHERE config_key = 'sys.docs.title'
);
