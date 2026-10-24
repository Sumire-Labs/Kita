package com.sumirelabs.kita.discord;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class SerialActionsTest {
    @Test void closeTerminatesActivePrefetchedAndQueuedResults() {
        var operations = new SerialActions();
        var active = operations.submit(Mono::<Void>never).toFuture();
        var prefetched = operations.submit(Mono::<Void>empty).toFuture();
        var queued = operations.submit(Mono::<Void>empty).toFuture();
        operations.close();
        assertTrue(active.isCompletedExceptionally()); assertTrue(prefetched.isCompletedExceptionally()); assertTrue(queued.isCompletedExceptionally());
        assertThrows(IllegalStateException.class, () -> operations.submit(Mono::<Void>empty).block(Duration.ofSeconds(1)));
    }
    @Test void acceptedMutationFinishesWhenItsSubscriberCancels() {
        var release = reactor.core.publisher.Sinks.<Void>one(); var committed = new java.util.concurrent.atomic.AtomicBoolean();
        try (var operations = new SerialActions()) {
            var client = operations.submit(() -> release.asMono().doOnSuccess(ignored -> committed.set(true))).subscribe();
            client.dispose(); assertFalse(committed.get()); release.tryEmitEmpty();
            operations.submit(Mono::<Void>empty).block(Duration.ofSeconds(1)); assertTrue(committed.get());
        }
    }
}
