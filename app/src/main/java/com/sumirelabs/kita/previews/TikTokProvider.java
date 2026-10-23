package com.sumirelabs.kita.previews;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public final class TikTokProvider implements PreviewProvider {
    private final PreviewProvider videos;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper json = JsonMapper.builderWithJackson2Defaults().build();
    public TikTokProvider(PreviewProvider videos) { this.videos = videos; }

    @Override public Preview fetch(SocialLink link) throws Exception {
        try { return videos.fetch(link); }
        catch (Exception ignored) { /* The public oEmbed endpoint can still provide a poster and caption. */ }
        var uri = URI.create("https://www.tiktok.com/oembed?url=" + URLEncoder.encode(link.uri().toString(), StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).header("User-Agent", "Discordbot/2.0")
                .header("Accept", "application/json").GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            if (response.statusCode() != 200) throw new IllegalStateException("TikTok public preview HTTP " + response.statusCode());
            return parse(json.readTree(body.readNBytes(1_048_576)), link.uri().toString());
        }
    }

    static Preview parse(JsonNode data, String url) {
        var thumbnail = data.path("thumbnail_url").asString();
        if (!thumbnail.startsWith("https://")) throw new IllegalStateException("TikTok public preview contains no poster");
        return new Preview("TikTok", data.path("author_name").asString(), data.path("title").asString(), url,
                List.of(thumbnail), "プレビュー画像です。動画は投稿元で再生できます。", false, false)
                .withEngagement(EngagementParser.video(data, "tiktok"));
    }
}
