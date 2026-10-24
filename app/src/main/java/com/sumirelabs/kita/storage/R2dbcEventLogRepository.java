package com.sumirelabs.kita.storage;

import com.sumirelabs.kita.eventlog.*;
import io.r2dbc.spi.ConnectionFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class R2dbcEventLogRepository implements EventLogRepository {
    private final Sql sql;
    private final R2dbcEventLogSnapshots snapshots;
    public R2dbcEventLogRepository(ConnectionFactory source) { sql = new Sql(source); snapshots = new R2dbcEventLogSnapshots(source); }
    public Mono<Boolean> save(MessageSnapshot snapshot, long limit) { return snapshots.save(snapshot, limit); }
    public Mono<MessageSnapshot> message(long guild, long id, long now) { return snapshots.message(guild, id, now); }
    public Flux<MessageSnapshot> messages(long guild, List<Long> ids, long now) { return snapshots.messages(guild, ids, now); }
    public Mono<Boolean> enqueue(LogEntry entry, long target, long expires) {
        String payload = entry.encode(); int bytes = payload.getBytes(StandardCharsets.UTF_8).length + 128;
        if (bytes > 512 * 1024) return Mono.just(false);
        String key = hash(entry.key());
        return sql.transaction(connection -> EventLogQuota.lock(connection, "queue", entry.guild())
                .then(EventLogQuota.fits(connection, "queue", entry.guild(), bytes, EventLogQuota.QUEUE_BUCKET, EventLogQuota.QUEUE_BUCKET)).flatMap(fits -> {
                    if (!fits) return Mono.just(false);
                    return Sql.update(connection, "INSERT IGNORE INTO eventlog_outbox (guild_id, event_key, source_channel, target_channel, bucket, payload, byte_size,"
                            + " available_ms, expires_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", entry.guild(), key, entry.channel(), target,
                            EventLogQuota.bucket(entry.guild()), payload, bytes, entry.time(), expires)
                            .flatMap(rows -> rows == 0 ? Mono.just(true) : EventLogQuota.adjust(connection, "queue", entry.guild(), bytes).thenReturn(true));
                }));
    }
    public Mono<List<Pending>> claim(long now) {
        return sql.transaction(connection -> Sql.rows(connection, "SELECT o.id, o.target_channel, o.payload FROM eventlog_outbox o WHERE o.delivered = 0"
                + " AND o.available_ms <= ? AND o.expires_ms > ? AND (o.lease_until_ms IS NULL OR o.lease_until_ms < ?)"
                + " AND NOT EXISTS (SELECT 1 FROM eventlog_outbox p WHERE p.guild_id=o.guild_id AND p.target_channel=o.target_channel"
                + " AND p.id<o.id AND p.delivered=0 AND p.expires_ms>?) ORDER BY o.id LIMIT 8 FOR UPDATE SKIP LOCKED",
                row -> new Pending(row.get("id", Long.class), row.get("target_channel", Long.class),
                        LogEntry.decode(row.get("payload", String.class), LogEntry.class), UUID.randomUUID().toString()), now, now, now, now).collectList()
                .flatMap(rows -> Flux.fromIterable(rows).concatMap(row -> Sql.update(connection,
                        "UPDATE eventlog_outbox SET lease_id = ?, lease_until_ms = ? WHERE id = ?", row.lease(), now + 60_000, row.id())).then(Mono.just(rows))));
    }
    public Mono<Void> complete(Pending pending) {
        return sql.transaction(connection -> EventLogQuota.lock(connection, "queue", pending.entry().guild())
                .then(Sql.rows(connection, "SELECT byte_size FROM eventlog_outbox WHERE id = ? AND lease_id = ? AND delivered = 0 FOR UPDATE",
                        row -> row.get("byte_size", Integer.class), pending.id(), pending.lease()).singleOrEmpty())
                .flatMap(bytes -> Sql.update(connection, "UPDATE eventlog_outbox SET delivered = 1, payload = '', byte_size = 0, lease_id = NULL, lease_until_ms = NULL"
                        + " WHERE id = ? AND lease_id = ?", pending.id(), pending.lease())
                        .then(EventLogQuota.adjust(connection, "queue", pending.entry().guild(), -bytes))));
    }
    public Mono<Void> retry(Pending pending, long available) {
        return sql.update("UPDATE eventlog_outbox SET lease_id = NULL, lease_until_ms = NULL, available_ms = ? WHERE id = ? AND lease_id = ?",
                available, pending.id(), pending.lease()).then();
    }
    public Mono<Void> purge(long guild) {
        return snapshots.purge(guild).then(sql.transaction(connection -> EventLogQuota.lock(connection, "queue", guild)
                .then(Sql.rows(connection, "SELECT COALESCE(SUM(byte_size),0) AS total FROM eventlog_outbox WHERE guild_id = ?",
                        row -> ((Number) row.get("total")).longValue(), guild).single())
                .flatMap(bytes -> Sql.update(connection, "DELETE FROM eventlog_outbox WHERE guild_id = ?", guild)
                        .then(EventLogQuota.adjust(connection, "queue", guild, -bytes)))));
    }
    public Mono<Void> cleanup(long now) {
        return Flux.range(0, 16).concatMap(bucket -> Mono.defer(() -> snapshots.prune(bucket, now))
                .expand(count -> count == 500 ? Mono.delay(java.time.Duration.ofMillis(10)).then(snapshots.prune(bucket, now)) : Mono.empty(), 1)
                .then(Mono.defer(() -> cleanQueue(bucket, now)).expand(count -> count == 500
                        ? Mono.delay(java.time.Duration.ofMillis(10)).then(cleanQueue(bucket, now)) : Mono.empty(), 1).then())).then();
    }
    private Mono<Integer> cleanQueue(int bucket, long now) {
        return sql.transaction(connection -> EventLogQuota.lock(connection, "queue", R2dbcEventLogSnapshots.representative(bucket))
                .then(Sql.rows(connection, "SELECT id, guild_id, byte_size FROM eventlog_outbox WHERE bucket = ? AND expires_ms <= ? LIMIT 500",
                        row -> new long[]{row.get("id", Long.class), row.get("guild_id", Long.class), row.get("byte_size", Integer.class)}, bucket, now).collectList())
                .flatMap(rows -> Flux.fromIterable(rows).concatMap(row -> Sql.update(connection, "DELETE FROM eventlog_outbox WHERE id = ?", row[0])
                        .then(EventLogQuota.adjust(connection, "queue", row[1], -row[2]))).then(Mono.just(rows.size()))));
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
}
