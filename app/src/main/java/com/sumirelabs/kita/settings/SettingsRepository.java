package com.sumirelabs.kita.settings;

import java.util.Map;
import reactor.core.publisher.Mono;

public interface SettingsRepository {
    Mono<GuildSettings> get(long guildId);
    Mono<GuildSettings> update(long guildId, Map<String, String> changes);
    Mono<GuildSettings> modify(long guildId, java.util.function.Function<GuildSettings, Map<String, String>> changes);
}
