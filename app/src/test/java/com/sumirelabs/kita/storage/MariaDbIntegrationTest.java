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
            var repository = new JdbcLogShareRepository(database.source());
            long guild = System.nanoTime();
            var key = new com.sumirelabs.kita.logshare.LogShareRepository.Key(guild, 1, "attachment");
            var wins = new java.util.concurrent.atomic.AtomicInteger();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
                for (int i = 0; i < 16; i++) futures.add(executor.submit(() -> { if (repository.claim(key)) wins.incrementAndGet(); }));
                for (var future : futures) future.get();
            }
            assertEquals(1, wins.get());
            repository.uploaded(key, "https://mclo.gs/test"); repository.release(key);
            var restarted = new JdbcLogShareRepository(database.source());
            assertEquals("https://mclo.gs/test", restarted.find(key).orElseThrow().url());
            assertTrue(restarted.claim(key)); restarted.replied(key, 100); restarted.release(key);
            assertFalse(restarted.claim(key));
            assertEquals(100, restarted.find(key).orElseThrow().replyId());
            var other = new com.sumirelabs.kita.logshare.LogShareRepository.Key(guild + 1, 1, "attachment");
            assertTrue(restarted.find(other).isEmpty()); assertTrue(restarted.claim(other)); restarted.release(other);
        }
    }

    @Test void guildIsolationAtomicMergingAndTicketLifecycle() throws Exception {
        try (var database = new Database(System.getenv("KITA_TEST_DB_URL"), System.getenv("KITA_TEST_DB_USER"),
                System.getenv("KITA_TEST_DB_PASSWORD"))) {
            var settings = new JdbcSettingsRepository(database.source());
            long guild = System.nanoTime(); long otherGuild = guild + 1;
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
                for (int i = 0; i < 16; i++) {
                    var key = "option" + i;
                    futures.add(executor.submit(() -> settings.update(guild, Map.of(key, "true"))));
                }
                for (var future : futures) future.get();
            }
            assertEquals(16, settings.get(guild).values().size());
            assertTrue(settings.get(otherGuild).values().isEmpty());
            var tickets = new JdbcTicketRepository(database.source());
            assertTrue(tickets.reserve(guild, 1, "support", 99));
            assertFalse(tickets.reserve(guild, 1, "duplicate", 99));
            assertTrue(tickets.reserve(otherGuild, 1, "other guild", 99));
            tickets.activate(guild, 1, guild + 2);
            assertTrue(tickets.find(otherGuild, guild + 2).isEmpty());
            tickets.claim(guild, guild + 2, 42);
            assertThrows(IllegalArgumentException.class, () -> tickets.claim(guild, guild + 2, 43));
            assertTrue(tickets.beginClose(guild, guild + 2));
            assertFalse(tickets.beginClose(guild, guild + 2));
            assertFalse(tickets.reserve(guild, 1, "while closing", 99));
            tickets.close(guild, guild + 2);
            assertTrue(tickets.reserve(guild, 1, "new ticket", 99));
        }
    }
}
