package com.sumirelabs.kita.previews;

import static org.junit.jupiter.api.Assertions.*;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class InstagramProviderTest {
    private static final String URL = "https://www.instagram.com/p/example/";
    @Test void photoPostExtractsContentWithoutAvatarOrCommentNoise() {
        var page = Jsoup.parse("""
                <span class="UsernameText">creator</span>
                <img src="https://scontent.cdninstagram.com/avatar.jpg">
                <img class="EmbeddedMediaImage" src="https://scontent.cdninstagram.com/photo.jpg?a=1&amp;b=2">
                <div class="Caption"><a class="CaptionUsername">creator</a><br>Photo caption
                  <div class="CaptionComments">Unrelated comments</div></div>
                """, URL);
        var preview = InstagramProvider.parse(page, URL);
        assertEquals("creator", preview.author());
        assertEquals("Photo caption", preview.text());
        assertEquals(java.util.List.of("https://scontent.cdninstagram.com/photo.jpg?a=1&b=2"), preview.media());
        assertTrue(PreviewRenderer.render(preview, false).isUsingComponentsV2());
    }
    @Test void loginPageIsNotMistakenForPostContent() {
        var page = Jsoup.parse("<title>Instagram login</title><img src='https://scontent.cdninstagram.com/logo.png'>", URL);
        assertThrows(IllegalStateException.class, () -> InstagramProvider.parse(page, URL));
    }
    @Test void rejectsUnexpectedMediaHosts() {
        var page = Jsoup.parse("<img class='EmbeddedMediaImage' src='https://cdninstagram.com.evil.test/a.jpg'>", URL);
        assertThrows(IllegalStateException.class, () -> InstagramProvider.parse(page, URL));
    }
}
