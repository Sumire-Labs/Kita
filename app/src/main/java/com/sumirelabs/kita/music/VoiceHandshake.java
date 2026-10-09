package com.sumirelabs.kita.music;

import dev.arbjerg.lavalink.protocol.v4.VoiceState;
import java.util.Objects;

final class VoiceHandshake {
    private String session, channel, token, endpoint;
    private VoiceState last;
    synchronized VoiceState state(String sessionId, String channelId) {
        if (channelId == null) { session = null; channel = null; token = null; endpoint = null; last = null; return null; }
        session = sessionId; channel = channelId; return ready();
    }
    synchronized VoiceState server(String credential, String address) {
        token = credential; endpoint = address; return ready();
    }
    private VoiceState ready() {
        if (session == null || channel == null || token == null || endpoint == null) return null;
        var next = new VoiceState(token, endpoint, session, channel);
        if (Objects.equals(next, last)) return null;
        last = next; return next;
    }
    synchronized void retry() { last = null; }
}
