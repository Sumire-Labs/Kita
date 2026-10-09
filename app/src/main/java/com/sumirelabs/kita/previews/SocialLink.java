package com.sumirelabs.kita.previews;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public record SocialLink(String platform, URI uri) {
    private static final Pattern URL = Pattern.compile("https://[^\\s<>|`]+", Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> HOSTS = Map.ofEntries(
            Map.entry("x.com", "x"), Map.entry("twitter.com", "x"),
            Map.entry("reddit.com", "reddit"), Map.entry("old.reddit.com", "reddit"), Map.entry("redd.it", "reddit"),
            Map.entry("tiktok.com", "tiktok"), Map.entry("vm.tiktok.com", "tiktok"), Map.entry("vt.tiktok.com", "tiktok"),
            Map.entry("twitch.tv", "twitch"), Map.entry("clips.twitch.tv", "twitch"),
            Map.entry("instagram.com", "instagram"), Map.entry("youtube.com", "youtube"), Map.entry("youtu.be", "youtube"));

    public static List<SocialLink> find(String content) {
        var links = new ArrayList<SocialLink>();
        var matcher = URL.matcher(content);
        while (matcher.find() && links.size() < 4) {
            try {
                var uri = URI.create(matcher.group().replaceAll("[),.!?]+$", ""));
                if (uri.getHost() == null || uri.getUserInfo() != null || uri.getPort() != -1) continue;
                var host = uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
                var platform = HOSTS.get(host);
                if (platform != null && !uri.getPath().isBlank() && !uri.getPath().equals("/")) {
                    var link = new SocialLink(platform, uri);
                    if (!links.contains(link)) links.add(link);
                }
            } catch (IllegalArgumentException ignored) { /* Ignore malformed links. */ }
        }
        return List.copyOf(links);
    }
}
