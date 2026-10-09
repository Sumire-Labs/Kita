package com.sumirelabs.kita.logshare;

import static org.junit.jupiter.api.Assertions.*;
import com.sumirelabs.kita.settings.GuildSettings;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LogSharePolicyTest {
    @Test void automaticSharingIsOptInAndChannelScopedIncludingForumThreads() {
        var off = new GuildSettings(1, Map.of());
        assertFalse(LogSharePolicy.automatic(off, 10, 0));
        var settings = new GuildSettings(1, Map.of("logshare.enabled", "true", "logshare.auto", "true", "logshare.channels", "10,20"));
        assertTrue(LogSharePolicy.automatic(settings, 10, 0));
        assertTrue(LogSharePolicy.automatic(settings, 99, 20));
        assertFalse(LogSharePolicy.automatic(settings, 99, 0));
        assertFalse(LogSharePolicy.automatic(new GuildSettings(2, Map.of()), 10, 0));
    }
    @Test void otherPeoplesLogsRequireAnExplicitRoleOrServerManagement() {
        var settings = new GuildSettings(1, Map.of("logshare.roles", "5,6"));
        assertTrue(LogSharePolicy.allowed(1, 1, false, Set.of(), settings));
        assertTrue(LogSharePolicy.allowed(2, 1, true, Set.of(), settings));
        assertTrue(LogSharePolicy.allowed(2, 1, false, Set.of("6"), settings));
        assertFalse(LogSharePolicy.allowed(2, 1, false, Set.of("7"), settings));
        assertFalse(LogSharePolicy.allowed(2, 1, false, Set.of(), settings));
    }
    @Test void responseUsesSilentComponentsAndNoEmbeds() {
        var message = LogShareMessages.shared("latest.log", "https://mclo.gs/abc123");
        assertTrue(message.isUsingComponentsV2()); assertTrue(message.isSuppressedNotifications());
        assertTrue(message.getEmbeds().isEmpty());
        assertTrue(message.toData().toString().contains("https://mclo.gs/abc123"));
    }
}
