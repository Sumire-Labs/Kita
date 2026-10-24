package com.sumirelabs.kita.previews;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.regex.Pattern;

public final class FxTwitterProvider implements PreviewProvider {
    private static final Pattern STATUS = Pattern.compile("/(?:[^/]+/)?status/(\\d+)");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Override public Preview fetch(SocialLink link) throws Exception {
        var matcher = STATUS.matcher(link.uri().getPath());
        if (!matcher.find()) throw new IllegalArgumentException("Not an X post");
        var request = HttpRequest.newBuilder(URI.create("https://api.fxtwitter.com/status/" + matcher.group(1)))
                .timeout(Duration.ofSeconds(15)).header("User-Agent", "Kita/1.0").GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            if (response.statusCode() != 200) throw new IllegalStateException("X preview unavailable");
            var root = json.readTree(body.readNBytes(1_048_576));
            var tweet = root.path("tweet");
            if (tweet.isMissingNode()) throw new IllegalStateException("X post unavailable");
            var media = new ArrayList<String>();
            for (var item : tweet.path("media").path("all")) {
                if (media.size() == 10) break;
                var url = item.path("url").asText();
                if (url.startsWith("https://")) media.add(url);
            }
            return new Preview("X", tweet.path("author").path("name").asText(), tweet.path("text").asText(),
                    link.uri().toString(), media, tweet.path("community_note").path("text").asText(),
                    tweet.path("possibly_sensitive").asBoolean()).withEngagement(EngagementParser.twitter(tweet));
        }
    }
}
