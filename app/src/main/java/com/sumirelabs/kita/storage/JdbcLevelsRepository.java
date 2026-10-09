package com.sumirelabs.kita.storage;

import com.sumirelabs.kita.levels.LevelPeriod;
import com.sumirelabs.kita.levels.LevelsRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;

public final class JdbcLevelsRepository implements LevelsRepository {
    private final DataSource source;
    private final JdbcLevelAwards awards;
    public JdbcLevelsRepository(DataSource source) { this.source = source; awards = new JdbcLevelAwards(source); }
    @Override public Award award(long guild, long user, Source kind, int amount, Instant now, int cooldown, byte[] hash, long voiceToken) {
        return awards.award(guild, user, kind, amount, now, cooldown, hash, voiceToken);
    }
    @Override public Profile profile(long guild, long user) {
        try (var connection = source.getConnection(); var query = connection.prepareStatement(
                "SELECT profile_score.xp, 1 + (SELECT COUNT(*) FROM level_members WHERE guild_id = ? AND xp > profile_score.xp)"
                + " FROM (SELECT COALESCE(MAX(xp), 0) AS xp FROM level_members WHERE guild_id = ? AND user_id = ?) profile_score")) {
            query.setLong(1, guild); query.setLong(2, guild); query.setLong(3, user);
            try (var rows = query.executeQuery()) { rows.next(); return new Profile(rows.getLong(1), rows.getLong(2)); }
        } catch (Exception error) { throw new IllegalStateException("Cannot read level profile", error); }
    }
    @Override public List<Entry> leaderboard(long guild, LevelPeriod period, Instant now, int page) {
        if (page < 0 || page > 10_000) throw new IllegalArgumentException("ページが範囲外です。");
        String base = period == LevelPeriod.TOTAL ? "SELECT user_id, xp FROM level_members WHERE guild_id = ? AND xp > 0"
                : "SELECT user_id, SUM(xp) AS xp FROM level_daily WHERE guild_id = ? AND day >= ? AND day <= ? GROUP BY user_id";
        String sql = "SELECT user_id, xp, RANK() OVER (ORDER BY xp DESC) AS ranking FROM (" + base
                + ") scores ORDER BY xp DESC, user_id ASC LIMIT 11 OFFSET ?";
        try (var connection = source.getConnection(); var query = connection.prepareStatement(sql)) {
            int index = 1; query.setLong(index++, guild);
            if (period != LevelPeriod.TOTAL) {
                query.setDate(index++, java.sql.Date.valueOf(period.since(now)));
                query.setDate(index++, java.sql.Date.valueOf(now.atZone(LevelPeriod.ZONE).toLocalDate()));
            }
            query.setInt(index, page * 10);
            try (var rows = query.executeQuery()) {
                var result = new ArrayList<Entry>();
                while (rows.next()) result.add(new Entry(rows.getLong(1), rows.getLong(2), rows.getLong(3)));
                return List.copyOf(result);
            }
        } catch (Exception error) { throw new IllegalStateException("Cannot read level leaderboard", error); }
    }
    @Override public Set<Long> grants(long guild, long user) {
        try (var connection = source.getConnection(); var query = connection.prepareStatement(
                "SELECT role_id FROM level_role_grants WHERE guild_id = ? AND user_id = ?")) {
            query.setLong(1, guild); query.setLong(2, user);
            try (var rows = query.executeQuery()) { var result = new HashSet<Long>(); while (rows.next()) result.add(rows.getLong(1)); return Set.copyOf(result); }
        } catch (Exception error) { throw new IllegalStateException("Cannot read role grants", error); }
    }
    @Override public void granted(long guild, long user, long role) { grant(guild, user, role, true); }
    @Override public void revoked(long guild, long user, long role) { grant(guild, user, role, false); }
    private void grant(long guild, long user, long role, boolean insert) {
        String sql = insert ? "INSERT IGNORE INTO level_role_grants (guild_id, user_id, role_id) VALUES (?, ?, ?)"
                : "DELETE FROM level_role_grants WHERE guild_id = ? AND user_id = ? AND role_id = ?";
        try (var connection = source.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, guild); statement.setLong(2, user); statement.setLong(3, role); statement.executeUpdate();
        } catch (Exception error) { throw new IllegalStateException("Cannot update role grants", error); }
    }
}
