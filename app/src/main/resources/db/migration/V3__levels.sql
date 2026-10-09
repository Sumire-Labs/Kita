CREATE TABLE level_members (
    guild_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    xp BIGINT NOT NULL DEFAULT 0,
    chat_next_ms BIGINT NOT NULL DEFAULT 0,
    chat_last_ms BIGINT NOT NULL DEFAULT 0,
    chat_hash BINARY(32) NULL,
    voice_token BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (guild_id, user_id),
    INDEX guild_xp (guild_id, xp)
);
CREATE TABLE level_daily (
    guild_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    day DATE NOT NULL,
    xp BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (guild_id, user_id, day),
    INDEX guild_day (guild_id, day)
);
CREATE TABLE level_role_grants (
    guild_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (guild_id, user_id, role_id)
);
