package com.sumirelabs.kita.music;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;

public final class PublicMusicResolver {
    private final ObjectMapper json = new ObjectMapper();
    public static final int MAX_TRACKS = 250;

    public List<String> resolve(String input) throws Exception {
        if (!input.startsWith("https://")) return List.of();
        var uri = URI.create(input);
        if (uri.getUserInfo() != null || uri.getPort() != -1) throw new IllegalArgumentException("不正な音楽URLです。");
        String host = uri.getHost();
        if (host == null) return List.of();
        if (host.equals("open.spotify.com")) {
            var path = uri.getPath().replaceFirst("^/intl-[^/]+", "").replaceFirst("^/embed", "");
            if (!path.matches("/(track|album|playlist)/[A-Za-z0-9]+")) throw new IllegalArgumentException("公開曲・アルバム・プレイリストのURLを使用してください。");
            var page = Jsoup.connect("https://open.spotify.com/embed" + path).timeout(15_000)
                    .maxBodySize(4_194_304).followRedirects(false).get();
            var script = page.selectFirst("script#__NEXT_DATA__");
            if (script == null) throw new IllegalArgumentException("Spotifyの公開曲情報を取得できません。");
            return spotify(json.readTree(script.data()));
        }
        if (host.equals("music.apple.com")) {
            var page = applePage(uri);
            var script = page.selectFirst("script#serialized-server-data");
            var tracks = new ArrayList<String>();
            String songId = querySong(uri);
            if (script != null) apple(json.readTree(script.data()), songId, tracks);
            if (tracks.isEmpty()) {
                for (var schema : page.select("script[type=application/ld+json]")) appleSchema(json.readTree(schema.data()), tracks);
            }
            if (tracks.isEmpty()) throw new IllegalArgumentException("Apple Musicの公開曲情報を取得できません。");
            return tracks.stream().limit(MAX_TRACKS).toList();
        }
        return List.of();
    }

    public static List<String> spotify(JsonNode root) {
        var entity = root.path("props").path("pageProps").path("state").path("data").path("entity");
        var result = new ArrayList<String>();
        if (entity.path("type").asText().equals("track")) {
            var artists = new ArrayList<String>();
            for (var artist : entity.path("artists")) artists.add(artist.path("name").asText());
            result.add(entity.path("title").asText(entity.path("name").asText()) + " " + String.join(" ", artists));
        } else {
            for (var track : entity.path("trackList")) {
                if (!track.path("title").asText().isBlank()) result.add(track.path("title").asText() + " " + track.path("subtitle").asText());
                if (result.size() == MAX_TRACKS) break;
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Spotifyの曲一覧を取得できません。");
        return List.copyOf(result);
    }

    public static void apple(JsonNode node, String songId, List<String> result) {
        if (result.size() >= MAX_TRACKS) return;
        if (node.isObject() && node.path("contentDescriptor").path("kind").asText().equals("song")
                && !node.path("title").asText().isBlank() && node.has("artistName")) {
            var id = node.path("contentDescriptor").path("identifiers").path("storeAdamID").asText();
            if (songId.isBlank() || id.equals(songId)) result.add(node.path("title").asText() + " " + node.path("artistName").asText());
            return;
        }
        for (var child : node) apple(child, songId, result);
    }

    private void appleSchema(JsonNode node, List<String> result) {
        if (node.path("@type").asText().equals("MusicRecording")) {
            var artists = node.path("byArtist");
            String artist = artists.isArray() ? artists.path(0).path("name").asText() : artists.path("name").asText();
            result.add(node.path("name").asText() + " " + artist);
            return;
        }
        for (var child : node) appleSchema(child, result);
    }

    private static String querySong(URI uri) {
        if (uri.getQuery() != null) for (var field : uri.getQuery().split("&")) if (field.startsWith("i=")) return field.substring(2);
        if (uri.getPath().contains("/song/")) return uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1);
        return "";
    }

    private static org.jsoup.nodes.Document applePage(URI uri) throws Exception {
        for (int redirect = 0; redirect < 4; redirect++) {
            if (!"https".equals(uri.getScheme()) || !"music.apple.com".equals(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1) throw new IllegalArgumentException("不正な転送先です。");
            var response = Jsoup.connect(uri.toString()).timeout(15_000).maxBodySize(8_388_608)
                    .followRedirects(false).execute();
            if (response.statusCode() >= 300 && response.statusCode() < 400 && response.hasHeader("Location")) {
                uri = uri.resolve(response.header("Location"));
            } else return response.parse();
        }
        throw new IllegalArgumentException("転送回数が多すぎます。");
    }
}
