package com.sumirelabs.kita.previews;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class PreviewReplacementTest {
    private final SocialLink instagram = new SocialLink("instagram", URI.create("https://www.instagram.com/p/example/"));
    private final Preview content = new Preview("Instagram", "author", "caption", instagram.uri().toString(),
            List.of("https://scontent.cdninstagram.com/photo.jpg"), "", false);
    @Test void suppressionHappensOnlyAfterAcceptedReply() {
        var events = new ArrayList<String>(); var replacement = new PreviewReplacement(Map.of("instagram", link -> content));
        assertEquals(1, replacement.replace(List.of(instagram), true, preview -> Mono.fromRunnable(() -> events.add("reply")),
                (link, error) -> Mono.error(error), () -> Mono.fromRunnable(() -> events.add("suppress"))).block());
        assertEquals(List.of("reply", "suppress"), events);
    }
    @Test void fetchFailurePreservesOriginal() {
        var replacement = new PreviewReplacement(Map.of("instagram", link -> { throw new IllegalStateException("unavailable"); }));
        var failures = new ArrayList<Throwable>();
        assertEquals(0, replacement.replace(List.of(instagram), true, preview -> fail("No reply expected"),
                (link, error) -> Mono.fromRunnable(() -> failures.add(error)), () -> fail("Original must remain visible")).block());
        assertEquals(1, failures.size());
    }
    @Test void rejectedDiscordReplyDoesNotSuppress() {
        var replacement = new PreviewReplacement(Map.of("instagram", link -> content)); var failures = new ArrayList<Throwable>();
        assertEquals(0, replacement.replace(List.of(instagram), true, preview -> Mono.error(new IllegalStateException("403")),
                (link, error) -> Mono.fromRunnable(() -> failures.add(error)), () -> fail("Original must remain visible")).block());
        assertEquals(1, failures.size());
    }
    @Test void partialSuccessPreservesOtherPlatformsPreviews() {
        var other = new SocialLink("youtube", URI.create("https://youtu.be/example"));
        var replacement = new PreviewReplacement(Map.of("instagram", link -> content, "youtube", link -> { throw new IllegalStateException("unavailable"); }));
        assertEquals(1, replacement.replace(List.of(instagram, other), true, preview -> Mono.empty(), (link, error) -> Mono.empty(),
                () -> fail("Failed platform's original must remain visible")).block());
    }
    @Test void unmanagedLinksAndEmptyResultsNeverTriggerSuppression() {
        var replacement = new PreviewReplacement(Map.of("instagram", link -> content));
        replacement.replace(List.of(instagram), false, preview -> Mono.empty(), (link, error) -> Mono.error(error),
                () -> fail("Unmanaged link would lose its preview")).block();
        var empty = new Preview("Instagram", "", "", instagram.uri().toString(), List.of(), "", false);
        var noContent = new PreviewReplacement(Map.of("instagram", link -> empty));
        assertEquals(0, noContent.replace(List.of(instagram), true, preview -> fail("Empty preview"), (link, error) -> Mono.empty(),
                () -> fail("Empty preview must not replace original")).block());
        assertEquals(2, SocialLink.urlCount("https://www.instagram.com/p/example/ http://example.com/article"));
    }
    @Test void posterOnlyFallbackKeepsOriginalVideoAvailable() {
        var poster = new Preview("YouTube", "author", "title", "https://youtu.be/example", List.of("https://example.com/poster.jpg"), "", false, false);
        var replacement = new PreviewReplacement(Map.of("instagram", link -> poster));
        assertEquals(1, replacement.replace(List.of(instagram), true, preview -> Mono.empty(), (link, error) -> Mono.error(error),
                () -> fail("Poster-only fallback must preserve the original player")).block());
    }
}
