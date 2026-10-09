package com.sumirelabs.kita.previews;

import com.sumirelabs.kita.discord.Ui;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public final class PreviewRenderer {
    private PreviewRenderer() {}
    public static MessageCreateData render(Preview preview, boolean nsfwChannel) {
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
                    .map(url -> MediaGalleryItem.fromUrl(url).withSpoiler(hidden)).toList()));
        }
        if (!preview.context().isBlank()) {
            var note = Ui.safe(preview.context());
            container = Ui.append(container, TextDisplay.of("**補足**\n" + note.substring(0, Math.min(note.length(), 700))));
        }
        container = Ui.append(container, ActionRow.of(Button.link(preview.url(), "投稿を開く")));
        return Ui.message(List.of(container));
    }
}
