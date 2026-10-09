package com.sumirelabs.kita.music;

import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.common.PresetFiles.Preset;
import dev.arbjerg.lavalink.client.player.LavalinkPlayer;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.entities.emoji.Emoji;

public final class PlayerPanel {
    private PlayerPanel() {}
    public static MessageCreateData render(MusicService music, long guildId) {
        var session = music.session(guildId);
        return render(session, music.player(guildId), music.presets());
    }
    static MessageCreateData render(MusicSession session, LavalinkPlayer player, List<Preset> presets) {
        synchronized (session) {
            var track = session.queue.current();
            long position = player == null ? 0 : player.getPosition();
            long length = track == null ? 0 : track.getInfo().getLength();
            int filled = length <= 0 ? 0 : Math.clamp(position * 20 / length, 0, 20);
            var status = track == null ? "待機中" : MusicTrackDisplay.title(track.getInfo().getTitle(), track.getInfo().getUri());
            if (!session.playbackError.isEmpty()) status = "⚠ 再生に失敗しました\n" + status;
            String queue = session.queue.upcoming().stream().limit(5)
                    .map(t -> "• " + MusicTrackDisplay.queueTitle(t.getInfo().getTitle(), t.getInfo().getUri()))
                    .collect(java.util.stream.Collectors.joining("\n"));
            var volume = StringSelectMenu.create("music:volume").setPlaceholder("音量");
            for (int level = 0; level <= 100; level += 5) volume.addOption(level + "%", String.valueOf(level));
            volume.setDefaultValues(String.valueOf(session.volume));
            boolean playing = track != null && player != null && player.getTrack() != null
                    && !player.getPaused() && session.playbackError.isEmpty();
            return Ui.message(List.of(Container.of(MusicTrackDisplay.heading("## Kita Player\n**" + status + "**",
                            track == null ? null : MusicTrackDisplay.artwork(track.getInfo())),
                    TextDisplay.of("▰".repeat(filled) + "▱".repeat(20 - filled) + "\n" + time(position) + " / " + time(length)
                            + " · 音量 " + session.volume + "% · ループ: " + loopName(session.queue.loop()) + "\n"
                            + PresetSelector.details(presets, session.preset) + "\n"
                            + (session.playbackError.isEmpty() ? "" : session.playbackError + "\n")
                            + "### キュー（" + session.queue.upcoming().size() + "曲）\n" + (queue.isBlank() ? "空です" : queue)),
                    ActionRow.of(Button.secondary("music:back", Emoji.fromUnicode("⏮️")),
                            Button.primary("music:pause", Emoji.fromUnicode(playing ? "⏸️" : "▶️")),
                            Button.secondary("music:skip", Emoji.fromUnicode("⏭️"))),
                    ActionRow.of(loopButton("loop-track", "🔂", session.queue.loop() == PlaybackQueue.Loop.TRACK),
                            loopButton("loop-queue", "🔁", session.queue.loop() == PlaybackQueue.Loop.QUEUE)),
                    ActionRow.of(PresetSelector.menu(presets, session.preset, session.presetPage)), ActionRow.of(volume.build()))));
        }
    }

    public static String time(long millis) { return "%d:%02d".formatted(millis / 60_000, millis / 1000 % 60); }
    private static Button loopButton(String action, String emoji, boolean selected) {
        return Button.of(selected ? ButtonStyle.PRIMARY : ButtonStyle.SECONDARY, "music:" + action, Emoji.fromUnicode(emoji));
    }
    private static String loopName(PlaybackQueue.Loop loop) {
        return switch (loop) { case OFF -> "オフ"; case TRACK -> "トラック"; case QUEUE -> "キュー"; };
    }
}
