package com.sumirelabs.kita.music;

import com.sumirelabs.kita.discord.WorkExecutor;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.event.TrackEndEvent;
import dev.arbjerg.lavalink.client.event.TrackExceptionEvent;
import dev.arbjerg.lavalink.client.event.TrackStuckEvent;
import dev.arbjerg.lavalink.client.player.Track;
import java.util.Map;
import org.slf4j.LoggerFactory;

final class MusicPlaybackEvents {
    static final String FAILURE = "音声を取得できませんでした。再生ボタンで再試行するか、スキップしてください。";
    @FunctionalInterface interface Starter { void start(long guildId, Track track, MusicSession session); }

    MusicPlaybackEvents(LavalinkClient client, Map<Long, MusicSession> sessions, WorkExecutor worker, Starter starter) {
        client.on(TrackEndEvent.class).subscribe(event -> worker.submit(() -> {
            var session = sessions.get(event.getGuildId());
            if (session == null) return;
            synchronized (session) {
                ended(session, event.getTrack(), event.getEndReason().name(), () -> {
                    var snapshot = session.queue.snapshot();
                    try { starter.start(event.getGuildId(), session.queue.next(true), session); }
                    catch (RuntimeException error) { session.queue.restore(snapshot); throw error; }
                });
            }
        }));
        client.on(TrackExceptionEvent.class).subscribe(event -> worker.submit(() -> {
            var session = sessions.get(event.getGuildId());
            if (session == null) return;
            synchronized (session) {
                failed(session, event.getTrack());
                LoggerFactory.getLogger(MusicPlaybackEvents.class).warn("Playback failed in guild {} for source {}",
                        event.getGuildId(), event.getTrack().getInfo().getSourceName());
            }
        }));
        client.on(TrackStuckEvent.class).subscribe(event -> worker.submit(() -> {
            var session = sessions.get(event.getGuildId());
            if (session == null) return;
            synchronized (session) { failed(session, event.getTrack()); }
        }));
    }

    static void ended(MusicSession session, Track track, String reason, Runnable advance) {
        if (!matches(session, track)) return;
        if (reason.equals("LOAD_FAILED")) failed(session, track);
        else if (reason.equals("FINISHED") && session.playbackError.isEmpty()) advance.run();
    }

    static void failed(MusicSession session, Track track) {
        if (matches(session, track)) session.playbackError = FAILURE;
    }

    private static boolean matches(MusicSession session, Track track) {
        return session.queue.current() != null
                && session.playbackId.equals(track.getUserData().path("kitaPlaybackId").asText());
    }
}
