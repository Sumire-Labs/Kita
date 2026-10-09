package com.sumirelabs.kita.settings;

import java.util.Map;

public interface SettingsRepository {
    GuildSettings get(long guildId);
    GuildSettings update(long guildId, Map<String, String> changes);
}
