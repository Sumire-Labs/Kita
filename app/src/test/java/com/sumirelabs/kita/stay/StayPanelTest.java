package com.sumirelabs.kita.stay;

import static org.junit.jupiter.api.Assertions.*;
import static com.sumirelabs.kita.discord.Payloads.json;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StayPanelTest {
    @Test void panelUsesComponentsV2WithDistinctSessionControlsAndDiscordTimestamps() {
        var session = new StaySession();
        session.start(123);
        var unlimited = StayPanel.render(session.view());
        assertTrue(unlimited.flags().get().contains(discord4j.core.object.entity.Message.Flag.IS_COMPONENTS_V2));
        var children = json(unlimited).get("components").get(0).get("components");
        assertTrue(children.get(0).get("content").asText().contains("無期限"));
        var buttons = children.get(1).get("components");
        assertEquals(3, buttons.size());
        assertEquals("stay:" + session.view().token() + ":duration", buttons.get(0).get("custom_id").asText());
        for (int i = 0; i < buttons.size(); i++) assertTrue(buttons.get(i).get("custom_id").asText().length() <= 100);
        var now = Instant.parse("2026-10-10T00:00:00Z");
        session.limit(now, Duration.ofMinutes(90));
        var timed = json(StayPanel.render(session.view())).toString();
        assertTrue(timed.contains("<t:" + now.plusSeconds(5400).getEpochSecond() + ":R>"));
        var modal = StayPanel.modal(session.view().token());
        assertTrue(modal.customId().get().length() <= 100);
        assertEquals(2, modal.components().get().size());
        session.stop();
        assertFalse(json(StayPanel.render(session.view())).toString().contains("custom_id"));
    }
}
