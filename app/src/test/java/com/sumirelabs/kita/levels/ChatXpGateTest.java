package com.sumirelabs.kita.levels;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ChatXpGateTest {
    @Test void onlyAcceptedAwardsSuppressFutureDatabaseWorkAndGuildsStaySeparate() {
        var gate = new ChatXpGate(); var now = Instant.parse("2026-10-10T00:00:00Z"); byte[] hash = {1};
        assertFalse(gate.blocked(1, 2, now, hash));
        gate.accepted(1, 2, now, 60, hash); hash[0] = 9;
        assertTrue(gate.blocked(1, 2, now.plusSeconds(59), null));
        assertTrue(gate.blocked(1, 2, now.plusSeconds(60), new byte[]{1}));
        assertFalse(gate.blocked(1, 2, now.plusSeconds(60), new byte[]{2}));
        assertFalse(gate.blocked(1, 2, now.plusSeconds(300), new byte[]{1}));
        assertFalse(gate.blocked(3, 2, now, new byte[]{1}));
    }
    @Test void lateCompletionCannotOverwriteANewerAcceptedMessage() {
        var gate = new ChatXpGate(); var now = Instant.parse("2026-10-10T00:00:00Z");
        gate.accepted(1, 1, now.plusSeconds(60), 60, new byte[]{2});
        gate.accepted(1, 1, now, 60, new byte[]{1});
        assertTrue(gate.blocked(1, 1, now.plusSeconds(90), null));
    }
}
