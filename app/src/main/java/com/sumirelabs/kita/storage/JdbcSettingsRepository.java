package com.sumirelabs.kita.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sumirelabs.kita.settings.GuildSettings;
import com.sumirelabs.kita.settings.SettingsRepository;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;

public final class JdbcSettingsRepository implements SettingsRepository {
    private final DataSource source;
    private final ObjectMapper json = new ObjectMapper();
    private static final TypeReference<Map<String, String>> TYPE = new TypeReference<>() {};

    public JdbcSettingsRepository(DataSource source) { this.source = source; }

    @Override public GuildSettings get(long guildId) {
        try (var connection = source.getConnection()) { return read(connection, guildId, false); }
        catch (Exception error) { throw new IllegalStateException("Cannot read guild settings", error); }
    }

    @Override public GuildSettings update(long guildId, Map<String, String> changes) {
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var insert = connection.prepareStatement(
                        "INSERT INTO guild_settings (guild_id, settings) VALUES (?, '{}') ON DUPLICATE KEY UPDATE guild_id = VALUES(guild_id)")) {
                    insert.setLong(1, guildId);
                    insert.executeUpdate();
                }
                var values = new HashMap<>(read(connection, guildId, true).values());
                values.putAll(changes);
                try (var update = connection.prepareStatement(
                        "UPDATE guild_settings SET settings = ? WHERE guild_id = ?")) {
                    update.setString(1, json.writeValueAsString(values));
                    update.setLong(2, guildId);
                    update.executeUpdate();
                }
                connection.commit();
                return new GuildSettings(guildId, values);
            } catch (Exception error) { connection.rollback(); throw error; }
        } catch (Exception error) { throw new IllegalStateException("Cannot update guild settings", error); }
    }

    private GuildSettings read(Connection connection, long guildId, boolean lock) throws Exception {
        try (var query = connection.prepareStatement(
                "SELECT settings FROM guild_settings WHERE guild_id = ?" + (lock ? " FOR UPDATE" : ""))) {
            query.setLong(1, guildId);
            try (var rows = query.executeQuery()) {
                return new GuildSettings(guildId, rows.next() ? json.readValue(rows.getString(1), TYPE) : Map.of());
            }
        }
    }
}
