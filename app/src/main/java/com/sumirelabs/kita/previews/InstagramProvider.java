package com.sumirelabs.kita.previews;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public final class InstagramProvider implements PreviewProvider {
    private static final Pattern POST = Pattern.compile("^/(p|reel|tv)/([A-Za-z0-9_-]{1,64})/?$");
    private final PreviewProvider videos;
    public InstagramProvider(PreviewProvider videos) { this.videos = videos; }

    @Override public Preview fetch(SocialLink link) throws Exception {
        var match = POST.matcher(link.uri().getPath());
        if (!match.matches()) throw new IllegalArgumentException("Unsupported Instagram post URL");
        String canonical = "https://www.instagram.com/" + match.group(1) + "/" + match.group(2) + "/";
        try {
            var video = videos.fetch(new SocialLink("instagram", URI.create(canonical)));
            if (!video.media().isEmpty()) return video;
        } catch (Exception ignored) { /* Image posts are not supported by yt-dlp. */ }
        var response = Jsoup.connect(canonical + "embed/captioned/").userAgent("Discordbot/2.0")
                .timeout(15_000).maxBodySize(4_194_304).followRedirects(false).execute();
        if (response.statusCode() != 200) throw new IllegalStateException("Instagram public preview unavailable");
        return parse(response.parse(), canonical);
    }

    static Preview parse(Document page, String canonical) {
        var author = page.selectFirst(".UsernameText, .CaptionUsername");
        var caption = page.selectFirst(".Caption");
        String text = "";
        if (caption != null) {
            caption = caption.clone();
            caption.select(".CaptionUsername, .CaptionComments").remove();
            text = caption.wholeText().strip();
        }
        List<String> media = page.select("img.EmbeddedMediaImage[src], video[src], video source[src]")
                .stream().map(element -> element.absUrl("src")).filter(InstagramProvider::isMediaUrl)
                .distinct().limit(10).toList();
        if (media.isEmpty()) throw new IllegalStateException("Instagram post image unavailable; login or a private post may be required");
        return new Preview("Instagram", author == null ? "" : author.text(), text, canonical, media, "", false)
                .withEngagement(EngagementParser.instagram(page));
    }

    private static boolean isMediaUrl(String url) {
        try {
            var uri = URI.create(url);
            String host = uri.getHost();
            return "https".equals(uri.getScheme()) && uri.getUserInfo() == null && uri.getPort() == -1
                    && host != null && (host.endsWith(".cdninstagram.com") || host.endsWith(".fbcdn.net"));
        } catch (IllegalArgumentException ignored) { return false; }
    }
}
