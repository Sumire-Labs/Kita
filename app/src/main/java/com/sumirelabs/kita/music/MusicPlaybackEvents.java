package com.sumirelabs.kita.music;

import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.event.EmittedEvent;
import dev.arbjerg.lavalink.client.event.TrackEndEvent;
import dev.arbjerg.lavalink.client.event.TrackExceptionEvent;
import dev.arbjerg.lavalink.client.event.TrackStuckEvent;
import dev.arbjerg.lavalink.client.player.Track;
import java.util.Map;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

final class MusicPlaybackEvents implements AutoCloseable {
    static final String FAILURE = "音声を取得できませんでした。再生ボタンで再試行するか、スキップしてください。";
    @FunctionalInterface interface Starter { Mono<Void> start(long guild, Track track, MusicSession session); }
    private final Disposable subscription;

    MusicPlaybackEvents(LavalinkClient client, Map<Long, MusicSession> sessions, Starter starter) {
        subscription = client.on(EmittedEvent.class).filter(event -> event instanceof TrackEndEvent
                || event instanceof TrackExceptionEvent || event instanceof TrackStuckEvent)
                .flatMap(event -> {
                    var session = sessions.get(event.getGuildId());
                    if (session == null) return Mono.empty();
                    return session.operations.submit(() -> handle(event, session, starter))
                            .onErrorResume(error -> {
                                LoggerFactory.getLogger(getClass()).warn("Playback event failed in guild {}: {}",
                                        event.getGuildId(), error.getClass().getSimpleName());
                                return Mono.empty();
                            });
                }, 128).subscribe();
    }
    private Mono<Void> handle(EmittedEvent event, MusicSession session, Starter starter) {
        switch (event) {
            case TrackEndEvent end -> {
                if (session.crossfadeActive) return Mono.empty();
                boolean advance;
                synchronized (session) {
                    if (!matches(session, end.getTrack())) return Mono.empty();
                    if (end.getEndReason().name().equals("LOAD_FAILED")) failed(session, end.getTrack());
                    advance = end.getEndReason().name().equals("FINISHED") && session.playbackError.isEmpty();
                }
                if (!advance) return Mono.empty();
                var snapshot = session.queue.snapshot();
                Track next;
                synchronized (session) { next = session.queue.next(true); }
                return starter.start(event.getGuildId(), next, session)
                        .doOnError(error -> { synchronized (session) { session.queue.restore(snapshot); } });
            }
            case TrackExceptionEvent failure -> failed(session, failure.getTrack());
            case TrackStuckEvent stuck -> failed(session, stuck.getTrack());
            default -> { }
        }
        return Mono.empty();
    }
    static void ended(MusicSession session, Track track, String reason, Runnable advance) {
        boolean next;
        synchronized (session) {
            if (!matches(session, track)) return;
            if (reason.equals("LOAD_FAILED")) failed(session, track);
            next = reason.equals("FINISHED") && session.playbackError.isEmpty();
        }
        if (next) advance.run();
    }
    static void failed(MusicSession session, Track track) {
        synchronized (session) { if (matches(session, track)) session.playbackError = FAILURE; }
    }
    private static boolean matches(MusicSession session, Track track) {
        return session.queue.current() != null && session.playbackId.equals(track.getUserData().path("kitaPlaybackId").asText());
    }
    @Override public void close() { subscription.dispose(); }
}
