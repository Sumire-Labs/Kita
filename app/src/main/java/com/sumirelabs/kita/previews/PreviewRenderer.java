package com.sumirelabs.kita.previews;

import com.sumirelabs.kita.discord.Ui;
import java.util.List;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.Container;
import discord4j.core.object.component.MediaGallery;
import discord4j.core.object.component.MediaGalleryItem;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.spec.MessageCreateSpec;

public final class PreviewRenderer {
    private PreviewRenderer() {}
    public static MessageCreateSpec render(Preview preview, boolean nsfwChannel) {
        var author = Ui.safe(preview.author());
        if (author.length() > 100) author = author.substring(0, 100);
        var container = Container.of(TextDisplay.of("### " + Ui.safe(preview.platform()) + (author.isBlank() ? "" : " · " + author)));
        boolean hidden = preview.sensitive() && !nsfwChannel;
        var text = Ui.safe(preview.text());
        if (text.length() > 2500) text = text.substring(0, 2500) + "…";
        if (hidden) text = "||" + text.replace("||", "") + "||";
        if (!text.isBlank()) container = Ui.append(container, TextDisplay.of(text));
        if (!preview.media().isEmpty()) {
            container = Ui.append(container, MediaGallery.of(preview.media().stream()
                    .map(url -> MediaGalleryItem.of(discord4j.core.object.component.UnfurledMediaItem.of(url), hidden)).toList()));
        }
        if (!preview.context().isBlank()) {
            var note = Ui.safe(preview.context());
            container = Ui.append(container, TextDisplay.of("**補足**\n" + note.substring(0, Math.min(note.length(), 700))));
        }
        var engagement = EngagementRenderer.render(preview);
        if (!engagement.isBlank()) container = Ui.append(container, TextDisplay.of(engagement));
        container = Ui.append(container, ActionRow.of(Button.link(preview.url(), "投稿を開く")));
        return Ui.message(List.of(container));
    }
}
