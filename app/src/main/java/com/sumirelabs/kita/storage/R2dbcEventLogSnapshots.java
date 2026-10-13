package com.sumirelabs.kita.storage;

import com.sumirelabs.kita.eventlog.LogEntry;
import com.sumirelabs.kita.eventlog.MessageSnapshot;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

final class R2dbcEventLogSnapshots {
    private record Stored(long guild, long message, int bytes) {}
    private final Sql sql;
    R2dbcEventLogSnapshots(ConnectionFactory source) { sql = new Sql(source); }
    Mono<Boolean> save(MessageSnapshot snapshot, long limit) {
        String payload = LogEntry.encodeObject(snapshot); int bytes = payload.getBytes(StandardCharsets.UTF_8).length + 128;
        if (bytes > 64 * 1024) return Mono.just(false);
        return sql.transaction(connection -> EventLogQuota.lock(connection, "messages", snapshot.guild()).then(
                Sql.rows(connection, "SELECT payload, byte_size FROM eventlog_messages WHERE guild_id = ? AND message_id = ?",
                        row -> java.util.Map.entry(LogEntry.decode(row.get("payload", String.class), MessageSnapshot.class),
                                row.get("byte_size", Integer.class)), snapshot.guild(), snapshot.message()).singleOrEmpty())
                .defaultIfEmpty(java.util.Map.entry(MessageSnapshot.tombstone(0, 0, 0, 0), 0)).flatMap(old -> {
                    if (!snapshot.deleted() && (old.getKey().deleted() && old.getKey().guild() != 0 || old.getKey().version() > snapshot.version())) return Mono.just(false);
                    long delta = bytes - old.getValue();
                    return EventLogQuota.fits(connection, "messages", snapshot.guild(), delta, limit, EventLogQuota.MESSAGE_BUCKET)
                            .flatMap(fits -> fits ? Mono.just(true) : evict(connection, snapshot.guild(), limit, snapshot.message())
                                    .then(EventLogQuota.fits(connection, "messages", snapshot.guild(), delta, limit, EventLogQuota.MESSAGE_BUCKET)))
                            .flatMap(fits -> {
                                if (!fits) return Mono.just(false);
                                return Sql.update(connection, "INSERT INTO eventlog_messages (guild_id, message_id, channel_id, bucket, payload, byte_size, expires_ms)"
                                        + " VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE payload = VALUES(payload), byte_size = VALUES(byte_size), expires_ms = VALUES(expires_ms)",
                                        snapshot.guild(), snapshot.message(), snapshot.channel(), EventLogQuota.bucket(snapshot.guild()), payload, bytes, snapshot.expires())
                                        .then(EventLogQuota.adjust(connection, "messages", snapshot.guild(), delta)).thenReturn(true);
                            });
                }));
    }
    Mono<MessageSnapshot> message(long guild, long id, long now) {
        return sql.one("SELECT payload FROM eventlog_messages WHERE guild_id = ? AND message_id = ? AND expires_ms > ?",
                row -> LogEntry.decode(row.get("payload", String.class), MessageSnapshot.class), guild, id, now).filter(snapshot -> !snapshot.deleted());
    }
    Flux<MessageSnapshot> messages(long guild, List<Long> ids, long now) {
        if (ids.isEmpty()) return Flux.empty();
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        var args = new java.util.ArrayList<Object>(); args.add(guild); args.add(now); args.addAll(ids);
        return sql.rows("SELECT payload FROM eventlog_messages WHERE guild_id = ? AND expires_ms > ? AND message_id IN (" + placeholders + ")",
                row -> LogEntry.decode(row.get("payload", String.class), MessageSnapshot.class), args.toArray()).filter(snapshot -> !snapshot.deleted());
    }
    private Mono<Void> evict(Connection connection, long guild, long limit, long except) {
        return EventLogQuota.used(connection, "messages", guild).flatMap(used -> {
            boolean local = used >= limit - 32 * 1024;
            return Sql.rows(connection, "SELECT guild_id, message_id, byte_size FROM eventlog_messages WHERE "
                            + (local ? "guild_id = ?" : "bucket = ?") + " AND NOT (guild_id = ? AND message_id = ?) ORDER BY expires_ms LIMIT 200",
                    row -> new Stored(row.get("guild_id", Long.class), row.get("message_id", Long.class), row.get("byte_size", Integer.class)),
                    local ? guild : EventLogQuota.bucket(guild), guild, except).collectList().flatMap(rows -> remove(connection, rows));
        });
    }
    private Mono<Void> remove(Connection connection, List<Stored> rows) {
        return Flux.fromIterable(rows).concatMap(row -> Sql.update(connection, "DELETE FROM eventlog_messages WHERE guild_id = ? AND message_id = ?",
                        row.guild, row.message).then(EventLogQuota.adjust(connection, "messages", row.guild, -row.bytes))).then();
    }
    Mono<Integer> prune(int bucket, long now) {
        return sql.transaction(connection -> EventLogQuota.lock(connection, "messages", representative(bucket))
                .then(Sql.rows(connection, "SELECT guild_id, message_id, byte_size FROM eventlog_messages WHERE bucket = ? AND expires_ms <= ? LIMIT 500",
                        row -> new Stored(row.get("guild_id", Long.class), row.get("message_id", Long.class), row.get("byte_size", Integer.class)), bucket, now).collectList())
                .flatMap(rows -> remove(connection, rows).thenReturn(rows.size())));
    }
    static long representative(int bucket) { return bucket == 0 ? 16 : bucket; }
    Mono<Void> purge(long guild) {
        return sql.transaction(connection -> EventLogQuota.lock(connection, "messages", guild)
                .then(Sql.rows(connection, "SELECT COALESCE(SUM(byte_size),0) AS total FROM eventlog_messages WHERE guild_id = ?",
                        row -> ((Number) row.get("total")).longValue(), guild).single())
                .flatMap(bytes -> Sql.update(connection, "DELETE FROM eventlog_messages WHERE guild_id = ?", guild)
                        .then(EventLogQuota.adjust(connection, "messages", guild, -bytes))));
    }
}
