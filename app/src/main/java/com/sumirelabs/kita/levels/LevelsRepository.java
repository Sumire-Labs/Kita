package com.sumirelabs.kita.levels;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface LevelsRepository {
    enum Source { CHAT, VOICE }
    record Award(boolean accepted, long before, long after) {}
    record Profile(long xp, long rank) {}
    record Entry(long userId, long xp, long rank) {}
    Award award(long guild, long user, Source source, int amount, Instant now, int cooldown, byte[] hash, long voiceToken);
    Profile profile(long guild, long user);
    List<Entry> leaderboard(long guild, LevelPeriod period, Instant now, int page);
    Set<Long> grants(long guild, long user);
    void granted(long guild, long user, long role);
    void revoked(long guild, long user, long role);
}
