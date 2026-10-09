package com.sumirelabs.kita.music;

import com.sumirelabs.kita.discord.Ui;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public final class PlayerPanel {
    private PlayerPanel() {}
    public static MessageCreateData render(MusicService music, long guildId) {
        var session = music.session(guildId);
        synchronized (session) {
            var player = music.player(guildId);
            var track = session.queue.current();
            long position = player == null ? 0 : player.getPosition();
            long length = track == null ? 0 : track.getInfo().getLength();
            int filled = length <= 0 ? 0 : Math.clamp(position * 20 / length, 0, 20);
            var status = track == null ? "待機中" : shortTitle(track.getInfo().getTitle());
            if (!session.playbackError.isEmpty()) status = "⚠ 再生に失敗しました\n" + status;
            String queue = session.queue.upcoming().stream().limit(5)
                    .map(t -> "• " + shortTitle(t.getInfo().getTitle())).collect(java.util.stream.Collectors.joining("\n"));
            var volume = StringSelectMenu.create("music:volume").setPlaceholder("音量");
            for (int level = 0; level <= 100; level += 5) volume.addOption(level + "%", String.valueOf(level));
            volume.setDefaultValues(String.valueOf(session.volume));
            var preset = StringSelectMenu.create("music:preset").setPlaceholder("HRIRプリセット").addOption("無効", "off");
            int start = session.presetPage * 24;
            for (var entry : music.presets().stream().skip(start).limit(24).toList()) {
                preset.addOptions(PresetSelector.option(entry));
            }
            if (session.preset.equals("off") || music.presets().stream().skip(start).limit(24).anyMatch(p -> p.id().equals(session.preset))) {
                preset.setDefaultValues(session.preset);
            }
            return Ui.message(List.of(Container.of(TextDisplay.of("## Kita Player\n**" + status + "**\n"
                            + "▰".repeat(filled) + "▱".repeat(20 - filled) + "\n" + time(position) + " / " + time(length)
                            + " · 音量 " + session.volume + "% · Loop " + session.queue.loop() + "\n"
                            + PresetSelector.details(music.presets(), session.preset) + "\n"
                            + (session.playbackError.isEmpty() ? "" : session.playbackError + "\n")
                            + "### キュー（" + session.queue.upcoming().size() + "曲）\n" + (queue.isBlank() ? "空です" : queue)),
                    ActionRow.of(Button.secondary("music:back", "戻る"), Button.primary("music:pause", "再生 / 一時停止"),
                            Button.danger("music:stop", "停止"), Button.primary("music:skip", "スキップ"), Button.secondary("music:loop", "ループ")),
                    ActionRow.of(Button.secondary("music:rewind", "−15秒"), Button.secondary("music:seek", "時刻指定"),
                            Button.secondary("music:forward", "+15秒"), Button.secondary("music:refresh", "更新")),
                    ActionRow.of(preset.build()), ActionRow.of(volume.build()),
                    ActionRow.of(Button.secondary("music:presets-prev", "前のプリセット一覧").withDisabled(session.presetPage == 0),
                            Button.secondary("music:presets-next", "次のプリセット一覧")
                                    .withDisabled(start + 24 >= music.presets().size())))));
        }
    }

    public static String time(long millis) { return "%d:%02d".formatted(millis / 60_000, millis / 1000 % 60); }
    private static String shortTitle(String text) { return Ui.safe(text.substring(0, Math.min(200, text.length()))); }
}
