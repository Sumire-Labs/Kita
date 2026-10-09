package com.sumirelabs.kita.settings;

import java.util.Map;

public record GuildSettings(long guildId, Map<String, String> values) {
    public GuildSettings { values = Map.copyOf(values); }

    public String value(String key, String fallback) { return values.getOrDefault(key, fallback); }

    public boolean enabled(String key) { return Boolean.parseBoolean(value(key, "false")); }

    public long snowflake(String key) {
        try { return Long.parseLong(value(key, "0")); }
        catch (NumberFormatException ignored) { return 0; }
    }
}
