package com.sumirelabs.kita.levels;

import com.sumirelabs.kita.discord.Ui;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

final class LevelMessages {
    private LevelMessages() {}
    static MessageCreateData profile(byte[] png, long owner) {
        var data = Ui.message(List.of(Container.of(MediaGallery.of(MediaGalleryItem.fromUrl("attachment://profile.png")),
                ActionRow.of(Button.secondary("levels:open:" + owner, "🏆 ランキング")))));
        return MessageCreateBuilder.from(data).addFiles(FileUpload.fromData(png, "profile.png")).build();
    }
    static MessageCreateData leaderboard(List<LevelsRepository.Entry> entries, LevelPeriod period, int page, long owner) {
        var rows = entries.stream().limit(10).map(entry -> "**#" + entry.rank() + "**  <@" + entry.userId() + "> · "
                + (period == LevelPeriod.TOTAL ? "Lv." + LevelCurve.level(entry.xp()) + " · " : "") + entry.xp() + " XP")
                .collect(java.util.stream.Collectors.joining("\n"));
        String base = "levels:board:" + owner + ":";
        var periodMenu = net.dv8tion.jda.api.components.selections.StringSelectMenu.create("levels:period:" + owner);
        for (var option : LevelPeriod.values()) periodMenu.addOption(option.label(), option.name());
        periodMenu.setDefaultValues(period.name());
        return Ui.message(List.of(Container.of(TextDisplay.of("## ランキング · " + period.label() + "\n"
                        + (rows.isEmpty() ? "まだ記録がありません。" : rows) + "\n-# " + (page + 1) + "ページ · 期間集計は日本時間（月曜／1日開始）"),
                ActionRow.of(periodMenu.build()), ActionRow.of(Button.secondary(base + period + ":" + Math.max(0, page - 1), "◀️")
                        .withDisabled(page == 0), Button.secondary(base + period + ":" + (page + 1), "▶️")
                        .withDisabled(entries.size() <= 10 || page >= 10_000)))));
    }
}
