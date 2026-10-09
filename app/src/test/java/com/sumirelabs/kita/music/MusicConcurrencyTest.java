package com.sumirelabs.kita.music;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

class MusicConcurrencyTest {
    @Test void slowUpdateKeepsPanelReadableAndSerializesMutations() throws Exception {
        var session = new MusicSession();
        session.replacePanel(1, 10);
        var request = Sinks.one();
        var started = new CountDownLatch(1);
        var waiting = new CountDownLatch(1);
        var second = new CountDownLatch(1);
        try (var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = tasks.submit(() -> session.operate(() -> MusicUpdates.commit(session,
                    request.asMono().doOnSubscribe(ignored -> started.countDown()), () -> session.volume = 20)));
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                tasks.submit(() -> {
                    waiting.countDown();
                    session.operate(second::countDown);
                });
                assertTrue(waiting.await(5, TimeUnit.SECONDS));
                var read = tasks.submit(() -> {
                    assertTrue(session.isCurrentPanel(10));
                    assertNotNull(session.beginPanelRefresh());
                    assertNotNull(PlayerPanel.render(session, null, List.of()));
                    synchronized (session) { return session.volume; }
                });
                assertEquals(10, read.get(5, TimeUnit.SECONDS));
                assertEquals(1, second.getCount(), "Mutations must wait for the active request");
                request.tryEmitEmpty();
                first.get(5, TimeUnit.SECONDS);
                assertTrue(second.await(5, TimeUnit.SECONDS));
                synchronized (session) { assertEquals(20, session.volume); }
            } finally { request.tryEmitEmpty(); }
        }
    }

    @Test void failedUpdateDoesNotCommitAndReleasesOperationLock() throws Exception {
        var session = new MusicSession();
        assertThrows(IllegalStateException.class, () -> session.operate(() -> MusicUpdates.commit(session,
                Mono.error(new IllegalStateException("offline")), () -> session.volume = 20)));
        assertEquals(10, session.volume);
        try (var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
            tasks.submit(() -> session.operate(() -> {})).get(5, TimeUnit.SECONDS);
        }
    }
}
