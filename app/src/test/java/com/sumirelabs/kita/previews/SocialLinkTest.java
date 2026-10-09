package com.sumirelabs.kita.previews;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SocialLinkTest {
    @Test void restrictsHostsAndRejectsCredentialsOrPorts() {
        assertTrue(SocialLink.find("https://x.com.evil.test/u/status/1").isEmpty());
        assertTrue(SocialLink.find("https://u:p@x.com/u/status/1").isEmpty());
        assertTrue(SocialLink.find("https://x.com:443/u/status/1").isEmpty());
        assertTrue(SocialLink.find("http://x.com/u/status/1").isEmpty());
    }
    @Test void extractsSpoilerAndAngleBracketLinksAndDeduplicates() {
        var links = SocialLink.find("||https://x.com/u/status/1|| <https://www.reddit.com/r/test/comments/abc> https://x.com/u/status/1");
        assertEquals(2, links.size()); assertEquals("x", links.getFirst().platform());
        assertEquals("https://x.com/u/status/1", links.getFirst().uri().toString());
    }
    @Test void rendererUsesSilentV2WithoutMentions() {
        var data = PreviewRenderer.render(new Preview("X", "author", "text", "https://x.com/u/status/1",
                java.util.List.of("https://example.com/video.mp4"), "context", true), false);
        assertTrue(data.isUsingComponentsV2());
        assertTrue(data.isSuppressedNotifications());
        assertTrue(data.getAllowedMentions().isEmpty());
        assertFalse(data.toData().hasKey("embeds"));
        assertTrue(data.toData().toString().contains("\"spoiler\":true"));
    }
}
