package com.sumirelabs.kita.storage;

import com.sumirelabs.kita.levels.LevelPeriod;
import com.sumirelabs.kita.levels.LevelsRepository;
import io.r2dbc.spi.ConnectionFactory;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import reactor.core.publisher.Mono;

public final class R2dbcLevelsRepository implements LevelsRepository {
    private final Sql sql; private final R2dbcLevelAwards awards;
    public R2dbcLevelsRepository(ConnectionFactory source) { sql = new Sql(source); awards = new R2dbcLevelAwards(source); }
    @Override public Mono<Award> award(long guild, long user, Source kind, int amount, Instant now, int cooldown, byte[] hash, long token) {
        return awards.award(guild, user, kind, amount, now, cooldown, hash, token);
    }
    @Override public Mono<Profile> profile(long guild, long user) {
        return sql.one("SELECT score.xp, 1 + (SELECT COUNT(*) FROM level_members WHERE guild_id = ? AND xp > score.xp) AS ranking"
                + " FROM (SELECT COALESCE(MAX(xp), 0) AS xp FROM level_members WHERE guild_id = ? AND user_id = ?) score",
                row -> new Profile(row.get("xp", Long.class), row.get("ranking", Long.class)), guild, guild, user);
    }
    @Override public Mono<List<Entry>> leaderboard(long guild, LevelPeriod period, Instant now, int page) {
        if (page < 0 || page > 10_000) return Mono.error(new IllegalArgumentException("ページが範囲外です。"));
        String base = period == LevelPeriod.TOTAL ? "SELECT user_id, xp FROM level_members WHERE guild_id = ? AND xp > 0"
                : "SELECT user_id, SUM(xp) AS xp FROM level_daily WHERE guild_id = ? AND day >= ? AND day <= ? GROUP BY user_id";
        Object[] values = period == LevelPeriod.TOTAL ? new Object[]{guild, page * 10}
                : new Object[]{guild, period.since(now), now.atZone(LevelPeriod.ZONE).toLocalDate(), page * 10};
        return sql.rows("SELECT user_id, xp, RANK() OVER (ORDER BY xp DESC) AS ranking FROM (" + base
                        + ") scores ORDER BY xp DESC, user_id ASC LIMIT 11 OFFSET ?",
                row -> new Entry(row.get("user_id", Long.class), row.get("xp", Long.class), row.get("ranking", Long.class)), values).collectList();
    }
    @Override public Mono<Set<Long>> grants(long guild, long user) {
        return sql.rows("SELECT role_id FROM level_role_grants WHERE guild_id = ? AND user_id = ?", row -> row.get("role_id", Long.class), guild, user)
                .collectList().map(Set::copyOf);
    }
    @Override public Mono<Void> granted(long guild, long user, long role) {
        return sql.update("INSERT IGNORE INTO level_role_grants (guild_id, user_id, role_id) VALUES (?, ?, ?)", guild, user, role).then();
    }
    @Override public Mono<Void> revoked(long guild, long user, long role) {
        return sql.update("DELETE FROM level_role_grants WHERE guild_id = ? AND user_id = ? AND role_id = ?", guild, user, role).then();
    }
}
