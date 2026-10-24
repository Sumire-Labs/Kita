package com.sumirelabs.kita.eventlog;

import com.sumirelabs.kita.settings.GuildSettings;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public record EventLogConfig(GuildSettings settings) {
    public boolean enabled() { return settings.enabled("eventlog.enabled"); }
    public boolean bots() { return settings.enabled("eventlog.bots"); }
    public boolean bodies() { return bool("bodies", true); }
    public boolean silent() { return bool("silent", true); }
    public boolean action(LogAction action) {
        return enabled() && selected(action);
    }
    public boolean selected(LogAction action) { return bool("action." + action.name(), action != LogAction.VOICE_STATE); }
    public Set<Long> exclusions(String kind) { return ids(kind); }
    public int hours() { return number("hours", 6, Set.of(1, 6, 24)); }
    public long snapshotLimit() { return number("capacity", 64, Set.of(8, 32, 64, 128)) * 1024L * 1024; }
    public long target(LogAction action) {
        long channel = settings.snowflake("eventlog.channel." + action.category());
        return channel == 0 ? settings.snowflake("eventlog.channel") : channel;
    }
    public Set<Long> outputs() {
        var result = new java.util.HashSet<Long>(); result.add(settings.snowflake("eventlog.channel"));
        for (var category : LogAction.Category.values()) result.add(settings.snowflake("eventlog.channel." + category));
        result.remove(0L); return Set.copyOf(result);
    }
    public boolean source(long channel, long parent, long category) {
        var excluded = ids("channels");
        return !outputs().contains(channel) && !outputs().contains(parent) && !excluded.contains(channel)
                && !excluded.contains(parent) && !excluded.contains(category);
    }
    public boolean user(long user, boolean bot, boolean webhook, long self) {
        return user != self && !ids("users").contains(user) && (bots() || !bot && !webhook);
    }
    public boolean captures(long channel, long parent, long category) {
        return enabled() && java.util.stream.Stream.of(LogAction.MESSAGE_DELETE, LogAction.MESSAGE_EDIT, LogAction.MESSAGE_BULK_DELETE)
                .anyMatch(action -> action(action) && target(action) != 0)
                && source(channel, parent, category);
    }
    private Set<Long> ids(String key) {
        return Arrays.stream(settings.value("eventlog.exclude." + key, "").split(",")).filter(value -> value.matches("[0-9]{1,19}"))
                .map(value -> { try { return Long.parseLong(value); } catch (NumberFormatException error) { return 0L; } })
                .filter(value -> value > 0).collect(Collectors.toUnmodifiableSet());
    }
    private boolean bool(String key, boolean fallback) { return Boolean.parseBoolean(settings.value("eventlog." + key, String.valueOf(fallback))); }
    private int number(String key, int fallback, Set<Integer> valid) {
        try { int value = Integer.parseInt(settings.value("eventlog." + key, "")); return valid.contains(value) ? value : fallback; }
        catch (NumberFormatException error) { return fallback; }
    }
}
