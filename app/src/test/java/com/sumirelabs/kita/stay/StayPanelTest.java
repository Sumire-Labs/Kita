package com.sumirelabs.kita.stay;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StayPanelTest {
    @Test void panelUsesComponentsV2WithDistinctSessionControlsAndDiscordTimestamps() {
        var session = new StaySession();
        session.start(123);
        var unlimited = StayPanel.render(session.view());
        assertTrue(unlimited.isUsingComponentsV2());
        var children = unlimited.toData().getArray("components").getObject(0).getArray("components");
        assertTrue(children.getObject(0).getString("content").contains("無期限"));
        var buttons = children.getObject(1).getArray("components");
        assertEquals(3, buttons.length());
        assertEquals("stay:" + session.view().token() + ":duration", buttons.getObject(0).getString("custom_id"));
        for (int i = 0; i < buttons.length(); i++) assertTrue(buttons.getObject(i).getString("custom_id").length() <= 100);
        var now = Instant.parse("2026-10-10T00:00:00Z");
        session.limit(now, Duration.ofMinutes(90));
        var timed = StayPanel.render(session.view()).toData().toString();
        assertTrue(timed.contains("<t:" + now.plusSeconds(5400).getEpochSecond() + ":R>"));
        var modal = StayPanel.modal(session.view().token());
        assertTrue(modal.getId().length() <= 100);
        assertEquals(2, modal.getComponents().size());
        session.stop();
        assertFalse(StayPanel.render(session.view()).toData().toString().contains("custom_id"));
    }
}
