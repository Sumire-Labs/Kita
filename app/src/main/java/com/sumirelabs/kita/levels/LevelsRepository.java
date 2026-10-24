package com.sumirelabs.kita.levels;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import reactor.core.publisher.Mono;

public interface LevelsRepository {
    enum Source { CHAT, VOICE }
    record Award(boolean accepted, long before, long after) {}
    record Profile(long xp, long rank) {}
    record Entry(long userId, long xp, long rank) {}
    Mono<Award> award(long guild, long user, Source source, int amount, Instant now, int cooldown, byte[] hash, long voiceToken);
    Mono<Profile> profile(long guild, long user);
    Mono<List<Entry>> leaderboard(long guild, LevelPeriod period, Instant now, int page);
    Mono<Set<Long>> grants(long guild, long user);
    Mono<Void> granted(long guild, long user, long role);
    Mono<Void> revoked(long guild, long user, long role);
}
