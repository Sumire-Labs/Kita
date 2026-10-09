package com.sumirelabs.kita.storage;

import static org.junit.jupiter.api.Assertions.*;
import com.sumirelabs.kita.levels.*;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "KITA_TEST_DB_URL", matches = ".+")
class LevelsIntegrationTest {
    private Database database() { return new Database(System.getenv("KITA_TEST_DB_URL"), System.getenv("KITA_TEST_DB_USER"), System.getenv("KITA_TEST_DB_PASSWORD")); }
    @Test void chatCooldownRepeatFilterAndVoiceIdempotencyAreDurableAndGuildScoped() throws Exception {
        try (var database = database()) {
            var repository = new R2dbcLevelsRepository(database.source()); long guild = System.nanoTime();
            var now = Instant.parse("2026-10-10T12:00:00Z"); byte[] first = new byte[32], second = new byte[32]; second[0] = 1;
            var wins = new AtomicInteger();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
                for (int i = 0; i < 16; i++) futures.add(executor.submit(() -> {
                    if (repository.award(guild, 1, LevelsRepository.Source.CHAT, 20, now, 60, first, 0).block().accepted()) wins.incrementAndGet();
                }));
                for (var future : futures) future.get();
            }
            assertEquals(1, wins.get());
            var restarted = new R2dbcLevelsRepository(database.source());
            assertFalse(restarted.award(guild, 1, LevelsRepository.Source.CHAT, 20, now.plusSeconds(30), 60, second, 0).block().accepted());
            assertFalse(restarted.award(guild, 1, LevelsRepository.Source.CHAT, 20, now.plusSeconds(60), 60, first, 0).block().accepted());
            assertTrue(restarted.award(guild, 1, LevelsRepository.Source.CHAT, 20, now.plusSeconds(60), 60, second, 0).block().accepted());
            assertTrue(restarted.award(guild, 1, LevelsRepository.Source.CHAT, 20, now.plusSeconds(360), 60, first, 0).block().accepted());
            assertTrue(restarted.award(guild, 1, LevelsRepository.Source.VOICE, 5, now, 0, null, 100).block().accepted());
            assertFalse(restarted.award(guild, 1, LevelsRepository.Source.VOICE, 5, now, 0, null, 100).block().accepted());
            assertEquals(65, restarted.profile(guild, 1).block().xp()); assertEquals(1, restarted.profile(guild, 1).block().rank());
            assertEquals(0, restarted.profile(guild + 1, 1).block().xp());
            restarted.granted(guild, 1, 50).block(); restarted.granted(guild, 1, 50).block();
            assertEquals(java.util.Set.of(50L), restarted.grants(guild, 1).block());
            assertTrue(restarted.grants(guild + 1, 1).block().isEmpty()); restarted.revoked(guild, 1, 50).block();
            assertTrue(restarted.grants(guild, 1).block().isEmpty());
        }
    }
    @Test void rankingTiesPeriodsPaginationAndEmptyProfilesAreConsistent() throws Exception {
        try (var database = database()) {
            var repository = new R2dbcLevelsRepository(database.source()); long guild = System.nanoTime();
            var old = Instant.parse("2026-09-01T10:00:00Z"); var current = Instant.parse("2026-10-10T12:00:00Z");
            repository.award(guild, 1, LevelsRepository.Source.CHAT, 1000, old, 60, new byte[32], 0).block();
            repository.award(guild, 1, LevelsRepository.Source.VOICE, 10, current, 0, null, 10).block();
            repository.award(guild, 2, LevelsRepository.Source.CHAT, 10, current, 60, new byte[32], 0).block();
            var total = repository.leaderboard(guild, LevelPeriod.TOTAL, current, 0).block();
            assertEquals(1010, total.getFirst().xp()); assertEquals(1, total.getFirst().userId());
            for (var period : List.of(LevelPeriod.WEEK, LevelPeriod.MONTH)) {
                var board = repository.leaderboard(guild, period, current, 0).block();
                assertEquals(10, board.getFirst().xp()); assertEquals(1, board.get(0).rank()); assertEquals(1, board.get(1).rank());
            }
            assertEquals(3, repository.profile(guild, 999).block().rank()); assertEquals(0, repository.profile(guild, 999).block().xp());
            for (int user = 3; user <= 14; user++) repository.award(guild, user, LevelsRepository.Source.CHAT, 1, current, 60, new byte[32], 0).block();
            assertEquals(11, repository.leaderboard(guild, LevelPeriod.TOTAL, current, 0).block().size());
            assertEquals(4, repository.leaderboard(guild, LevelPeriod.TOTAL, current, 1).block().size());
            assertTrue(repository.leaderboard(guild + 1, LevelPeriod.TOTAL, current, 0).block().isEmpty());
        }
    }
}
