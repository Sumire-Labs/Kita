package com.sumirelabs.kita.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sumirelabs.kita.settings.GuildSettings;
import com.sumirelabs.kita.settings.SettingsRepository;
import io.r2dbc.spi.ConnectionFactory;
import java.util.HashMap;
import java.util.Map;
import reactor.core.publisher.Mono;

public final class R2dbcSettingsRepository implements SettingsRepository {
    private final Sql sql;
    private final ObjectMapper json = new ObjectMapper();
    private static final TypeReference<Map<String, String>> TYPE = new TypeReference<>() {};
    public R2dbcSettingsRepository(ConnectionFactory source) { sql = new Sql(source); }
    @Override public Mono<GuildSettings> get(long guild) {
        return sql.one("SELECT settings FROM guild_settings WHERE guild_id = ?", row -> decode(guild, row.get("settings", String.class)), guild)
                .defaultIfEmpty(new GuildSettings(guild, Map.of()));
    }
    @Override public Mono<GuildSettings> update(long guild, Map<String, String> changes) {
        return modify(guild, old -> changes);
    }
    @Override public Mono<GuildSettings> modify(long guild, java.util.function.Function<GuildSettings, Map<String, String>> changes) {
        return sql.transaction(connection -> Sql.update(connection,
                "INSERT INTO guild_settings (guild_id, settings) VALUES (?, '{}') ON DUPLICATE KEY UPDATE guild_id = VALUES(guild_id)", guild)
                .then(Sql.rows(connection, "SELECT settings FROM guild_settings WHERE guild_id = ? FOR UPDATE",
                        row -> decode(guild, row.get("settings", String.class)), guild).single())
                .flatMap(old -> {
                    var merged = new HashMap<>(old.values()); merged.putAll(changes.apply(old));
                    return Mono.fromCallable(() -> json.writeValueAsString(merged))
                            .flatMap(encoded -> Sql.update(connection, "UPDATE guild_settings SET settings = ? WHERE guild_id = ?", encoded, guild))
                            .thenReturn(new GuildSettings(guild, merged));
                }));
    }
    private GuildSettings decode(long guild, String text) {
        try { return new GuildSettings(guild, json.readValue(text, TYPE)); }
        catch (Exception error) { throw new IllegalStateException("Cannot decode guild settings", error); }
    }
}
