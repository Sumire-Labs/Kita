package com.sumirelabs.kita.music;

import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.discord.ui.Choices;
import com.sumirelabs.kita.settings.GuildSettings;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Container;
import discord4j.core.object.component.TextDisplay;

public final class MusicSettings {
    private MusicSettings() {}
    public static int seconds(GuildSettings settings) {
        try { int value = Integer.parseInt(settings.value("music.crossfade", "0")); return value >= 0 && value <= 12 ? value : 0; }
        catch (NumberFormatException ignored) { return 0; }
    }
    public static Container append(Container base, GuildSettings settings, String prefix) {
        var choices = Choices.create(prefix + "music.crossfade").setPlaceholder("クロスフェード時間").addOption("OFF", "0");
        for (int seconds = 1; seconds <= 12; seconds++) choices.addOption(seconds + "秒" + (seconds == 5 ? "（おすすめ）" : ""), String.valueOf(seconds));
        return Ui.append(base, TextDisplay.of("### Music\n曲の終わりと次の曲の冒頭を重ねて再生します。おすすめは5秒です。\n"
                        + "変更は次に開始する曲から適用します。ライブ・トラックループは対象外です。\n"
                        + "準備が間に合わない場合や音声ノードの上限到達時は、通常の曲切り替えに戻します。"),
                ActionRow.of(choices.setDefaultValues(String.valueOf(seconds(settings))).build()));
    }
}
