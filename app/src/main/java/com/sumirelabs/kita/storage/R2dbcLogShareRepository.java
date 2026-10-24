package com.sumirelabs.kita.storage;

import com.sumirelabs.kita.logshare.LogShareRepository;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Mono;

public final class R2dbcLogShareRepository implements LogShareRepository {
    private static final String WHERE = " WHERE guild_id = ? AND message_id = ? AND item_key = ?";
    private final Sql sql;
    public R2dbcLogShareRepository(ConnectionFactory source) { sql = new Sql(source); }
    @Override public Mono<Share> find(Key key) {
        return sql.one("SELECT url, reply_id FROM log_shares" + WHERE, row -> {
            Long reply = row.get("reply_id", Long.class); return new Share(row.get("url", String.class), reply == null ? 0 : reply);
        }, key.guildId(), key.messageId(), key.item());
    }
    @Override public Mono<Boolean> claim(Key key) {
        return sql.update("INSERT IGNORE INTO log_shares (guild_id, message_id, item_key) VALUES (?, ?, ?)", key.guildId(), key.messageId(), key.item())
                .then(sql.update("UPDATE log_shares SET leased_until = DATE_ADD(NOW(), INTERVAL 5 MINUTE)" + WHERE
                        + " AND reply_id IS NULL AND (leased_until IS NULL OR leased_until < NOW())", key.guildId(), key.messageId(), key.item()))
                .map(rows -> rows == 1);
    }
    @Override public Mono<Void> uploaded(Key key, String url) { return update(key, "url", url); }
    @Override public Mono<Void> replied(Key key, long reply) { return update(key, "reply_id", reply); }
    @Override public Mono<Void> release(Key key) { return update(key, "leased_until", null); }
    private Mono<Void> update(Key key, String field, Object value) {
        return sql.update("UPDATE log_shares SET " + field + " = ?" + WHERE, value, key.guildId(), key.messageId(), key.item()).then();
    }
}
