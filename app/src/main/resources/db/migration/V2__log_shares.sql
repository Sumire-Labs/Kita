CREATE TABLE log_shares (
    guild_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    item_key VARCHAR(80) NOT NULL,
    url VARCHAR(255) NULL,
    reply_id BIGINT NULL,
    leased_until DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (guild_id, message_id, item_key)
);
