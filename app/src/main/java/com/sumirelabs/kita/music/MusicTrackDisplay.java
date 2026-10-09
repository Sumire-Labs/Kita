package com.sumirelabs.kita.music;

import dev.arbjerg.lavalink.protocol.v4.TrackInfo;
import java.net.URI;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;

final class MusicTrackDisplay {
    private MusicTrackDisplay() {}

    static String title(String title, String sourceUrl) {
        String text = title.replaceAll("[\\r\\n]+", " ");
        text = MarkdownSanitizer.escape(text.substring(0, Math.min(text.length(), 250)));
        text = text.replace("[", "\\[").replace("]", "\\]");
        String url = httpUrl(sourceUrl);
        return url == null ? text : "[" + text + "](<" + url + ">)";
    }

    static ContainerChildComponent heading(String text, String artworkUrl) {
        var content = TextDisplay.of(text);
        String url = httpUrl(artworkUrl);
        return url == null ? content : Section.of(Thumbnail.fromUrl(url).withDescription("曲のサムネイル"), content);
    }

    static String artwork(TrackInfo track) {
        String supplied = httpUrl(track.getArtworkUrl());
        if (supplied != null) return supplied;
        // Flat YouTube search results may omit artwork even though the video has a thumbnail.
        return "youtube".equals(track.getSourceName()) && track.getIdentifier().matches("[A-Za-z0-9_-]{11}")
                ? "https://i.ytimg.com/vi/" + track.getIdentifier() + "/hqdefault.jpg" : null;
    }

    static String queueTitle(String title, String url) {
        String linked = title(title, url);
        return linked.length() <= 400 ? linked : title(title, null);
    }

    private static String httpUrl(String value) {
        if (value == null) return null;
        try {
            var url = URI.create(value);
            if (("https".equals(url.getScheme()) || "http".equals(url.getScheme())) && url.getHost() != null
                    && url.getUserInfo() == null && url.toASCIIString().length() <= 2000) return url.toASCIIString();
        } catch (IllegalArgumentException ignored) { /* Render text alone for unusable source metadata. */ }
        return null;
    }
}
