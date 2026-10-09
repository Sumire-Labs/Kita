package com.sumirelabs.kita.music;

import com.sumirelabs.kita.discord.Ui;
import java.util.List;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public final class MusicMessages {
    private MusicMessages() {}
    public static MessageCreateData added(QueueAddition addition) {
        String title = MusicTrackDisplay.title(addition.title(), addition.url());
        String others = addition.count() > 1 ? " ほか" + (addition.count() - 1) + "曲" : "";
        return Ui.message(List.of(Container.of(MusicTrackDisplay.heading(title + others + "をキューに追加", addition.artworkUrl()),
                TextDisplay.of("-# /player で操作できます。"))));
    }
}
