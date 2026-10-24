package com.sumirelabs.kita.eventlog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sumirelabs.kita.settings.GuildSettings;
import com.sumirelabs.kita.settings.SettingsRepository;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class EventLogServiceTest {
    @Test void bodyDisabledStillKeepsMetadataButNeverPersistsBodyInOutbox() {
        var settings = mock(SettingsRepository.class); var gateway = mock(GatewayDiscordClient.class);
        var repository = mock(EventLogRepository.class);
        when(gateway.getSelfId()).thenReturn(Snowflake.of(99));
        var config = new GuildSettings(1, Map.of("eventlog.enabled", "true", "eventlog.channel", "100", "eventlog.bodies", "false"));
        when(settings.get(1)).thenReturn(Mono.just(config));
        when(repository.enqueue(any(), eq(100L), anyLong())).thenReturn(Mono.just(true));
        assertTrue(new EventLogConfig(config).captures(10, 0, 0));
        try (var service = new EventLogService(gateway, settings, repository)) {
            var entry = EventLogRulesTest.entry(LogAction.MESSAGE_DELETE, "must-not-persist");
            service.emit(entry).block(Duration.ofSeconds(2));
            var saved = ArgumentCaptor.forClass(LogEntry.class);
            verify(repository).enqueue(saved.capture(), eq(100L), eq(entry.time() + 21_600_000L));
            assertFalse(saved.getValue().encode().contains("must-not-persist"));
        }
    }
    @Test void excludedActorsDoNotEnterOutbox() {
        var settings = mock(SettingsRepository.class); var gateway = mock(GatewayDiscordClient.class); var repository = mock(EventLogRepository.class);
        when(gateway.getSelfId()).thenReturn(Snowflake.of(99));
        when(settings.get(1)).thenReturn(Mono.just(new GuildSettings(1, Map.of("eventlog.enabled", "true", "eventlog.channel", "100", "eventlog.exclude.users", "21"))));
        try (var service = new EventLogService(gateway, settings, repository)) {
            service.emit(EventLogRulesTest.entry(LogAction.BAN, "reason")).block(Duration.ofSeconds(2));
            verifyNoInteractions(repository);
        }
    }
}
