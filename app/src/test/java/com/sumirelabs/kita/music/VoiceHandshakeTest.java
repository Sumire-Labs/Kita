package com.sumirelabs.kita.music;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class VoiceHandshakeTest {
    @Test void bothEventOrdersUseTheVoiceSessionAndChannel() {
        for (boolean serverFirst : new boolean[]{true, false}) {
            var handshake = new VoiceHandshake();
            dev.arbjerg.lavalink.protocol.v4.VoiceState result;
            if (serverFirst) { assertNull(handshake.server("credential", "voice.example")); result = handshake.state("voice-session", "100"); }
            else { assertNull(handshake.state("voice-session", "100")); result = handshake.server("credential", "voice.example"); }
            assertNotNull(result); assertEquals("voice-session", result.getSessionId()); assertEquals("100", result.getChannelId());
            assertNull(handshake.state("voice-session", "100"), "Mute-only updates must not reconnect");
        }
    }
    @Test void disconnectClearsCredentialsAndTransientEndpointsCannotConnect() {
        var first = new VoiceHandshake(); var second = new VoiceHandshake();
        first.state("one", "100"); assertNotNull(first.server("credential", "voice.example"));
        assertNull(second.state("two", "200"));
        assertNull(first.state("one", null));
        assertNull(first.state("new", "100"), "A new connection needs fresh credentials");
        assertNull(first.server("new-credential", null)); assertNotNull(first.server("new-credential", "new.example"));
        first.retry(); assertNotNull(first.state("new", "100"));
    }
}
