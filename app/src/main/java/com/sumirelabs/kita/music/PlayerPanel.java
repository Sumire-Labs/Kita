package com.sumirelabs.kita.music;

import com.sumirelabs.kita.discord.Ui;
import com.sumirelabs.kita.common.PresetFiles.Preset;
import dev.arbjerg.lavalink.client.player.LavalinkPlayer;
import java.util.List;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;

import discord4j.core.object.component.Container;
import com.sumirelabs.kita.discord.ui.Choices;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.object.emoji.Emoji;

public final class PlayerPanel {
    private PlayerPanel() {}
    public static MessageCreateSpec render(MusicService music, long guildId) {
        var session = music.session(guildId);
        return render(session, music.player(guildId), music.presets());
    }
    static MessageCreateSpec render(MusicSession session, LavalinkPlayer player, List<Preset> presets) {
        synchronized (session) {
            var track = session.queue.current();
            long position = session.crossfadeActive && session.crossfadeState != null ? session.crossfadeState.position() : player == null ? 0 : player.getPosition();
            long length = track == null ? 0 : track.getInfo().getLength();
            int filled = length <= 0 ? 0 : Math.clamp(position * 20 / length, 0, 20);
            var status = track == null ? "待機中" : MusicTrackDisplay.title(track.getInfo().getTitle(), track.getInfo().getUri());
            if (!session.playbackError.isEmpty()) status = "⚠ 再生に失敗しました\n" + status;
            if (session.crossfadeActive && session.crossfadeState != null && session.crossfadeState.fading() && session.crossfadeNext != null) {
                var next = session.crossfadeNext.getInfo();
                status += " → " + MusicTrackDisplay.title(next.getTitle(), next.getUri());
            }
            String queue = session.queue.upcoming().stream().limit(5)
                    .map(t -> "• " + MusicTrackDisplay.queueTitle(t.getInfo().getTitle(), t.getInfo().getUri()))
                    .collect(java.util.stream.Collectors.joining("\n"));
            var volume = Choices.create("music:volume").setPlaceholder("音量");
            for (int level = 0; level <= 100; level += 5) volume.addOption(level + "%", String.valueOf(level));
            volume.setDefaultValues(String.valueOf(session.volume));
            boolean playing = track != null && player != null && player.getTrack() != null
                    && !player.getPaused() && session.playbackError.isEmpty();
            return Ui.message(List.of(Ui.append(MusicTrackDisplay.heading("## Kita Player\n**" + status + "**",
                            track == null ? null : MusicTrackDisplay.artwork(track.getInfo())),
                    TextDisplay.of("▰".repeat(filled) + "▱".repeat(20 - filled) + "\n" + time(position) + " / " + time(length)
                            + " · 音量 " + session.volume + "% · ループ: " + loopName(session.queue.loop()) + "\n"
                            + PresetSelector.details(presets, session.preset) + "\n"
                            + (session.playbackError.isEmpty() ? "" : session.playbackError + "\n")
                            + "### キュー（" + session.queue.upcoming().size() + "曲）\n" + (queue.isBlank() ? "空です" : queue)),
                    ActionRow.of(Button.secondary("music:back", Emoji.unicode("⏮️")),
                            Button.primary("music:pause", Emoji.unicode(playing ? "⏸️" : "▶️")),
                            Button.secondary("music:skip", Emoji.unicode("⏭️"))),
                    ActionRow.of(loopButton("loop-track", "🔂", session.queue.loop() == PlaybackQueue.Loop.TRACK),
                            loopButton("loop-queue", "🔁", session.queue.loop() == PlaybackQueue.Loop.QUEUE)),
                    ActionRow.of(PresetSelector.menu(presets, session.preset, session.presetPage)), ActionRow.of(volume.build()))));
        }
    }

    public static String time(long millis) { return "%d:%02d".formatted(millis / 60_000, millis / 1000 % 60); }
    private static Button loopButton(String action, String emoji, boolean selected) {
        return selected ? Button.primary("music:" + action, Emoji.unicode(emoji)) : Button.secondary("music:" + action, Emoji.unicode(emoji));
    }
    private static String loopName(PlaybackQueue.Loop loop) {
        return switch (loop) { case OFF -> "オフ"; case TRACK -> "トラック"; case QUEUE -> "キュー"; };
    }
}
