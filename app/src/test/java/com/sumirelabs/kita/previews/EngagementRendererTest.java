package com.sumirelabs.kita.previews;

import static org.junit.jupiter.api.Assertions.*;
import static com.sumirelabs.kita.previews.Engagement.Metric.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EngagementRendererTest {
    private Preview preview(String platform, Map<Engagement.Metric, Long> counts) {
        return new Preview(platform, "author", "caption", "https://example.com/post", List.of(), "", false)
                .withEngagement(new Engagement(counts));
    }

    @Test void platformSpecificRowsUseAppropriateLabels() {
        var youtube = EngagementRenderer.render(preview("youtube", Map.of(LIKES, 12500L, COMMENTS, 0L, VIEWS, 1500000L)));
        assertTrue(youtube.contains("👍 高評価 **12.5K**"));
        assertTrue(youtube.contains("💬 コメント **0**"));
        assertTrue(youtube.contains("▶ 再生 **1.5M**"));
        assertFalse(youtube.contains("リポスト"));
        var twitch = EngagementRenderer.render(preview("twitch", Map.of(VIEWS, 123L)));
        assertEquals("▶ 再生 **123**", twitch);
        var reddit = EngagementRenderer.render(preview("reddit", Map.of(SCORE, -12L)));
        assertTrue(reddit.contains("⬆ スコア **-12**"));
        assertFalse(reddit.contains("いいね"));
    }

    @Test void fallbackUsesUnknownInsteadOfFalseZerosForEverySupportedPlatform() {
        for (var platform : List.of("x", "instagram", "tiktok", "reddit", "youtube", "twitch")) {
            var row = EngagementRenderer.render(preview(platform, Map.of()));
            assertTrue(row.contains("**—**"), platform);
            assertFalse(row.contains("**0**"), platform);
        }
        assertTrue(EngagementRenderer.render(preview("tiktok", Map.of(SHARES, 5L))).contains("↗ シェア **5**"));
    }

    @Test void engagementIsInTheSilentComponentsV2PayloadAndDoesNotChangeReplacementQuality() {
        var original = new Preview("X", "author", "caption", "https://x.com/example/status/1", List.of(), "", false, false);
        var counted = original.withEngagement(new Engagement(Map.of(LIKES, 42L, REPOSTS, 7L)));
        var message = PreviewRenderer.render(counted, false);
        var payload = message.toData().toString();
        assertTrue(message.isUsingComponentsV2());
        assertTrue(message.isSuppressedNotifications());
        assertFalse(message.toData().hasKey("embeds"));
        assertTrue(payload.contains("いいね **42**"));
        assertTrue(payload.contains("リポスト **7**"));
        assertFalse(counted.replacesOriginal());
    }
}
