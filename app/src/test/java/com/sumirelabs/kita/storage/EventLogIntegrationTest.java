package com.sumirelabs.kita.storage;

import static org.junit.jupiter.api.Assertions.*;
import com.sumirelabs.kita.eventlog.*;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

@EnabledIfEnvironmentVariable(named = "KITA_TEST_DB_URL", matches = ".+")
class EventLogIntegrationTest {
    private Database database() { return new Database(System.getenv("KITA_TEST_DB_URL"), System.getenv("KITA_TEST_DB_USER"), System.getenv("KITA_TEST_DB_PASSWORD")); }
    private MessageSnapshot snapshot(long guild, long id, long version, long expires, String text) {
        return new MessageSnapshot(guild, 10, 0, 0, id, 20, "user", false, false, text, List.of(), 1000, version, expires, false);
    }
    private LogEntry entry(long guild, String key) {
        return new LogEntry(key, guild, 10, 0, 0, 20, 0, false, false, false, LogAction.MESSAGE_DELETE,
                System.currentTimeMillis(), List.of(new LogEntry.Field("本文", "秘密のテスト本文")), "", "", "");
    }
    @Test void snapshotsPersistIsolateExpireAndCannotResurrectAfterDeletion() {
        try (var database = database()) {
            database.migrate().block(Duration.ofSeconds(30)); var repository = new R2dbcEventLogRepository(database.source());
            long guild = System.nanoTime(), now = System.currentTimeMillis();
            assertTrue(repository.save(snapshot(guild, 1, 2, now + 21_600_000, "最新"), 8192).block());
            assertFalse(repository.save(snapshot(guild, 1, 1, now + 21_600_000, "古い"), 8192).block());
            assertEquals("最新", new R2dbcEventLogRepository(database.source()).message(guild, 1, now).block().content());
            assertNull(repository.message(guild + 1, 1, now).block()); assertNull(repository.message(guild, 1, now + 21_600_000).block());
            repository.save(MessageSnapshot.tombstone(guild, 10, 1, now + 21_600_000), 8192).block();
            assertNull(repository.message(guild, 1, now).block()); assertFalse(repository.save(snapshot(guild, 1, 3, now + 10000, "復活"), 8192).block());
            repository.cleanup(now + 21_600_001).block(Duration.ofSeconds(10)); repository.purge(guild).block();
        }
    }
    @Test void quotaEvictionAndPurgingKeepCountersConsistent() {
        try (var database = database()) {
            database.migrate().block(); var repository = new R2dbcEventLogRepository(database.source()); var sql = new Sql(database.source());
            long guild = System.nanoTime(), now = System.currentTimeMillis();
            for (int id = 1; id <= 8; id++) assertTrue(repository.save(snapshot(guild, id, 1, now + id * 1000, "x".repeat(300)), 1500).block());
            long bytes = sql.one("SELECT COALESCE(SUM(byte_size),0) AS total FROM eventlog_messages WHERE guild_id = ?", row ->
                    ((Number) row.get("total")).longValue(), guild).block();
            assertTrue(bytes <= 1500); assertEquals(bytes, sql.one("SELECT used_bytes FROM eventlog_usage WHERE scope='messages' AND guild_id=?",
                    row -> row.get("used_bytes", Long.class), guild).block());
            repository.purge(guild).block();
            assertEquals(0, sql.one("SELECT used_bytes FROM eventlog_usage WHERE scope='messages' AND guild_id=?", row -> row.get("used_bytes", Long.class), guild).block());
        }
    }
    @Test void outboxDeduplicatesAndOldLeaseCannotAcknowledgeANewLease() {
        try (var database = database()) {
            database.migrate().block(); var repository = new R2dbcEventLogRepository(database.source());
            long guild = System.nanoTime(), now = System.currentTimeMillis();
            Flux.range(0, 16).flatMap(index -> repository.enqueue(entry(guild, "same"), 100), 16).then().block(Duration.ofSeconds(10));
            var first = repository.claim(now + 1000).block().stream().filter(row -> row.entry().guild() == guild).findFirst().orElseThrow();
            repository.retry(first, now).block();
            var second = repository.claim(now + 1000).block().stream().filter(row -> row.id() == first.id()).findFirst().orElseThrow();
            assertNotEquals(first.lease(), second.lease()); repository.complete(first).block();
            var sql = new Sql(database.source());
            assertEquals(0, sql.one("SELECT delivered FROM eventlog_outbox WHERE id=?", row -> row.get("delivered", Integer.class), first.id()).block());
            repository.complete(second).block();
            assertEquals("", sql.one("SELECT payload FROM eventlog_outbox WHERE id=?", row -> row.get("payload", String.class), first.id()).block());
            repository.enqueue(entry(guild, "same"), 100).block();
            assertEquals(1, sql.one("SELECT COUNT(*) AS total FROM eventlog_outbox WHERE guild_id=?", row -> ((Number) row.get("total")).longValue(), guild).block());
            repository.purge(guild).block();
        }
    }
    @Test void retryingDestinationBlocksLaterEntriesButNotOtherDestinations() {
        try (var database = database()) {
            database.migrate().block(); var repository = new R2dbcEventLogRepository(database.source());
            long guild = System.nanoTime(), now = System.currentTimeMillis();
            repository.enqueue(entry(guild, "first"), 100).block();
            repository.enqueue(entry(guild, "second"), 100).block();
            repository.enqueue(entry(guild, "other-channel"), 200).block();
            var claims = repository.claim(now + 1000).block().stream().filter(row -> row.entry().guild() == guild).toList();
            assertEquals(2, claims.size());
            var first = claims.stream().filter(row -> row.target() == 100).findFirst().orElseThrow();
            assertEquals("first", first.entry().key()); repository.retry(first, now + 60_000).block();
            for (var claim : claims) if (claim.target() == 200) repository.complete(claim).block();
            assertTrue(repository.claim(now + 2000).block().stream().noneMatch(row -> row.entry().guild() == guild));
            var retried = repository.claim(now + 61_000).block().stream().filter(row -> row.entry().guild() == guild).findFirst().orElseThrow();
            assertEquals("first", retried.entry().key()); repository.complete(retried).block();
            var next = repository.claim(now + 62_000).block().stream().filter(row -> row.entry().guild() == guild).findFirst().orElseThrow();
            assertEquals("second", next.entry().key()); repository.purge(guild).block();
        }
    }
}
