CREATE TABLE guild_settings (
    guild_id BIGINT NOT NULL PRIMARY KEY,
    settings LONGTEXT NOT NULL CHECK (JSON_VALID(settings)),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE tickets (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    support_role_id BIGINT NOT NULL,
    channel_id BIGINT NULL UNIQUE,
    subject VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL,
    assignee_id BIGINT NOT NULL DEFAULT 0,
    active_owner BIGINT AS (IF(status IN ('opening', 'open', 'closing'), owner_id, NULL)) PERSISTENT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    closed_at TIMESTAMP NULL,
    UNIQUE KEY one_active_ticket (guild_id, active_owner),
    INDEX guild_status (guild_id, status)
);
