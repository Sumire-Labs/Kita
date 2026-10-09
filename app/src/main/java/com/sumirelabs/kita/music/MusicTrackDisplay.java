package com.sumirelabs.kita.music;

import dev.arbjerg.lavalink.protocol.v4.TrackInfo;
import java.net.URI;
import discord4j.core.object.component.Container;
import discord4j.core.object.component.Section;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.object.component.Thumbnail;
import com.sumirelabs.kita.discord.Ui;

final class MusicTrackDisplay {
    private MusicTrackDisplay() {}

    static String title(String title, String sourceUrl) {
        String text = title.replaceAll("[\\r\\n]+", " ");
        text = Ui.escape(text.substring(0, Math.min(text.length(), 250)));
        String url = httpUrl(sourceUrl);
        return url == null ? text : "[" + text + "](<" + url + ">)";
    }

    static Container heading(String text, String artworkUrl) {
        var content = TextDisplay.of(text);
        String url = httpUrl(artworkUrl);
        return url == null ? Container.of(content) : Container.of(Section.of(Thumbnail.of(discord4j.core.object.component.UnfurledMediaItem.of(url), "曲のサムネイル"), content));
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
