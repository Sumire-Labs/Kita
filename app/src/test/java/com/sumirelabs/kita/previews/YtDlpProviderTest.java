package com.sumirelabs.kita.previews;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.junit.jupiter.api.Test;

class YtDlpProviderTest {
    private final ObjectMapper json = new ObjectMapper();
    private final SocialLink link = new SocialLink("youtube", URI.create("https://youtu.be/example"));
    @Test void selectsPlayableCombinedVideoOverThumbnail() throws Exception {
        var data = json.readTree("""
                {"title":"clip","thumbnail":"https://example.com/poster.jpg","formats":[
                  {"ext":"mp4","vcodec":"h264","acodec":"none","protocol":"https","height":720,"url":"https://example.com/silent.mp4"},
                  {"ext":"mp4","vcodec":"h264","acodec":"aac","protocol":"https","height":720,"url":"https://example.com/clip.mp4"}]}
                """);
        assertEquals(java.util.List.of("https://example.com/clip.mp4"), YtDlpProvider.parse(data, link).media());
    }
    @Test void refusesEmptyExtractionAndKeepsValidPosterFallback() throws Exception {
        assertThrows(IllegalStateException.class, () -> YtDlpProvider.parse(json.readTree("{}"), link));
        var preview = YtDlpProvider.parse(json.readTree("{\"thumbnail\":\"https://example.com/poster.jpg\"}"), link);
        assertEquals(java.util.List.of("https://example.com/poster.jpg"), preview.media());
        assertFalse(preview.replacesOriginal());
    }
}
