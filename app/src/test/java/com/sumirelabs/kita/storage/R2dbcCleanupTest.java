package com.sumirelabs.kita.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@EnabledIfEnvironmentVariable(named = "KITA_TEST_DB_URL", matches = ".+")
class R2dbcCleanupTest {
    @Test void errorAndCancellationRollbackAndReturnConnectionsToThePool() {
        try (var database = new Database(System.getenv("KITA_TEST_DB_URL"), System.getenv("KITA_TEST_DB_USER"), System.getenv("KITA_TEST_DB_PASSWORD"))) {
            database.migrate().block(Duration.ofSeconds(30));
            var settings = new R2dbcSettingsRepository(database.source()); var sql = new Sql(database.source()); long guild = System.nanoTime();
            settings.update(guild, Map.of("saved", "true")).block();
            assertThrows(IllegalStateException.class, () -> sql.transaction(connection ->
                    Sql.update(connection, "UPDATE guild_settings SET settings = '{}' WHERE guild_id = ?", guild)
                            .then(Mono.error(new IllegalStateException("rollback")))).block(Duration.ofSeconds(5)));
            assertEquals("true", settings.get(guild).block().value("saved", ""));
            for (int iteration = 0; iteration < 16; iteration++) {
                var entered = Sinks.<Void>one();
                var operation = sql.transaction(connection -> Sql.update(connection, "UPDATE guild_settings SET settings = '{}' WHERE guild_id = ?", guild)
                        .doOnSuccess(ignored -> entered.tryEmitEmpty()).then(Mono.never())).subscribe();
                entered.asMono().block(Duration.ofSeconds(5)); operation.dispose();
                settings.update(guild, Map.of("retry", "true")).block(Duration.ofSeconds(5));
                assertEquals("true", settings.get(guild).block().value("saved", ""));
            }
            Flux.range(0, 16).flatMap(index -> settings.get(guild), 16).count().doOnNext(count -> assertEquals(16, count)).block(Duration.ofSeconds(5));
        }
    }
}
