package com.sumirelabs.kita.previews;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;

public final class RedditProvider implements PreviewProvider {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final PreviewProvider fallback = new RedditEmbedProvider();

    @Override public Preview fetch(SocialLink link) throws Exception {
        try { return fetchJson(link); }
        catch (Exception ignored) { return fallback.fetch(link); }
    }

    private Preview fetchJson(SocialLink link) throws Exception {
        var uri = link.uri();
        if (uri.getHost().equals("redd.it")) uri = URI.create("https://www.reddit.com/comments/" + uri.getPath().substring(1));
        uri = URI.create("https://www.reddit.com" + uri.getPath().replaceAll("/+$", "") + ".json?raw_json=1&limit=1");
        var response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Kita Discord bot/1.0").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            if (response.statusCode() != 200) throw new IllegalStateException("Reddit JSON HTTP " + response.statusCode());
            var root = json.readTree(body.readNBytes(2_097_152));
            var post = root.path(0).path("data").path("children").path(0).path("data");
            if (post.isMissingNode()) throw new IllegalArgumentException("Reddit post not found");
            var media = new ArrayList<String>();
            var video = post.path("secure_media").path("reddit_video").path("fallback_url").asText();
            if (video.startsWith("https://")) media.add(video);
            for (var item : post.path("gallery_data").path("items")) {
                var metadata = post.path("media_metadata").path(item.path("media_id").asText()).path("s");
                var url = metadata.path("u").asText(metadata.path("gif").asText());
                if (url.startsWith("https://") && media.size() < 10) media.add(url);
            }
            if (media.isEmpty()) {
                var image = post.path("preview").path("images").path(0).path("source").path("url").asText();
                if (image.startsWith("https://")) media.add(image);
            }
            var comment = root.path(1).path("data").path("children").path(0).path("data");
            boolean linkedComment = link.uri().getPath().split("/").length >= 7;
            String context = linkedComment ? comment.path("author").asText() + ": " + comment.path("body").asText() : "";
            return new Preview("Reddit", "u/" + post.path("author").asText(),
                    post.path("title").asText() + "\n" + post.path("selftext").asText(), link.uri().toString(), media,
                    context, post.path("over_18").asBoolean() || post.path("spoiler").asBoolean())
                    .withEngagement(EngagementParser.reddit(post));
        }
    }
}
