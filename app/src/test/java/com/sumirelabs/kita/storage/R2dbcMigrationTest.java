package com.sumirelabs.kita.storage;

import static org.junit.jupiter.api.Assertions.*;
import com.sumirelabs.kita.levels.LevelsRepository;
import com.sumirelabs.kita.levels.LevelPeriod;
import com.sumirelabs.kita.logshare.LogShareRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@EnabledIfEnvironmentVariable(named = "KITA_TEST_DB_URL", matches = ".+")
class R2dbcMigrationTest {
    @Test void repositoriesOperateReactivelyAndKeepAtomicityAndExistingSchema() {
        try (var database = new Database(System.getenv("KITA_TEST_DB_URL"), System.getenv("KITA_TEST_DB_USER"), System.getenv("KITA_TEST_DB_PASSWORD"))) {
            database.migrate().block(Duration.ofSeconds(30));
            var settings = new R2dbcSettingsRepository(database.source());
            var levels = new R2dbcLevelsRepository(database.source());
            var tickets = new R2dbcTicketRepository(database.source());
            var logs = new R2dbcLogShareRepository(database.source());
            long guild = System.nanoTime(); var now = Instant.parse("2026-10-10T00:00:00Z");
            Mono<Void> scenario = Flux.range(0, 16).flatMap(index -> settings.update(guild, Map.of("option" + index, "true")), 16)
                    .then(settings.get(guild)).doOnNext(value -> assertEquals(16, value.values().size()))
                    .thenMany(Flux.range(0, 16).flatMap(index -> levels.award(guild, 1, LevelsRepository.Source.CHAT, 20, now, 60, new byte[32], 0), 16))
                    .collectList().doOnNext(values -> assertEquals(1, values.stream().filter(LevelsRepository.Award::accepted).count()))
                    .then(levels.profile(guild, 1)).doOnNext(value -> { assertEquals(20, value.xp()); assertEquals(1, value.rank()); })
                    .then(levels.profile(guild + 1, 1)).doOnNext(value -> assertEquals(0, value.xp()))
                    .then(levels.leaderboard(guild, LevelPeriod.MONTH, now, 0)).doOnNext(value -> assertEquals(20, value.getFirst().xp()))
                    .then(tickets.reserve(guild, 1, "test", 99)).doOnNext(value -> assertTrue(value))
                    .then(tickets.reserve(guild, 1, "duplicate", 99)).doOnNext(value -> assertFalse(value))
                    .then(tickets.activate(guild, 1, guild + 2)).then(tickets.find(guild, guild + 2))
                    .doOnNext(value -> { assertEquals(1, value.ownerId()); assertNotNull(value.createdAt()); })
                    .then(tickets.beginClose(guild, guild + 2)).doOnNext(value -> assertTrue(value))
                    .then(tickets.close(guild, guild + 2))
                    .then(logs.claim(new LogShareRepository.Key(guild, 10, "file"))).doOnNext(value -> assertTrue(value))
                    .then(logs.uploaded(new LogShareRepository.Key(guild, 10, "file"), "https://mclo.gs/test"))
                    .then(logs.find(new LogShareRepository.Key(guild, 10, "file"))).doOnNext(value -> assertEquals("https://mclo.gs/test", value.url()))
                    .then(logs.release(new LogShareRepository.Key(guild, 10, "file"))).then();
            scenario.subscribeOn(Schedulers.parallel()).block(Duration.ofSeconds(30));
        }
    }
}
