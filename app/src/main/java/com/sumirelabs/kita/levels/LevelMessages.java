package com.sumirelabs.kita.levels;

import com.sumirelabs.kita.discord.Ui;
import java.util.List;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.Container;
import discord4j.core.object.component.MediaGallery;
import discord4j.core.object.component.MediaGalleryItem;
import discord4j.core.object.component.TextDisplay;


import discord4j.core.spec.MessageCreateSpec;

final class LevelMessages {
    private LevelMessages() {}
    static MessageCreateSpec profile(byte[] png, long owner) {
        var data = Ui.message(List.of(Container.of(MediaGallery.of(MediaGalleryItem.of(discord4j.core.object.component.UnfurledMediaItem.of("attachment://profile.png"))),
                ActionRow.of(Button.secondary("levels:open:" + owner, "🏆 ランキング")))));
        return Ui.file(data, "profile.png", png);
    }
    static MessageCreateSpec leaderboard(List<LevelsRepository.Entry> entries, LevelPeriod period, int page, long owner) {
        var rows = entries.stream().limit(10).map(entry -> "**#" + entry.rank() + "**  <@" + entry.userId() + "> · "
                + (period == LevelPeriod.TOTAL ? "Lv." + LevelCurve.level(entry.xp()) + " · " : "") + entry.xp() + " XP")
                .collect(java.util.stream.Collectors.joining("\n"));
        String base = "levels:board:" + owner + ":";
        var periodMenu = com.sumirelabs.kita.discord.ui.Choices.create("levels:period:" + owner);
        for (var option : LevelPeriod.values()) periodMenu.addOption(option.label(), option.name());
        periodMenu.setDefaultValues(period.name());
        return Ui.message(List.of(Container.of(TextDisplay.of("## ランキング · " + period.label() + "\n"
                        + (rows.isEmpty() ? "まだ記録がありません。" : rows) + "\n-# " + (page + 1) + "ページ · 期間集計は日本時間（月曜／1日開始）"),
                ActionRow.of(periodMenu.build()), ActionRow.of(Button.secondary(base + period + ":" + Math.max(0, page - 1), "◀️")
                        .disabled(page == 0), Button.secondary(base + period + ":" + (page + 1), "▶️")
                        .disabled(entries.size() <= 10 || page >= 10_000)))));
    }
}
