package com.sumirelabs.kita.storage;

import com.sumirelabs.kita.tickets.TicketRecord;
import com.sumirelabs.kita.tickets.TicketRepository;
import java.util.Optional;
import javax.sql.DataSource;

public final class JdbcTicketRepository implements TicketRepository {
    private final DataSource source;
    public JdbcTicketRepository(DataSource source) { this.source = source; }

    @Override public boolean reserve(long guildId, long ownerId, String subject, long supportRoleId) {
        return execute("INSERT IGNORE INTO tickets (guild_id, owner_id, subject, support_role_id, status) VALUES (?, ?, ?, ?, 'opening')",
                guildId, ownerId, subject, supportRoleId) == 1;
    }

    @Override public void activate(long guildId, long ownerId, long channelId) {
        execute("UPDATE tickets SET channel_id = ?, status = 'open' WHERE guild_id = ? AND owner_id = ? AND status = 'opening'",
                channelId, guildId, ownerId);
    }

    @Override public void release(long guildId, long ownerId) {
        execute("DELETE FROM tickets WHERE guild_id = ? AND owner_id = ? AND status = 'opening'", guildId, ownerId);
    }

    @Override public Optional<TicketRecord> find(long guildId, long channelId) {
        try (var connection = source.getConnection(); var statement = connection.prepareStatement(
                "SELECT * FROM tickets WHERE guild_id = ? AND channel_id = ?")) {
            statement.setLong(1, guildId);
            statement.setLong(2, channelId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                return Optional.of(new TicketRecord(guildId, channelId, rows.getLong("owner_id"),
                        rows.getLong("support_role_id"), rows.getString("subject"), rows.getString("status"), rows.getLong("assignee_id"),
                        rows.getTimestamp("created_at").toInstant()));
            }
        } catch (Exception error) { throw new IllegalStateException("Cannot read ticket", error); }
    }

    @Override public void claim(long guildId, long channelId, long assigneeId) {
        if (execute("UPDATE tickets SET assignee_id = ? WHERE guild_id = ? AND channel_id = ? AND status = 'open' AND assignee_id = 0",
                assigneeId, guildId, channelId) == 0) throw new IllegalArgumentException("このチケットは担当済みです。");
    }

    @Override public void close(long guildId, long channelId) {
        execute("UPDATE tickets SET status = 'closed', closed_at = CURRENT_TIMESTAMP WHERE guild_id = ? AND channel_id = ?",
                guildId, channelId);
    }

    @Override public boolean beginClose(long guildId, long channelId) {
        return execute("UPDATE tickets SET status = 'closing' WHERE guild_id = ? AND channel_id = ? AND status = 'open'",
                guildId, channelId) == 1;
    }

    @Override public void abortClose(long guildId, long channelId) {
        execute("UPDATE tickets SET status = 'open' WHERE guild_id = ? AND channel_id = ? AND status = 'closing'",
                guildId, channelId);
    }

    @Override public java.util.List<TicketRecord> pending() {
        try (var connection = source.getConnection(); var query = connection.prepareStatement(
                "SELECT * FROM tickets WHERE status IN ('opening', 'closing') AND updated_at < CURRENT_TIMESTAMP - INTERVAL 5 MINUTE");
                var rows = query.executeQuery()) {
            var records = new java.util.ArrayList<TicketRecord>();
            while (rows.next()) records.add(new TicketRecord(rows.getLong("guild_id"), rows.getLong("channel_id"),
                    rows.getLong("owner_id"), rows.getLong("support_role_id"), rows.getString("subject"), rows.getString("status"),
                    rows.getLong("assignee_id"), rows.getTimestamp("created_at").toInstant()));
            return java.util.List.copyOf(records);
        } catch (Exception error) { throw new IllegalStateException("Cannot read pending tickets", error); }
    }

    private int execute(String sql, Object... arguments) {
        try (var connection = source.getConnection(); var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < arguments.length; index++) statement.setObject(index + 1, arguments[index]);
            return statement.executeUpdate();
        } catch (Exception error) { throw new IllegalStateException("Cannot write ticket", error); }
    }
}
