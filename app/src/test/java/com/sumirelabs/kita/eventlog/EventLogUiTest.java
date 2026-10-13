package com.sumirelabs.kita.eventlog;

import static org.junit.jupiter.api.Assertions.*;
import static com.sumirelabs.kita.discord.Payloads.json;
import com.sumirelabs.kita.settings.GuildSettings;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventLogUiTest {
    @Test void allPagesFitV2LimitsAndArePrivatePanelFriendly() {
        var pages = new java.util.ArrayList<String>(); pages.add("home"); pages.add("storage");
        for (var category : LogAction.Category.values()) pages.add(category.name());
        for (var page : pages) {
            var body = json(EventLogSettings.render(new GuildSettings(1, Map.of()), 2, page, "status"));
            assertEquals(36864, body.path("flags").asInt()); assertTrue(body.toString().contains("eventlog-settings:2:"));
            assertTrue(body.toString().split("\"type\":").length - 1 <= 40); assertFalse(body.has("embeds"));
        }
    }
    @Test void contentNeverMentionsUsersAndBodySwitchAlsoSuppressesFile() {
        var entry = EventLogRulesTest.entry(LogAction.MESSAGE_EDIT, "@everyone **private**");
        var config = EventLogRulesTest.config(Map.of());
        var body = json(EventLogMessages.render(entry, config));
        assertTrue(body.path("allowed_mentions").path("parse").isEmpty()); assertFalse(body.path("allowed_mentions").path("replied_user").asBoolean());
        assertTrue(body.toString().contains("＠everyone")); assertEquals(1, EventLogMessages.render(entry, config).files().size());
        var hidden = EventLogMessages.render(entry, EventLogRulesTest.config(Map.of("eventlog.bodies", "false")));
        assertFalse(json(hidden).toString().contains("private")); assertTrue(hidden.files().isEmpty());
    }
    @Test void longEditsShowBothSidesAndAttachFullText() {
        var old = new MessageSnapshot(1, 2, 0, 0, 3, 4, "user", false, false, "before".repeat(600), java.util.List.of(), 1, 1, 2, false);
        var current = new MessageSnapshot(1, 2, 0, 0, 3, 4, "user", false, false, "after".repeat(700), java.util.List.of(), 1, 2, 3, false);
        var entry = MessageLogEntries.edit(old, current);
        var rendered = json(EventLogMessages.render(entry, EventLogRulesTest.config(Map.of()))).toString();
        assertTrue(rendered.contains("before")); assertTrue(rendered.contains("after"));
        assertTrue(entry.fileText().contains(current.content()));
        assertTrue(entry.withoutBodies().fileText().isEmpty());
    }
}
