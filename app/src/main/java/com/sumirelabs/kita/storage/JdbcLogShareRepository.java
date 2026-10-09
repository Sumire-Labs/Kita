package com.sumirelabs.kita.storage;

import com.sumirelabs.kita.logshare.LogShareRepository;
import java.sql.PreparedStatement;
import java.util.Optional;
import javax.sql.DataSource;

public final class JdbcLogShareRepository implements LogShareRepository {
    private static final String WHERE = " WHERE guild_id = ? AND message_id = ? AND item_key = ?";
    private final DataSource source;
    public JdbcLogShareRepository(DataSource source) { this.source = source; }
    @Override public Optional<Share> find(Key key) {
        try (var connection = source.getConnection(); var query = connection.prepareStatement("SELECT url, reply_id FROM log_shares" + WHERE)) {
            bind(query, key, 1);
            try (var rows = query.executeQuery()) { return rows.next() ? Optional.of(new Share(rows.getString(1), rows.getLong(2))) : Optional.empty(); }
        } catch (Exception error) { throw new IllegalStateException("Cannot read log share", error); }
    }
    @Override public boolean claim(Key key) {
        try (var connection = source.getConnection()) {
            try (var insert = connection.prepareStatement("INSERT IGNORE INTO log_shares (guild_id, message_id, item_key) VALUES (?, ?, ?)")) {
                bind(insert, key, 1); insert.executeUpdate();
            }
            try (var update = connection.prepareStatement("UPDATE log_shares SET leased_until = DATE_ADD(NOW(), INTERVAL 5 MINUTE)"
                    + WHERE + " AND reply_id IS NULL AND (leased_until IS NULL OR leased_until < NOW())")) {
                bind(update, key, 1); return update.executeUpdate() == 1;
            }
        } catch (Exception error) { throw new IllegalStateException("Cannot claim log share", error); }
    }
    @Override public void uploaded(Key key, String url) { update(key, "url", url); }
    @Override public void replied(Key key, long replyId) { update(key, "reply_id", replyId); }
    @Override public void release(Key key) { update(key, "leased_until", null); }
    private void update(Key key, String column, Object value) {
        try (var connection = source.getConnection(); var statement = connection.prepareStatement("UPDATE log_shares SET " + column + " = ?" + WHERE)) {
            statement.setObject(1, value); bind(statement, key, 2); statement.executeUpdate();
        } catch (Exception error) { throw new IllegalStateException("Cannot update log share", error); }
    }
    private static void bind(PreparedStatement statement, Key key, int start) throws Exception {
        statement.setLong(start, key.guildId()); statement.setLong(start + 1, key.messageId()); statement.setString(start + 2, key.item());
    }
}
