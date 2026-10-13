package com.sumirelabs.kita.music;

import static org.junit.jupiter.api.Assertions.*;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.NodeOptions;
import dev.arbjerg.lavalink.client.player.TrackLoaded;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Mono;

/** Isolated node with generated local WAVs; never connects to a Discord voice channel. */
@EnabledIfEnvironmentVariable(named = "KITA_TEST_CROSSFADE_NODE", matches = ".+")
class CrossfadeNodeIntegrationTest {
    @Test void clientCanControlRealPluginAndDeserializeState() {
        try (var client = new LavalinkClient(123456789123456789L)) {
            var node = client.addNode(new NodeOptions.Builder().setName("prototype")
                    .setServerUri(System.getenv("KITA_TEST_CROSSFADE_NODE")).setPassword("crossfade-integration-only").build());
            Mono.defer(() -> node.getAvailable() ? Mono.just(true) : Mono.empty()).repeatWhenEmpty(50, repeats -> repeats.delayElements(Duration.ofMillis(100)))
                    .block(Duration.ofSeconds(10));
            var link = client.getOrCreateLink(7);
            link.createOrUpdatePlayer().setVolume(10).block(Duration.ofSeconds(5));
            var first = ((TrackLoaded) link.loadItem("/opt/test/a.wav").block(Duration.ofSeconds(5))).getTrack();
            var second = ((TrackLoaded) link.loadItem("/opt/test/b.wav").block(Duration.ofSeconds(5))).getTrack();
            var api = new CrossfadeApi(client);
            var state = api.request(7, "", "POST", Map.of("id", "integration-A", "encoded", first.getEncoded(), "seconds", 1)).block();
            assertEquals("integration-A", state.token());
            state = api.request(7, "/integration-A", "PUT", Map.of("token", "integration-B", "expected", "integration-A", "encoded", second.getEncoded())).block();
            assertEquals("integration-B", state.next()); assertEquals(0, state.position());
            link.createOrUpdatePlayer().setPaused(true).block();
            assertEquals(0, api.request(7, "/integration-A", "GET", null).block().position());
            api.request(7, "/integration-A/pending", "DELETE", null).block();
            assertEquals("", api.request(7, "/integration-A", "GET", null).block().next());
            api.stop(7, "integration-A").block();
            api.request(7, "", "POST", Map.of("id", "integration-C", "encoded", first.getEncoded(), "seconds", 1)).block();
            link.createOrUpdatePlayer().setTrack(first.makeClone()).block();
            assertThrows(IllegalStateException.class, () -> api.request(7, "/integration-C", "GET", null).block());
            link.destroy().block();
        }
    }
}
