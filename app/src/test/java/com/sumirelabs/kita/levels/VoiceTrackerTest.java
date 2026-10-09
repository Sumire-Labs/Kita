package com.sumirelabs.kita.levels;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VoiceTrackerTest {
    private static final Instant START = Instant.parse("2026-10-09T15:00:00Z");
    @Test void requiresAFullEligibleMinuteAndRetriesUntilAcknowledged() {
        var tracker = new VoiceTracker(); var users = Map.of(1L, 10L);
        assertTrue(tracker.update(users, START).isEmpty());
        for (int second : new int[]{15, 30, 45}) assertTrue(tracker.update(users, START.plusSeconds(second)).isEmpty());
        var credit = tracker.update(users, START.plusSeconds(60)).getFirst();
        assertEquals(1, credit.minutes()); assertEquals(1, credit.userId());
        assertEquals(credit, tracker.update(users, START.plusSeconds(75)).getFirst());
        tracker.acknowledge(credit);
        assertTrue(tracker.update(users, START.plusSeconds(90)).isEmpty());
        tracker.update(users, START.plusSeconds(105));
        assertNotEquals(credit.token(), tracker.update(users, START.plusSeconds(120)).getFirst().token());
    }
    @Test void movingLeavingAndConnectionGapsDoNotEarnAwayTime() {
        var tracker = new VoiceTracker();
        tracker.update(Map.of(1L, 10L), START); tracker.update(Map.of(1L, 10L), START.plusSeconds(30));
        tracker.update(Map.of(1L, 11L), START.plusSeconds(45));
        assertTrue(tracker.update(Map.of(1L, 11L), START.plusSeconds(60)).isEmpty());
        tracker.invalidate(1); assertTrue(tracker.update(Map.of(1L, 11L), START.plusSeconds(75)).isEmpty());
        assertTrue(tracker.update(Map.of(1L, 11L), START.plusSeconds(500)).isEmpty());
        tracker.update(Map.of(), START.plusSeconds(510));
        assertTrue(tracker.update(Map.of(1L, 11L), START.plusSeconds(530)).isEmpty());
    }
}
