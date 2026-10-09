package com.sumirelabs.kita.levels;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

final class ChatXpGate {
    private record Key(long guild, long user) {}
    private record Accepted(long at, long next, byte[] hash) {}
    private final Cache<Key, Accepted> cache = Caffeine.newBuilder().maximumSize(100_000).expireAfterWrite(Duration.ofHours(2)).build();
    boolean blocked(long guild, long user, Instant now, byte[] hash) {
        var entry = cache.getIfPresent(new Key(guild, user));
        return entry != null && (now.toEpochMilli() < entry.next || hash != null
                && Arrays.equals(hash, entry.hash) && now.toEpochMilli() - entry.at < 300_000);
    }
    void accepted(long guild, long user, Instant now, int cooldown, byte[] hash) {
        long at = now.toEpochMilli();
        cache.asMap().compute(new Key(guild, user), (key, old) -> old != null && old.at > at ? old
                : new Accepted(at, at + cooldown * 1000L, hash.clone()));
    }
}
