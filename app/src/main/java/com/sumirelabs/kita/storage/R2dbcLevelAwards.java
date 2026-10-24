package com.sumirelabs.kita.storage;

import com.sumirelabs.kita.levels.LevelCurve;
import com.sumirelabs.kita.levels.LevelPeriod;
import com.sumirelabs.kita.levels.LevelsRepository;
import io.r2dbc.spi.ConnectionFactory;
import java.time.Instant;
import java.util.Arrays;
import reactor.core.publisher.Mono;

final class R2dbcLevelAwards {
    private record State(long xp, boolean blocked) {}
    private final Sql sql;
    R2dbcLevelAwards(ConnectionFactory source) { sql = new Sql(source); }
    Mono<LevelsRepository.Award> award(long guild, long user, LevelsRepository.Source kind, int amount,
                                     Instant now, int cooldown, byte[] hash, long token) {
        if (amount <= 0 || amount > 1_000_000 || kind == LevelsRepository.Source.CHAT && (hash == null || hash.length != 32)) {
            return Mono.error(new IllegalArgumentException("Invalid XP award"));
        }
        return sql.transaction(connection -> Sql.update(connection,
                "INSERT INTO level_members (guild_id, user_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)", guild, user)
                .then(Sql.rows(connection, "SELECT xp, chat_next_ms, chat_last_ms, chat_hash, voice_token"
                        + " FROM level_members WHERE guild_id = ? AND user_id = ? FOR UPDATE", row -> {
                    long xp = row.get("xp", Long.class);
                    boolean blocked = kind == LevelsRepository.Source.CHAT
                            ? now.toEpochMilli() < row.get("chat_next_ms", Long.class) || (Arrays.equals(hash, row.get("chat_hash", byte[].class))
                                && now.toEpochMilli() - row.get("chat_last_ms", Long.class) < 300_000)
                            : token <= row.get("voice_token", Long.class);
                    return new State(xp, blocked);
                }, guild, user).single()).flatMap(state -> {
                    if (state.blocked || state.xp >= LevelCurve.MAX_XP) return Mono.just(new LevelsRepository.Award(false, state.xp, state.xp));
                    long after = Math.min(LevelCurve.MAX_XP, state.xp + amount);
                    String fields = kind == LevelsRepository.Source.CHAT ? "chat_next_ms = ?, chat_last_ms = ?, chat_hash = ?" : "voice_token = ?";
                    Object[] values = kind == LevelsRepository.Source.CHAT
                            ? new Object[]{after, now.toEpochMilli() + cooldown * 1000L, now.toEpochMilli(), hash, guild, user}
                            : new Object[]{after, token, guild, user};
                    return Sql.update(connection, "UPDATE level_members SET xp = ?, " + fields + " WHERE guild_id = ? AND user_id = ?", values)
                            .then(Sql.update(connection, "INSERT INTO level_daily (guild_id, user_id, day, xp) VALUES (?, ?, ?, ?)"
                                    + " ON DUPLICATE KEY UPDATE xp = xp + VALUES(xp)", guild, user,
                                    now.atZone(LevelPeriod.ZONE).toLocalDate(), after - state.xp))
                            .thenReturn(new LevelsRepository.Award(true, state.xp, after));
                }));
    }
}
