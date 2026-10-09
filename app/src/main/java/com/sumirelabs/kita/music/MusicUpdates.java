package com.sumirelabs.kita.music;

import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.player.Track;
import java.time.Duration;
import java.util.Map;
import reactor.core.publisher.Mono;

final class MusicUpdates {
    private final LavalinkClient client;
    MusicUpdates(LavalinkClient client) { this.client = client; }

    Mono<Void> start(long guildId, Track track, MusicSession session) {
        var update = client.getOrCreateLink(guildId).createOrUpdatePlayer();
        synchronized (session) { update.setVolume(session.volume).setPaused(false).setFilters(MusicPresets.filters(session.preset)); }
        var playbackId = java.util.UUID.randomUUID().toString();
        if (track == null) update.stopTrack();
        else {
            var playing = track.makeClone();
            playing.setUserData(Map.of("kitaPlaybackId", playbackId));
            update.setTrack(playing);
        }
        return commit(session, update, () -> { session.playbackId = playbackId; session.playbackError = ""; });
    }

    Mono<Void> action(long guildId, MusicSession session, String action, long value) {
        var link = client.getOrCreateLink(guildId);
        if (action.equals("volume")) {
            if (value < 0 || value > 100) return Mono.error(new IllegalArgumentException("音量は0〜100です。"));
            return commit(session, link.createOrUpdatePlayer().setVolume((int) value), () -> session.volume = (int) value);
        }
        synchronized (session) {
            if (action.equals("pause") && !session.playbackError.isEmpty() && session.queue.current() != null) {
                return start(guildId, session.queue.current(), session);
            }
        }
        var player = link.getCachedPlayer();
        if (player == null || player.getTrack() == null) return Mono.error(new IllegalArgumentException("再生中の曲はありません。"));
        if (action.equals("pause")) return commit(session, player.setPaused(!player.getPaused()), () -> {});
        if (!player.getTrack().getInfo().isSeekable()) return Mono.error(new IllegalArgumentException("この曲はシークできません。"));
        return commit(session, player.setPosition(Math.clamp(value, 0, player.getTrack().getInfo().getLength())), () -> {});
    }

    Mono<Void> preset(long guildId, MusicSession session, String id) {
        return commit(session, client.getOrCreateLink(guildId).createOrUpdatePlayer().setFilters(MusicPresets.filters(id)), () -> session.preset = id);
    }

    static Mono<Void> commit(MusicSession session, Mono<?> request, Runnable accepted) {
        return request.timeout(Duration.ofSeconds(20)).then(Mono.fromRunnable(() -> { synchronized (session) { accepted.run(); } }));
    }
}
