package com.sumirelabs.kita.music;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sumirelabs.kita.common.CrossfadeState;
import dev.arbjerg.lavalink.client.LavalinkClient;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import reactor.core.publisher.Mono;

final class CrossfadeApi {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final LavalinkClient client;
    CrossfadeApi(LavalinkClient client) { this.client = client; }
    Mono<CrossfadeState> request(long guild, String suffix, String method, Map<String, ?> payload) {
        return Mono.defer(() -> {
            var node = client.getOrCreateLink(guild).getNode();
            if (node.getSessionId() == null) return Mono.error(new IllegalStateException("Audio session unavailable"));
            String path = "/v4/kita/crossfade/" + node.getSessionId() + "/" + guild + suffix;
            RequestBody body;
            try { body = payload == null ? null : RequestBody.create(JSON.writeValueAsBytes(payload), MediaType.get("application/json")); }
            catch (Exception failure) { return Mono.error(failure); }
            return node.customJsonRequest(CrossfadeState.class, builder -> builder.path(path).method(method, body));
        }).timeout(java.time.Duration.ofSeconds(15));
    }
    Mono<Void> stop(long guild, String id) { return id.isBlank() ? Mono.empty() : request(guild, "/" + id, "DELETE", null).then(); }
}
