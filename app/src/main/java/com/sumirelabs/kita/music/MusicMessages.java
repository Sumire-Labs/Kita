package com.sumirelabs.kita.music;

import com.sumirelabs.kita.discord.Ui;
import java.net.URI;
import java.util.List;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public final class MusicMessages {
    private MusicMessages() {}
    public static MessageCreateData added(QueueAddition addition) {
        String title = addition.title().replaceAll("[\\r\\n]+", " ");
        title = MarkdownSanitizer.escape(title.substring(0, Math.min(title.length(), 250)));
        title = title.replace("[", "\\[").replace("]", "\\]");
        if (addition.url() != null) {
            try {
                var url = URI.create(addition.url());
                if (("https".equals(url.getScheme()) || "http".equals(url.getScheme())) && url.getHost() != null
                        && url.getUserInfo() == null && url.toASCIIString().length() <= 2000) {
                    title = "[" + title + "](<" + url.toASCIIString() + ">)";
                }
            } catch (IllegalArgumentException ignored) { /* Keep the title when the source has no usable URL. */ }
        }
        String others = addition.count() > 1 ? " ほか" + (addition.count() - 1) + "曲" : "";
        return Ui.message(List.of(Container.of(TextDisplay.of(title + others + "をキューに追加"),
                TextDisplay.of("-# /player で操作できます。"))));
    }
}
