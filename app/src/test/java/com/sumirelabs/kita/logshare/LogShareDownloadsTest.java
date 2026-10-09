package com.sumirelabs.kita.logshare;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LogShareDownloadsTest {
    @Test void downloadButtonRetainsUsualFileNamesAndFitsDiscordLimits() {
        String id = LogShareDownloads.buttonId("latest.log", "https://mclo.gs/abc123");
        assertEquals("latest.log", LogShareDownloads.filename(id));
        assertTrue(id.length() <= 100);
        String longest = LogShareDownloads.buttonId("a".repeat(100), "https://mclo.gs/" + "a".repeat(32));
        assertTrue(longest.length() <= 100);
        assertEquals("log.log", LogShareDownloads.filename(longest));
        assertEquals("日本語.log", LogShareDownloads.filename(LogShareDownloads.buttonId("日本語.log", "https://mclo.gs/abc")));
        assertFalse(LogShareDownloads.filename(LogShareDownloads.buttonId("../latest.log", "https://mclo.gs/abc")).contains("/"));
    }
    @Test void downloadResponseHasAnActualFileAndIsSilentV2() {
        try (var message = LogShareDownloads.file("latest.log", "日本語のログ".getBytes(StandardCharsets.UTF_8))) {
            assertTrue(message.isUsingComponentsV2()); assertTrue(message.isSuppressedNotifications());
            assertTrue(message.getEmbeds().isEmpty());
            assertEquals(1, message.getFiles().size());
            assertEquals("latest.log", message.getFiles().getFirst().getName());
            assertTrue(message.toData().toString().contains("attachment://latest.log"));
        }
    }
}
