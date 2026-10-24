package com.sumirelabs.kita.logshare;

import static org.junit.jupiter.api.Assertions.*;
import static com.sumirelabs.kita.discord.Payloads.json;
import com.sumirelabs.kita.settings.GuildSettings;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LogSharePolicyTest {
    @Test void automaticSharingIsOptInAndExcludesSelectedChannelsAndForumThreads() {
        var off = new GuildSettings(1, Map.of());
        assertFalse(LogSharePolicy.automatic(off, 10, 0));
        var settings = new GuildSettings(1, Map.of("logshare.enabled", "true", "logshare.auto", "true", "logshare.excludedChannels", "10,20"));
        assertFalse(LogSharePolicy.automatic(settings, 10, 0));
        assertFalse(LogSharePolicy.automatic(settings, 99, 20));
        assertTrue(LogSharePolicy.automatic(settings, 99, 0));
        assertTrue(LogSharePolicy.excluded(settings, 10, 0));
        assertTrue(LogSharePolicy.excluded(settings, 99, 20));
        assertFalse(LogSharePolicy.automatic(new GuildSettings(2, Map.of()), 10, 0));
    }
    @Test void noExclusionsAllowsAllChannelsAndOldAllowListIsNotInverted() {
        var settings = new GuildSettings(1, Map.of("logshare.enabled", "true", "logshare.auto", "true", "logshare.channels", "10"));
        assertTrue(LogSharePolicy.automatic(settings, 10, 0));
        assertTrue(LogSharePolicy.automatic(settings, 20, 0));
        assertFalse(LogSharePolicy.excluded(settings, 10, 0));
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
        assertTrue(message.flags().get().contains(discord4j.core.object.entity.Message.Flag.IS_COMPONENTS_V2)); assertTrue(message.flags().get().contains(discord4j.core.object.entity.Message.Flag.SUPPRESS_NOTIFICATIONS));
        assertTrue(message.embeds().toOptional().orElse(java.util.List.of()).isEmpty());
        assertTrue(json(message).toString().contains("https://mclo.gs/abc123"));
        var children = json(message).get("components").get(0).get("components");
        assertEquals("## LogShare\nファイル名: latest.log", children.get(0).get("content").asText());
        var buttons = children.get(1).get("components");
        assertEquals("mclo.gsでログを開く", buttons.get(0).get("label").asText());
        assertEquals("ログをダウンロード", buttons.get(1).get("label").asText());
        assertEquals("latest.log", LogShareDownloads.filename(buttons.get(1).get("custom_id").asText()));
    }
}
