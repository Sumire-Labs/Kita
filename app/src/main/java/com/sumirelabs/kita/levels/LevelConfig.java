package com.sumirelabs.kita.levels;

import com.sumirelabs.kita.settings.GuildSettings;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public record LevelConfig(boolean enabled, boolean chat, boolean voice, int chatXp, int cooldown, int voiceXp,
                          boolean mutedVoice, boolean stackRewards, String notice, long noticeChannel,
                          Set<String> channels, Set<String> roles) {
    public static LevelConfig from(GuildSettings settings) {
        return new LevelConfig(settings.enabled("levels.enabled"), bool(settings, "chat", true), bool(settings, "voice", true),
                number(settings, "chatXp", 20, 1, 10_000), number(settings, "cooldown", 60, 10, 3600),
                number(settings, "voiceXp", 5, 1, 10_000), bool(settings, "mutedVoice", true),
                !settings.value("levels.rewardsMode", "stack").equals("highest"), settings.value("levels.notice", "rewards"),
                settings.snowflake("levels.noticeChannel"), ids(settings, "levels.channels"), ids(settings, "levels.roles"));
    }
    private static boolean bool(GuildSettings settings, String key, boolean fallback) {
        return Boolean.parseBoolean(settings.value("levels." + key, String.valueOf(fallback)));
    }
    private static int number(GuildSettings settings, String key, int fallback, int min, int max) {
        try { return Math.clamp(Integer.parseInt(settings.value("levels." + key, "")), min, max); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    public static Set<String> ids(GuildSettings settings, String key) {
        return Arrays.stream(settings.value(key, "").split(",")).filter(id -> id.matches("[0-9]+"))
                .collect(Collectors.toUnmodifiableSet());
    }
    public boolean channel(long id, long parent) { return !channels.contains(String.valueOf(id)) && !channels.contains(String.valueOf(parent)); }
    public boolean member(Set<String> memberRoles) { return memberRoles.stream().noneMatch(roles::contains); }
}
