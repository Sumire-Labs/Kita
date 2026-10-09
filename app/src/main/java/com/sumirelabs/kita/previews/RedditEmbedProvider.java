package com.sumirelabs.kita.previews;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public final class RedditEmbedProvider implements PreviewProvider {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Override public Preview fetch(SocialLink link) throws Exception {
        var path = link.uri().getPath();
        if (link.uri().getHost().equals("redd.it")) path = "/comments/" + path.substring(1);
        var match = java.util.regex.Pattern.compile("/comments/([A-Za-z0-9]+)(?:/|$)").matcher(path);
        if (!match.find()) throw new IllegalArgumentException("Not a Reddit post URL");
        var canonical = "https://www.reddit.com" + path;
        var request = HttpRequest.newBuilder(URI.create("https://www.reddit.com/oembed?url="
                        + URLEncoder.encode(canonical, StandardCharsets.UTF_8))).timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Discordbot/2.0").GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        JsonNode metadata;
        try (var body = response.body()) {
            if (response.statusCode() != 200) throw new IllegalStateException("Reddit public preview HTTP " + response.statusCode());
            metadata = json.readTree(body.readNBytes(1_048_576));
        }
        var page = publicPage(URI.create("https://www.redditmedia.com/mediaembed/" + match.group(1) + "?embed=true"));
        return parse(metadata, page, canonical);
    }

    private static Document publicPage(URI uri) throws Exception {
        for (int attempt = 0; attempt < 4; attempt++) {
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || uri.getPort() != -1
                    || !java.util.Set.of("www.redditmedia.com", "embed.reddit.com", "www.reddit.com").contains(uri.getHost())) {
                throw new IllegalStateException("Unexpected Reddit embed redirect");
            }
            var response = Jsoup.connect(uri.toString()).userAgent("Discordbot/2.0").timeout(15_000)
                    .maxBodySize(4_194_304).followRedirects(false).execute();
            if (response.statusCode() >= 300 && response.statusCode() < 400 && response.hasHeader("Location")) {
                uri = uri.resolve(response.header("Location"));
            } else if (response.statusCode() == 200) return response.parse();
            else throw new IllegalStateException("Reddit embed HTTP " + response.statusCode());
        }
        throw new IllegalStateException("Reddit embed redirect limit exceeded");
    }

    static Preview parse(JsonNode metadata, Document page, String url) {
        List<String> media = page.select("img.preview-image[src], [poster]").stream()
                .map(element -> element.absUrl(element.hasAttr("poster") ? "poster" : "src"))
                .filter(RedditEmbedProvider::isMediaUrl).distinct().limit(10).toList();
        String title = metadata.path("title").asText();
        if (title.isBlank()) throw new IllegalStateException("Reddit public preview contains no title");
        boolean sensitive = !page.select("[nsfw=true], [data-nsfw=true], [spoiler=true], [data-spoiler=true]").isEmpty();
        return new Preview("Reddit", "u/" + metadata.path("author_name").asText(), title, url, media, "", sensitive, false)
                .withEngagement(EngagementParser.redditEmbed(metadata, page));
    }

    private static boolean isMediaUrl(String url) {
        try {
            var uri = URI.create(url);
            var host = uri.getHost();
            return "https".equals(uri.getScheme()) && host != null && uri.getUserInfo() == null
                    && (host.equals("redd.it") || host.endsWith(".redd.it") || host.endsWith(".redditmedia.com"));
        } catch (IllegalArgumentException ignored) { return false; }
    }
}
