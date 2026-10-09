package com.sumirelabs.kita.logshare;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ShareCoordinatorTest {
    private static final class Store implements LogShareRepository {
        final ConcurrentHashMap<Key, Share> rows = new ConcurrentHashMap<>();
        final java.util.Set<Key> claims = ConcurrentHashMap.newKeySet();
        public Optional<Share> find(Key key) { return Optional.ofNullable(rows.get(key)); }
        public boolean claim(Key key) {
            if (!claims.add(key)) return false;
            rows.putIfAbsent(key, new Share(null, 0)); return true;
        }
        public void uploaded(Key key, String url) { rows.put(key, new Share(url, 0)); }
        public void replied(Key key, long id) { rows.compute(key, (ignored, share) -> new Share(share.url(), id)); }
        public void release(Key key) { claims.remove(key); }
    }
    @Test void simultaneousRequestsUploadAndReplyOnlyOnce() throws Exception {
        var store = new Store(); var coordinator = new ShareCoordinator(store);
        var key = new LogShareRepository.Key(1, 10, "file");
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var uploads = new AtomicInteger(); var replies = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> coordinator.share(key, () -> {
                uploads.incrementAndGet(); entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return "https://mclo.gs/first";
            }, url -> { replies.incrementAndGet(); return 100; }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertThrows(IllegalArgumentException.class, () -> coordinator.share(key, () -> fail("duplicate upload"), url -> fail("duplicate reply")));
            release.countDown(); assertEquals("https://mclo.gs/first", first.get());
        } finally { release.countDown(); }
        assertEquals("https://mclo.gs/first", coordinator.share(key, () -> fail("already stored"), url -> fail("already replied")));
        assertEquals(1, uploads.get()); assertEquals(1, replies.get());
    }
    @Test void discordReplyFailureReusesSavedUrlEvenAfterServiceRecreation() throws Exception {
        var store = new Store(); var key = new LogShareRepository.Key(1, 10, "file");
        assertThrows(IllegalStateException.class, () -> new ShareCoordinator(store).share(key, () -> "https://mclo.gs/saved",
                url -> { throw new IllegalStateException("Discord unavailable"); }));
        assertEquals("https://mclo.gs/saved", new ShareCoordinator(store).share(key,
                () -> fail("must reuse the durable upload"), url -> 100));
        var other = new LogShareRepository.Key(2, 10, "file");
        assertEquals("https://mclo.gs/other", new ShareCoordinator(store).share(other, () -> "https://mclo.gs/other", url -> 200));
    }
    @Test void failedUploadReleasesClaimForAnExplicitRetry() throws Exception {
        var store = new Store(); var coordinator = new ShareCoordinator(store);
        var key = new LogShareRepository.Key(1, 10, "file");
        assertThrows(IllegalStateException.class, () -> coordinator.share(key, () -> { throw new IllegalStateException(); }, url -> fail()));
        assertEquals("https://mclo.gs/retry", coordinator.share(key, () -> "https://mclo.gs/retry", url -> 100));
    }
}
