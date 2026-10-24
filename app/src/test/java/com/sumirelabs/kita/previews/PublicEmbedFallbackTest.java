package com.sumirelabs.kita.previews;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class PublicEmbedFallbackTest {
    private final ObjectMapper json = new ObjectMapper();
    @Test void redditEmbedFallbackPreservesCaptionAndPostPoster() throws Exception {
        var page = Jsoup.parse("""
                <img src="https://example.com/avatar.jpg">
                <shreddit-player poster="https://preview.redd.it/post.webp"></shreddit-player>
                <img class="preview-image" src="https://preview.redd.it/post.webp">
                """, "https://www.redditmedia.com/");
        var metadata = json.readTree("{\"title\":\"Post title\",\"author_name\":\"author\"}");
        var preview = RedditEmbedProvider.parse(metadata, page, "https://www.reddit.com/comments/example/");
        assertEquals("u/author", preview.author());
        assertEquals(java.util.List.of("https://preview.redd.it/post.webp"), preview.media());
        assertEquals("Post title", preview.text());
    }
    @Test void tiktokOEmbedProvidesPosterWithoutPretendingItIsVideo() throws Exception {
        var metadata = json.readTree("{\"author_name\":\"author\",\"title\":\"Caption\",\"thumbnail_url\":\"https://example.com/poster.jpg\"}");
        var preview = TikTokProvider.parse(metadata, "https://www.tiktok.com/@author/video/123");
        assertEquals(java.util.List.of("https://example.com/poster.jpg"), preview.media());
        assertThrows(IllegalStateException.class, () -> TikTokProvider.parse(json.readTree("{}"), preview.url()));
    }
}
