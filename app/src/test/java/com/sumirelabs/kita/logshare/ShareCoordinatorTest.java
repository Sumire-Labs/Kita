package com.sumirelabs.kita.logshare;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

class ShareCoordinatorTest {
    private static final class Store implements LogShareRepository {
        final ConcurrentHashMap<Key, Share> rows = new ConcurrentHashMap<>();
        final java.util.Set<Key> claims = ConcurrentHashMap.newKeySet();
        public Mono<Share> find(Key key) { return Mono.defer(() -> Mono.justOrEmpty(rows.get(key))); }
        public Mono<Boolean> claim(Key key) { return Mono.fromSupplier(() -> {
            if (!claims.add(key)) return false; rows.putIfAbsent(key, new Share(null, 0)); return true;
        }); }
        public Mono<Void> uploaded(Key key, String url) { return Mono.fromRunnable(() -> rows.put(key, new Share(url, 0))); }
        public Mono<Void> replied(Key key, long id) { return Mono.fromRunnable(() -> rows.compute(key, (ignored, share) -> new Share(share.url(), id))); }
        public Mono<Void> release(Key key) { return Mono.fromRunnable(() -> claims.remove(key)); }
    }
    @Test void simultaneousRequestsUploadAndReplyOnlyOnce() {
        var store = new Store(); var coordinator = new ShareCoordinator(store); var key = new LogShareRepository.Key(1, 10, "file");
        var upload = Sinks.<String>one(); var replies = new AtomicInteger();
        var first = coordinator.share(key, upload::asMono, url -> { replies.incrementAndGet(); return Mono.just(100L); }).toFuture();
        assertThrows(IllegalArgumentException.class, () -> coordinator.share(key, () -> fail("duplicate upload"), url -> fail("duplicate reply")).block());
        upload.tryEmitValue("https://mclo.gs/first"); assertEquals("https://mclo.gs/first", first.join());
        assertEquals("https://mclo.gs/first", coordinator.share(key, () -> fail("already stored"), url -> fail("already replied")).block());
        assertEquals(1, replies.get()); assertTrue(store.claims.isEmpty());
    }
    @Test void discordReplyFailureReusesSavedUrlEvenAfterServiceRecreation() {
        var store = new Store(); var key = new LogShareRepository.Key(1, 10, "file");
        assertThrows(IllegalStateException.class, () -> new ShareCoordinator(store).share(key, () -> Mono.just("https://mclo.gs/saved"),
                url -> Mono.error(new IllegalStateException("Discord unavailable"))).block());
        assertEquals("https://mclo.gs/saved", new ShareCoordinator(store).share(key, () -> fail("must reuse upload"), url -> Mono.just(100L)).block());
        var other = new LogShareRepository.Key(2, 10, "file");
        assertEquals("https://mclo.gs/other", new ShareCoordinator(store).share(other, () -> Mono.just("https://mclo.gs/other"), url -> Mono.just(200L)).block());
    }
    @Test void failedUploadReleasesClaimForAnExplicitRetry() {
        var store = new Store(); var coordinator = new ShareCoordinator(store); var key = new LogShareRepository.Key(1, 10, "file");
        assertThrows(IllegalStateException.class, () -> coordinator.share(key, () -> Mono.error(new IllegalStateException()), url -> fail()).block());
        assertEquals("https://mclo.gs/retry", coordinator.share(key, () -> Mono.just("https://mclo.gs/retry"), url -> Mono.just(100L)).block());
    }
    @Test void cancellationReleasesClaimWithoutPublishing() {
        var store = new Store(); var coordinator = new ShareCoordinator(store); var key = new LogShareRepository.Key(1, 10, "file");
        var subscription = coordinator.share(key, Mono::never, url -> fail()).subscribe();
        assertTrue(store.claims.contains(key)); subscription.dispose(); assertFalse(store.claims.contains(key));
    }
}
