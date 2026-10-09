package com.sumirelabs.kita.music;

import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.player.Track;
import java.time.Duration;
import java.util.Map;
import reactor.core.publisher.Mono;

// Callers serialize mutations with MusicSession.operate; the state monitor never spans I/O.
final class MusicUpdates {
    private final LavalinkClient client;
    MusicUpdates(LavalinkClient client) { this.client = client; }

    void start(long guildId, Track track, MusicSession session) {
        var update = client.getOrCreateLink(guildId).createOrUpdatePlayer();
        synchronized (session) {
            update.setVolume(session.volume).setPaused(false).setFilters(MusicPresets.filters(session.preset));
        }
        var playbackId = java.util.UUID.randomUUID().toString();
        if (track == null) update.stopTrack();
        else {
            var playing = track.makeClone();
            playing.setUserData(Map.of("kitaPlaybackId", playbackId));
            update.setTrack(playing);
        }
        commit(session, update, () -> { session.playbackId = playbackId; session.playbackError = ""; });
    }

    void action(long guildId, MusicSession session, String action, long value) {
        var link = client.getOrCreateLink(guildId);
        if (action.equals("volume")) {
            if (value < 0 || value > 100) throw new IllegalArgumentException("音量は0〜100です。");
            commit(session, link.createOrUpdatePlayer().setVolume((int) value), () -> session.volume = (int) value);
            return;
        }
        boolean retry;
        synchronized (session) { retry = !session.playbackError.isEmpty() && session.queue.current() != null; }
        if (action.equals("pause") && retry) { start(guildId, session.queue.current(), session); return; }
        var player = link.getCachedPlayer();
        if (player == null || player.getTrack() == null) throw new IllegalArgumentException("再生中の曲はありません。");
        if (action.equals("pause")) commit(session, player.setPaused(!player.getPaused()), () -> {});
        else {
            if (!player.getTrack().getInfo().isSeekable()) throw new IllegalArgumentException("この曲はシークできません。");
            commit(session, player.setPosition(Math.clamp(value, 0, player.getTrack().getInfo().getLength())), () -> {});
        }
    }

    void preset(long guildId, MusicSession session, String id) {
        commit(session, client.getOrCreateLink(guildId).createOrUpdatePlayer().setFilters(MusicPresets.filters(id)),
                () -> session.preset = id);
    }

    static void commit(MusicSession session, Mono<?> request, Runnable accepted) {
        request.block(Duration.ofSeconds(20));
        synchronized (session) { accepted.run(); }
    }
}
