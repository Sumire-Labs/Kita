package com.sumirelabs.kita.previews;

import static org.junit.jupiter.api.Assertions.*;
import static com.sumirelabs.kita.previews.Engagement.Metric.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class EngagementParserTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void twitterKeepsRealZeroSeparateFromUnavailableCounts() throws Exception {
        var counts = EngagementParser.twitter(json.readTree("""
                {"likes":0,"retweets":12,"replies":"1,234","views":null,"quotes":7,"bookmarks":9}
                """)).values();
        assertEquals(0L, counts.get(LIKES));
        assertEquals(12L, counts.get(REPOSTS));
        assertEquals(1234L, counts.get(REPLIES));
        assertEquals(7L, counts.get(QUOTES));
        assertEquals(9L, counts.get(SAVES));
        assertFalse(counts.containsKey(VIEWS));
    }

    @Test void videoCountersPreserveTikTokShareSemantics() throws Exception {
        var data = json.readTree("""
                {"like_count":25,"comment_count":3,"view_count":1000,"save_count":2,"repost_count":8}
                """);
        var counts = EngagementParser.video(data, "tiktok").values();
        assertEquals(8L, counts.get(SHARES));
        assertFalse(counts.containsKey(REPOSTS));
        assertEquals(1000L, counts.get(VIEWS));
        assertEquals(2L, counts.get(SAVES));
    }

    @Test void instagramExtractsOnlyDedicatedVisibleCounters() {
        var page = Jsoup.parse("""
                <span class="UsernameText">creator123</span>
                <div class="LikeCount">17,229 likes</div>
                <a class="CaptionCommentsExpand">View all 483 comments</a>
                <span class="ViewCount">1.2M views</span>
                """);
        var counts = EngagementParser.instagram(page).values();
        assertEquals(17229L, counts.get(LIKES));
        assertEquals(483L, counts.get(COMMENTS));
        assertEquals(1_200_000L, counts.get(VIEWS));
        assertFalse(counts.containsKey(REPOSTS));
    }

    @Test void hiddenAndInvalidValuesDoNotBecomeInventedZeros() throws Exception {
        var counts = EngagementParser.video(json.readTree("""
                {"like_count":-1,"view_count":"unknown","comment_count":false,"save_count":1.5}
                """), "youtube").values();
        assertTrue(counts.isEmpty());
        assertTrue(EngagementParser.instagram(Jsoup.parse("<div class='LikeCount'>Hidden</div>")).values().isEmpty());
    }

    @Test void redditAllowsNegativeScoreAndRespectsScoreHiding() throws Exception {
        var visible = EngagementParser.reddit(json.readTree("{\"score\":-5,\"num_comments\":0,\"num_crossposts\":2}"));
        assertEquals(-5L, visible.values().get(SCORE));
        assertEquals(0L, visible.values().get(COMMENTS));
        assertEquals(2L, visible.values().get(CROSSPOSTS));
        var hidden = json.readTree("{\"score\":0,\"score_hidden\":true}");
        assertFalse(EngagementParser.redditEmbed(hidden, Jsoup.parse("<shreddit-post score='5'>")).values().containsKey(SCORE));
    }
}
