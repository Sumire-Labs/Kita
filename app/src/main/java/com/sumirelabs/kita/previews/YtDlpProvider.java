package com.sumirelabs.kita.previews;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class YtDlpProvider implements PreviewProvider {
    private final String executable;
    private final ObjectMapper json = new ObjectMapper();
    public YtDlpProvider(String executable) { this.executable = executable; }

    @Override public Preview fetch(SocialLink link) throws Exception {
        var output = Files.createTempFile("kita-preview-", ".json");
        var errors = Files.createTempFile("kita-preview-", ".err");
        Process process = null;
        try {
            process = new ProcessBuilder(executable, "--dump-single-json", "--skip-download", "--no-playlist",
                    "--no-warnings", "--socket-timeout", "10", "--retries", "1", "--ignore-config",
                    "--", link.uri().toString()).redirectOutput(output.toFile())
                    .redirectError(errors.toFile()).start();
            if (!process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Preview timed out");
            }
            if (process.exitValue() != 0 || Files.size(output) > 8_388_608) {
                String detail;
                try (var input = Files.newInputStream(errors)) {
                    detail = new String(input.readNBytes(1500), java.nio.charset.StandardCharsets.UTF_8)
                            .replaceAll("https?://\\S+", "[URL]").replaceAll("[\\r\\n]+", " ").strip();
                }
                throw new IllegalStateException("yt-dlp exited " + process.exitValue() + ": " + detail);
            }
            var data = json.readTree(output.toFile());
            return parse(data, link);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(output);
            Files.deleteIfExists(errors);
        }
    }

    static Preview parse(JsonNode data, SocialLink link) {
        var media = new ArrayList<String>();
        boolean playable = collect(data, media);
        for (var entry : data.path("entries")) if (media.size() < 10) playable |= collect(entry, media);
        if (media.isEmpty()) throw new IllegalStateException("yt-dlp returned no displayable media");
        return new Preview(link.platform(), data.path("uploader").asText(data.path("channel").asText()),
                data.path("title").asText(data.path("description").asText()), link.uri().toString(),
                media.stream().distinct().limit(10).toList(), playable ? "" : "プレビュー画像です。動画は投稿元で再生できます。",
                data.path("age_limit").asInt() >= 18, playable);
    }

    private static boolean collect(JsonNode data, List<String> media) {
        String video = "";
        for (var format : data.path("formats")) {
            if (format.path("ext").asText().equals("mp4") && !format.path("vcodec").asText("").equals("none")
                    && !format.path("acodec").asText("").equals("none")
                    && format.path("protocol").asText().equals("https") && format.path("height").asInt() <= 720) {
                video = format.path("url").asText();
            }
        }
        String url = video.isBlank() ? data.path("thumbnail").asText() : video;
        if (url.startsWith("https://")) media.add(url);
        return !video.isBlank();
    }
}
