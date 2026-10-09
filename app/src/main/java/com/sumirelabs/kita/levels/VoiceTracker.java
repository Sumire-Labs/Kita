package com.sumirelabs.kita.levels;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public final class VoiceTracker {
    public record Credit(long userId, long token, int minutes) {}
    private record Presence(long channel, Instant since, Instant observed, Credit pending) {}
    private final Map<Long, Presence> members = new HashMap<>();
    public synchronized List<Credit> update(Map<Long, Long> eligible, Instant now) {
        members.keySet().retainAll(eligible.keySet());
        var credits = new ArrayList<Credit>();
        eligible.forEach((user, channel) -> {
            var previous = members.get(user);
            if (previous == null || previous.channel != channel || now.isBefore(previous.observed)
                    || now.toEpochMilli() - previous.observed.toEpochMilli() > 45_000) {
                members.put(user, new Presence(channel, now, now, null)); return;
            }
            boolean due = java.time.Duration.between(previous.since, now).toMinutes() >= 1;
            var pending = previous.pending;
            if (pending == null && due) pending = new Credit(user, previous.since.plusSeconds(60).getEpochSecond() / 60, 1);
            if (pending != null) credits.add(pending);
            members.put(user, new Presence(channel, previous.since, now, pending));
        });
        return List.copyOf(credits);
    }
    public synchronized void acknowledge(Credit credit) {
        var state = members.get(credit.userId());
        if (state != null && credit.equals(state.pending)) members.put(credit.userId(),
                new Presence(state.channel, state.since.plusSeconds(credit.minutes() * 60L), state.observed, null));
    }
    public synchronized void invalidate(long user) { members.remove(user); }
    public synchronized void clear() { members.clear(); }
}
