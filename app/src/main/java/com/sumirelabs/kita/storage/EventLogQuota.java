package com.sumirelabs.kita.storage;

import io.r2dbc.spi.Connection;
import reactor.core.publisher.Mono;

final class EventLogQuota {
    static final long MESSAGE_BUCKET = 128L * 1024 * 1024, QUEUE_BUCKET = 8L * 1024 * 1024;
    private EventLogQuota() {}
    static int bucket(long guild) { return Math.floorMod(Long.hashCode(guild), 16); }
    static Mono<Void> lock(Connection connection, String scope, long guild) {
        return Sql.update(connection, "INSERT INTO eventlog_usage (scope, guild_id, used_bytes) VALUES (?, ?, 0)"
                        + " ON DUPLICATE KEY UPDATE guild_id = VALUES(guild_id)", scope, -1L - bucket(guild))
                .then(Sql.update(connection, "INSERT IGNORE INTO eventlog_usage (scope, guild_id, used_bytes) VALUES (?, ?, 0)", scope, guild)).then();
    }
    static Mono<Long> used(Connection connection, String scope, long guild) {
        return Sql.rows(connection, "SELECT used_bytes FROM eventlog_usage WHERE scope = ? AND guild_id = ?",
                row -> row.get("used_bytes", Long.class), scope, guild).single();
    }
    static Mono<Boolean> fits(Connection connection, String scope, long guild, long change, long limit, long bucketLimit) {
        return used(connection, scope, -1L - bucket(guild)).flatMap(total -> used(connection, scope, guild)
                .map(local -> total + change <= bucketLimit && local + change <= limit));
    }
    static Mono<Void> adjust(Connection connection, String scope, long guild, long change) {
        return Sql.update(connection, "UPDATE eventlog_usage SET used_bytes = GREATEST(0, used_bytes + ?) WHERE scope = ? AND guild_id IN (?, ?)",
                change, scope, guild, -1L - bucket(guild)).then();
    }
}