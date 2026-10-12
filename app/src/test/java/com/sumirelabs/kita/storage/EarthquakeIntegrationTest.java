package com.sumirelabs.kita.storage;

import static org.junit.jupiter.api.Assertions.*;
import com.sumirelabs.kita.earthquake.*;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

@EnabledIfEnvironmentVariable(named = "KITA_TEST_DB_URL", matches = ".+")
class EarthquakeIntegrationTest {
    @Test void ingestionLeasesRecreationCancellationAndGuildIsolationAreDurable() {
        try (var database = new Database(System.getenv("KITA_TEST_DB_URL"), System.getenv("KITA_TEST_DB_USER"), System.getenv("KITA_TEST_DB_PASSWORD"))) {
            database.migrate().block(Duration.ofSeconds(30)); var repository = new R2dbcEarthquakeRepository(database.source());
            var original = Notices.warning(2, false);
            var notice = new EarthquakeNotice(original.source(), original.sourceUrl(), original.key() + System.nanoTime(), original.kind(),
                    original.revision(), original.issuedMs(), original.occurredMs(), false, false, -1, original.title(), original.place(), null, null,
                    original.notes(), original.areas());
            var accepted = Flux.range(0, 16).flatMap(index -> repository.accept(notice), 16).collectList().block(Duration.ofSeconds(10));
            assertEquals(1, accepted.stream().filter(Boolean::booleanValue).count());
            var leases = Flux.range(0, 16).flatMap(index -> repository.claim(notice, 1, 10), 16).collectList().block(Duration.ofSeconds(10));
            assertEquals(1, leases.size()); var lease = leases.getFirst();
            assertNotNull(repository.claim(notice, 2, 10).block());
            repository.sent(lease, notice, 100).then(repository.release(lease)).block();
            var restarted = new R2dbcEarthquakeRepository(database.source());
            assertNull(restarted.claim(notice, 1, 10).block()); assertEquals(100, restarted.delivery(notice.key(), 1, 10).block().messageId());
            var cancelled = new EarthquakeNotice(notice.source(), notice.sourceUrl(), notice.key(), notice.kind(), notice.revision(), notice.issuedMs(),
                    notice.occurredMs(), true, false, -1, "取消", "", null, null, java.util.List.of(), java.util.List.of());
            assertTrue(restarted.accept(cancelled).block()); assertFalse(restarted.accept(notice).block());
            var retry = restarted.claim(cancelled, 1, 10).block(); assertNotNull(retry);
            restarted.release(retry).block(); assertNotNull(restarted.claim(cancelled, 1, 10).block());
            assertTrue(restarted.recent(Instant.EPOCH).any(value -> value.key().equals(notice.key()) && value.cancelled()).block());
        }
    }
}