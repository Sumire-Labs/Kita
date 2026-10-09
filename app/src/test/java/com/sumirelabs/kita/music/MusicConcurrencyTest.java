package com.sumirelabs.kita.music;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

class MusicConcurrencyTest {
    @Test void slowUpdateKeepsPanelReadableAndSerializesMutations() {
        var session = new MusicSession(); session.replacePanel(1, 10);
        var request = Sinks.<Void>one(); var second = new AtomicBoolean();
        try (var operations = session.operations) {
            var first = operations.submit(() -> MusicUpdates.commit(session, request.asMono(), () -> session.volume = 20)).toFuture();
            var next = operations.submit(() -> Mono.fromRunnable(() -> second.set(true))).toFuture();
            assertTrue(session.isCurrentPanel(10)); assertNotNull(session.beginPanelRefresh());
            assertNotNull(PlayerPanel.render(session, null, List.of()));
            assertEquals(10, session.volume); assertFalse(second.get());
            request.tryEmitEmpty(); first.join(); next.join();
            assertEquals(20, session.volume); assertTrue(second.get());
        }
    }
    @Test void failedUpdateDoesNotCommitAndAllowsLaterOperations() {
        var session = new MusicSession();
        try (var operations = session.operations) {
            assertThrows(IllegalStateException.class, () -> operations.submit(() -> MusicUpdates.commit(session,
                    Mono.error(new IllegalStateException("offline")), () -> session.volume = 20)).block(Duration.ofSeconds(2)));
            assertEquals(10, session.volume);
            operations.submit(Mono::<Void>empty).block(Duration.ofSeconds(2));
        }
    }
}
