package com.sumirelabs.kita.storage;

import com.sumirelabs.kita.earthquake.EarthquakeNotice;
import com.sumirelabs.kita.earthquake.EarthquakeRepository;
import io.r2dbc.spi.ConnectionFactory;
import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class R2dbcEarthquakeRepository implements EarthquakeRepository {
    private static final String WHERE = " WHERE event_key = ? AND guild_id = ? AND channel_id = ?";
    private final Sql sql;
    public R2dbcEarthquakeRepository(ConnectionFactory source) { sql = new Sql(source); }
    @Override public Mono<Boolean> accept(EarthquakeNotice notice) {
        return sql.transaction(connection -> Sql.update(connection,
                "INSERT INTO earthquake_events (event_key, issued_ms, payload) VALUES (?, -1, '')"
                        + " ON DUPLICATE KEY UPDATE event_key = VALUES(event_key)", notice.key())
                .then(Sql.rows(connection, "SELECT payload FROM earthquake_events WHERE event_key = ? FOR UPDATE",
                            row -> row.get("payload", String.class), notice.key()).single()).flatMap(old -> {
                        if (!old.isEmpty() && !notice.supersedes(EarthquakeNotice.decode(old))) return Mono.just(false);
                        return Sql.update(connection, "UPDATE earthquake_events SET issued_ms = ?, payload = ? WHERE event_key = ?",
                                notice.issuedMs(), notice.encode(), notice.key()).thenReturn(true);
                }));
    }
    @Override public Flux<EarthquakeNotice> recent(Instant since) {
        return sql.rows("SELECT payload FROM earthquake_events WHERE issued_ms >= ?", row ->
                EarthquakeNotice.decode(row.get("payload", String.class)), since.toEpochMilli());
    }
    @Override public Mono<Delivery> delivery(String key, long guild, long channel) {
        return sql.one("SELECT message_id, notice_token, content_token FROM earthquake_deliveries" + WHERE,
                row -> new Delivery(row.get("message_id", Long.class), row.get("notice_token", String.class),
                        row.get("content_token", String.class)), key, guild, channel);
    }
    @Override public Mono<Lease> claim(EarthquakeNotice notice, long guild, long channel) {
        var lease = new Lease(notice.key(), guild, channel, UUID.randomUUID().toString());
        return sql.update("INSERT IGNORE INTO earthquake_deliveries (event_key, guild_id, channel_id) VALUES (?, ?, ?)",
                        notice.key(), guild, channel)
                .then(sql.update("UPDATE earthquake_deliveries SET lease_id = ?, leased_until = DATE_ADD(NOW(), INTERVAL 1 MINUTE)"
                        + WHERE + " AND notice_token <> ? AND (leased_until IS NULL OR leased_until < NOW())",
                        lease.owner(), notice.key(), guild, channel, notice.token()))
                .flatMap(rows -> rows == 1 ? Mono.just(lease) : Mono.empty());
    }
    @Override public Mono<Void> sent(Lease lease, EarthquakeNotice notice, long message) {
        return sql.update("UPDATE earthquake_deliveries SET message_id = ?, notice_token = ?, content_token = ?"
                        + WHERE + " AND lease_id = ?", message, notice.token(), notice.contentToken(), lease.event(),
                lease.guild(), lease.channel(), lease.owner()).then();
    }
    @Override public Mono<Void> release(Lease lease) {
        return sql.update("UPDATE earthquake_deliveries SET lease_id = NULL, leased_until = NULL" + WHERE + " AND lease_id = ?",
                lease.event(), lease.guild(), lease.channel(), lease.owner()).then();
    }
    @Override public Mono<Void> prune(Instant before) {
        return sql.update("DELETE FROM earthquake_events WHERE issued_ms < ?", before.toEpochMilli()).then();
    }
}
