package com.sumirelabs.kita.logshare;

import static org.junit.jupiter.api.Assertions.*;
import static com.sumirelabs.kita.discord.Payloads.json;
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
        { var message = LogShareDownloads.file("latest.log", "日本語のログ".getBytes(StandardCharsets.UTF_8));
            assertTrue(message.flags().get().contains(discord4j.core.object.entity.Message.Flag.IS_COMPONENTS_V2)); assertTrue(message.flags().get().contains(discord4j.core.object.entity.Message.Flag.SUPPRESS_NOTIFICATIONS));
            assertTrue(message.embeds().toOptional().orElse(java.util.List.of()).isEmpty());
            assertEquals(1, message.files().size());
            assertEquals("latest.log", message.files().getFirst().name());
            assertTrue(json(message).toString().contains("attachment://latest.log"));
        }
    }
}
