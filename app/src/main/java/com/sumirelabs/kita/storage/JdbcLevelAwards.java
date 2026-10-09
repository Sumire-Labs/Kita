package com.sumirelabs.kita.storage;

import com.sumirelabs.kita.levels.LevelCurve;
import com.sumirelabs.kita.levels.LevelPeriod;
import com.sumirelabs.kita.levels.LevelsRepository;
import java.sql.Connection;
import java.time.Instant;
import java.util.Arrays;
import javax.sql.DataSource;

final class JdbcLevelAwards {
    private final DataSource source;
    JdbcLevelAwards(DataSource source) { this.source = source; }
    LevelsRepository.Award award(long guild, long user, LevelsRepository.Source kind, int amount,
                                 Instant now, int cooldown, byte[] hash, long token) {
        if (amount <= 0 || amount > 1_000_000) throw new IllegalArgumentException("Invalid XP amount");
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var insert = connection.prepareStatement("INSERT INTO level_members (guild_id, user_id) VALUES (?, ?)"
                        + " ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)")) {
                    insert.setLong(1, guild); insert.setLong(2, user); insert.executeUpdate();
                }
                long before;
                try (var query = connection.prepareStatement("SELECT xp, chat_next_ms, chat_last_ms, chat_hash, voice_token"
                        + " FROM level_members WHERE guild_id = ? AND user_id = ? FOR UPDATE")) {
                    query.setLong(1, guild); query.setLong(2, user);
                    try (var row = query.executeQuery()) {
                        row.next(); before = row.getLong(1);
                        boolean blocked = kind == LevelsRepository.Source.CHAT
                                ? now.toEpochMilli() < row.getLong(2) || (Arrays.equals(hash, row.getBytes(4)) && now.toEpochMilli() - row.getLong(3) < 300_000)
                                : token <= row.getLong(5);
                        if (blocked || before >= LevelCurve.MAX_XP) { connection.commit(); return new LevelsRepository.Award(false, before, before); }
                    }
                }
                long after = Math.min(LevelCurve.MAX_XP, before + amount);
                String fields = kind == LevelsRepository.Source.CHAT ? "chat_next_ms = ?, chat_last_ms = ?, chat_hash = ?" : "voice_token = ?";
                try (var update = connection.prepareStatement("UPDATE level_members SET xp = ?, " + fields + " WHERE guild_id = ? AND user_id = ?")) {
                    int index = 1; update.setLong(index++, after);
                    if (kind == LevelsRepository.Source.CHAT) {
                        update.setLong(index++, now.toEpochMilli() + cooldown * 1000L); update.setLong(index++, now.toEpochMilli()); update.setBytes(index++, hash);
                    } else update.setLong(index++, token);
                    update.setLong(index++, guild); update.setLong(index, user); update.executeUpdate();
                }
                daily(connection, guild, user, now, after - before);
                connection.commit(); return new LevelsRepository.Award(true, before, after);
            } catch (Exception error) { connection.rollback(); throw error; }
        } catch (Exception error) { throw new IllegalStateException("Cannot award XP", error); }
    }
    private static void daily(Connection connection, long guild, long user, Instant now, long amount) throws Exception {
        try (var statement = connection.prepareStatement("INSERT INTO level_daily (guild_id, user_id, day, xp) VALUES (?, ?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE xp = xp + VALUES(xp)")) {
            statement.setLong(1, guild); statement.setLong(2, user);
            statement.setDate(3, java.sql.Date.valueOf(now.atZone(LevelPeriod.ZONE).toLocalDate()));
            statement.setLong(4, amount); statement.executeUpdate();
        }
    }
}
