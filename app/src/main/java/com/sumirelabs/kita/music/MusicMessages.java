package com.sumirelabs.kita.music;

import com.sumirelabs.kita.discord.Ui;
import java.util.List;
import discord4j.core.object.component.Container;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.spec.MessageCreateSpec;

public final class MusicMessages {
    private MusicMessages() {}
    public static MessageCreateSpec added(QueueAddition addition) {
        String title = MusicTrackDisplay.title(addition.title(), addition.url());
        String others = addition.count() > 1 ? " ほか" + (addition.count() - 1) + "曲" : "";
        return Ui.message(List.of(Ui.append(MusicTrackDisplay.heading(title + others + "をキューに追加", addition.artworkUrl()),
                TextDisplay.of("-# /player で操作できます。"))));
    }
}
