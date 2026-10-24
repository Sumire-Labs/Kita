package com.sumirelabs.kita.storage;

import com.sumirelabs.kita.tickets.TicketRecord;
import com.sumirelabs.kita.tickets.TicketRepository;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Row;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class R2dbcTicketRepository implements TicketRepository {
    private final Sql sql;
    public R2dbcTicketRepository(ConnectionFactory source) { sql = new Sql(source); }
    @Override public Mono<Boolean> reserve(long guild, long owner, String subject, long role) {
        return sql.update("INSERT IGNORE INTO tickets (guild_id, owner_id, subject, support_role_id, status) VALUES (?, ?, ?, ?, 'opening')",
                guild, owner, subject, role).map(rows -> rows == 1);
    }
    @Override public Mono<Void> activate(long guild, long owner, long channel) {
        return sql.update("UPDATE tickets SET channel_id = ?, status = 'open' WHERE guild_id = ? AND owner_id = ? AND status = 'opening'", channel, guild, owner).flatMap(rows -> rows == 1 ? Mono.empty() : Mono.error(new IllegalStateException("Ticket reservation was lost")));
    }
    @Override public Mono<Boolean> release(long guild, long owner) {
        return sql.update("DELETE FROM tickets WHERE guild_id = ? AND owner_id = ? AND status = 'opening'", guild, owner).map(rows -> rows == 1);
    }
    @Override public Mono<TicketRecord> find(long guild, long channel) {
        return sql.one("SELECT * FROM tickets WHERE guild_id = ? AND channel_id = ?", R2dbcTicketRepository::record, guild, channel);
    }
    @Override public Mono<Void> claim(long guild, long channel, long assignee) {
        return sql.update("UPDATE tickets SET assignee_id = ? WHERE guild_id = ? AND channel_id = ? AND status = 'open' AND assignee_id = 0", assignee, guild, channel)
                .flatMap(rows -> rows == 0 ? Mono.error(new IllegalArgumentException("このチケットは担当済みです。")) : Mono.empty());
    }
    @Override public Mono<Boolean> beginClose(long guild, long channel) {
        return sql.update("UPDATE tickets SET status = 'closing' WHERE guild_id = ? AND channel_id = ? AND status = 'open'", guild, channel).map(rows -> rows == 1);
    }
    @Override public Mono<Void> abortClose(long guild, long channel) {
        return sql.update("UPDATE tickets SET status = 'open' WHERE guild_id = ? AND channel_id = ? AND status = 'closing'", guild, channel).then();
    }
    @Override public Flux<TicketRecord> pending() {
        return sql.rows("SELECT * FROM tickets WHERE status IN ('opening', 'closing') AND updated_at < CURRENT_TIMESTAMP - INTERVAL 5 MINUTE", R2dbcTicketRepository::record);
    }
    @Override public Mono<Void> close(long guild, long channel) {
        return sql.update("UPDATE tickets SET status = 'closed', closed_at = CURRENT_TIMESTAMP WHERE guild_id = ? AND channel_id = ?", guild, channel).then();
    }
    private static TicketRecord record(Row row) {
        Long channel = row.get("channel_id", Long.class);
        return new TicketRecord(row.get("guild_id", Long.class), channel == null ? 0 : channel, row.get("owner_id", Long.class),
                row.get("support_role_id", Long.class), row.get("subject", String.class), row.get("status", String.class),
                row.get("assignee_id", Long.class), row.get("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
    }
}
