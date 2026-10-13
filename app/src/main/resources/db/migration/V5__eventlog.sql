CREATE TABLE eventlog_usage (
    scope VARCHAR(16) NOT NULL,
    guild_id BIGINT NOT NULL,
    used_bytes BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY(scope, guild_id)
);
CREATE TABLE eventlog_messages (
    guild_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    bucket INT NOT NULL,
    payload LONGTEXT NOT NULL,
    byte_size INT NOT NULL,
    expires_ms BIGINT NOT NULL,
    PRIMARY KEY(guild_id, message_id),
    INDEX eventlog_message_expiry(bucket, expires_ms)
);
CREATE TABLE eventlog_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    event_key CHAR(64) NOT NULL,
    source_channel BIGINT NOT NULL,
    target_channel BIGINT NOT NULL,
    bucket INT NOT NULL,
    payload LONGTEXT NOT NULL,
    byte_size INT NOT NULL,
    available_ms BIGINT NOT NULL,
    expires_ms BIGINT NOT NULL,
    lease_id CHAR(36),
    lease_until_ms BIGINT,
    delivered BOOLEAN NOT NULL DEFAULT 0,
    UNIQUE KEY eventlog_event(guild_id, event_key),
    INDEX eventlog_queue(delivered, available_ms),
    INDEX eventlog_fifo(guild_id, target_channel, delivered, id),
    INDEX eventlog_queue_expiry(bucket, expires_ms)
);
