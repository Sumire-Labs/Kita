package com.sumirelabs.kita.eventlog;

import static org.junit.jupiter.api.Assertions.*;
import com.sumirelabs.kita.settings.GuildSettings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventLogRulesTest {
    static EventLogConfig config(Map<String, String> values) { return new EventLogConfig(new GuildSettings(1, values)); }
    static LogEntry entry(LogAction action, String text) {
        return new LogEntry("event", 1, 10, 11, 12, 20, 21, false, false, false, action, 1000,
                List.of(new LogEntry.Field("変更前", text)), "", "deleted.txt", text);
    }
    @Test void defaultsAreSixHoursBoundedAndExcludeBots() {
        var config = config(Map.of());
        assertFalse(config.enabled()); assertFalse(config.bots()); assertTrue(config.bodies()); assertTrue(config.silent());
        assertEquals(6, config.hours()); assertEquals(64 * 1024 * 1024, config.snapshotLimit());
        assertTrue(config.selected(LogAction.MESSAGE_DELETE)); assertFalse(config.selected(LogAction.VOICE_STATE));
    }
    @Test void outputChannelsParentCategoriesAndUsersAreExcluded() {
        var config = config(Map.of("eventlog.enabled", "true", "eventlog.channel", "100",
                "eventlog.channel.MESSAGES", "101", "eventlog.exclude.channels", "12,13",
                "eventlog.exclude.users", "1423456789012345678"));
        assertFalse(config.source(100, 0, 0)); assertFalse(config.source(200, 101, 0)); assertFalse(config.source(200, 0, 12));
        assertFalse(config.user(1423456789012345678L, false, false, 99));
        assertFalse(config.user(99, false, false, 99)); assertFalse(config.user(42, true, false, 99)); assertFalse(config.user(42, false, true, 99));
        assertEquals(101, config.target(LogAction.MESSAGE_DELETE)); assertEquals(100, config.target(LogAction.BAN));
    }
    @Test void bodyRedactionRemovesContentAndFilesButPreservesNonMessageDiffs() {
        var message = entry(LogAction.MESSAGE_DELETE, "private-text").withoutBodies();
        assertFalse(message.encode().contains("private-text")); assertEquals("", message.fileText());
        assertTrue(entry(LogAction.ROLE_UPDATE, "permission-diff").withoutBodies().encode().contains("permission-diff"));
    }
    @Test void permissionAndExclusionChecksApplyToActorAndSubject() {
        var config = config(Map.of("eventlog.enabled", "true", "eventlog.exclude.users", "21"));
        assertFalse(EventLogService.allowed(config, entry(LogAction.MESSAGE_EDIT, "body"), 99));
        assertFalse(EventLogService.allowed(config(Map.of("eventlog.enabled", "true")), entry(LogAction.MESSAGE_EDIT, "body"), 20));
        assertFalse(EventLogService.allowed(config(Map.of()), entry(LogAction.MESSAGE_EDIT, "body"), 99));
    }
    @Test void userIdInputAcceptsMentionsAndRejectsOverflow() {
        assertEquals("123,456", EventLogSettingsListener.users("<@123>, <@!456> 123"));
        assertEquals("", EventLogSettingsListener.users(""));
        assertThrows(IllegalArgumentException.class, () -> EventLogSettingsListener.users("9999999999999999999"));
        assertThrows(IllegalArgumentException.class, () -> EventLogSettingsListener.users("not-an-id"));
    }
}