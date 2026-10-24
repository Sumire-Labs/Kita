CREATE TABLE earthquake_events (
    event_key VARCHAR(160) NOT NULL PRIMARY KEY,
    issued_ms BIGINT NOT NULL,
    payload LONGTEXT NOT NULL,
    INDEX earthquake_recent (issued_ms)
);
CREATE TABLE earthquake_deliveries (
    event_key VARCHAR(160) NOT NULL,
    guild_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL DEFAULT 0,
    notice_token CHAR(64) NOT NULL DEFAULT '',
    content_token CHAR(64) NOT NULL DEFAULT '',
    lease_id CHAR(36),
    leased_until DATETIME(3),
    PRIMARY KEY (event_key, guild_id, channel_id),
    CONSTRAINT earthquake_event_fk FOREIGN KEY (event_key) REFERENCES earthquake_events(event_key) ON DELETE CASCADE
);