package com.sumirelabs.kita.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "KITA_TEST_DB_URL", matches = ".+")
class MariaDbIntegrationTest {
    @Test void logShareLeasesAreAtomicGuildScopedAndSurviveRepositoryRecreation() throws Exception {
        try (var database = new Database(System.getenv("KITA_TEST_DB_URL"), System.getenv("KITA_TEST_DB_USER"),
                System.getenv("KITA_TEST_DB_PASSWORD"))) {
            var repository = new R2dbcLogShareRepository(database.source());
            long guild = System.nanoTime();
            var key = new com.sumirelabs.kita.logshare.LogShareRepository.Key(guild, 1, "attachment");
            var wins = new java.util.concurrent.atomic.AtomicInteger();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
                for (int i = 0; i < 16; i++) futures.add(executor.submit(() -> { if (repository.claim(key).block()) wins.incrementAndGet(); }));
                for (var future : futures) future.get();
            }
            assertEquals(1, wins.get());
            repository.uploaded(key, "https://mclo.gs/test").block(); repository.release(key).block();
            var restarted = new R2dbcLogShareRepository(database.source());
            assertEquals("https://mclo.gs/test", restarted.find(key).blockOptional().orElseThrow().url());
            assertTrue(restarted.claim(key).block()); restarted.replied(key, 100).block(); restarted.release(key).block();
            assertFalse(restarted.claim(key).block());
            assertEquals(100, restarted.find(key).blockOptional().orElseThrow().replyId());
            var other = new com.sumirelabs.kita.logshare.LogShareRepository.Key(guild + 1, 1, "attachment");
            assertTrue(restarted.find(other).blockOptional().isEmpty()); assertTrue(restarted.claim(other).block()); restarted.release(other).block();
        }
    }

    @Test void guildIsolationAtomicMergingAndTicketLifecycle() throws Exception {
        try (var database = new Database(System.getenv("KITA_TEST_DB_URL"), System.getenv("KITA_TEST_DB_USER"),
                System.getenv("KITA_TEST_DB_PASSWORD"))) {
            var settings = new R2dbcSettingsRepository(database.source());
            long guild = System.nanoTime(); long otherGuild = guild + 1;
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
                for (int i = 0; i < 16; i++) {
                    var key = "option" + i;
                    futures.add(executor.submit(() -> settings.update(guild, Map.of(key, "true")).block()));
                }
                for (var future : futures) future.get();
            }
            assertEquals(16, settings.get(guild).block().values().size());
            assertTrue(settings.get(otherGuild).block().values().isEmpty());
            var tickets = new R2dbcTicketRepository(database.source());
            assertTrue(tickets.reserve(guild, 1, "support", 99).block());
            assertFalse(tickets.reserve(guild, 1, "duplicate", 99).block());
            assertTrue(tickets.reserve(otherGuild, 1, "other guild", 99).block());
            tickets.activate(guild, 1, guild + 2).block();
            assertTrue(tickets.find(otherGuild, guild + 2).blockOptional().isEmpty());
            tickets.claim(guild, guild + 2, 42).block();
            assertThrows(IllegalArgumentException.class, () -> tickets.claim(guild, guild + 2, 43).block());
            assertTrue(tickets.beginClose(guild, guild + 2).block());
            assertFalse(tickets.beginClose(guild, guild + 2).block());
            assertFalse(tickets.reserve(guild, 1, "while closing", 99).block());
            tickets.close(guild, guild + 2).block();
            assertTrue(tickets.reserve(guild, 1, "new ticket", 99).block());
        }
    }
}
