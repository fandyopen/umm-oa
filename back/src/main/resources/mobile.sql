CREATE TABLE IF NOT EXISTS oa_mobile_message_read (
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    message_key VARCHAR(64) NOT NULL,
    read_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, user_id, message_key)
);
