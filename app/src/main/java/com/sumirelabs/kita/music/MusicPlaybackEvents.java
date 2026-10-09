package com.sumirelabs.kita.music;

import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.event.EmittedEvent;
import dev.arbjerg.lavalink.client.event.TrackEndEvent;
import dev.arbjerg.lavalink.client.event.TrackExceptionEvent;
import dev.arbjerg.lavalink.client.event.TrackStuckEvent;
import dev.arbjerg.lavalink.client.player.Track;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

final class MusicPlaybackEvents implements AutoCloseable {
    static final String FAILURE = "音声を取得できませんでした。再生ボタンで再試行するか、スキップしてください。";
    @FunctionalInterface interface Starter { void start(long guildId, Track track, MusicSession session); }
    private final Disposable subscription;

    MusicPlaybackEvents(LavalinkClient client, Map<Long, MusicSession> sessions, Starter starter) {
        var events = client.on(EmittedEvent.class).filter(event -> event instanceof TrackEndEvent
                || event instanceof TrackExceptionEvent || event instanceof TrackStuckEvent);
        subscription = ordered(events, EmittedEvent::getGuildId, event -> {
            var session = sessions.get(event.getGuildId());
            if (session == null) return;
            synchronized (session) {
                switch (event) {
                    case TrackEndEvent end -> ended(session, end.getTrack(), end.getEndReason().name(), () -> {
                        var snapshot = session.queue.snapshot();
                        try { starter.start(event.getGuildId(), session.queue.next(true), session); }
                        catch (RuntimeException error) { session.queue.restore(snapshot); throw error; }
                    });
                    case TrackExceptionEvent failure -> {
                        failed(session, failure.getTrack());
                        LoggerFactory.getLogger(MusicPlaybackEvents.class).warn("Playback failed in guild {} for source {}",
                                event.getGuildId(), failure.getTrack().getInfo().getSourceName());
                    }
                    case TrackStuckEvent stuck -> failed(session, stuck.getTrack());
                    default -> { }
                }
            }
        });
    }

    static <T> Disposable ordered(Flux<T> events, ToLongFunction<T> guildId, Consumer<T> handler) {
        // Bound the number of groups while keeping every guild's events in arrival order.
        return events.groupBy(event -> Math.floorMod(guildId.applyAsLong(event), 64))
                .flatMap(group -> group.publishOn(Schedulers.boundedElastic()).doOnNext(event -> {
                    try { handler.accept(event); }
                    catch (RuntimeException error) {
                        LoggerFactory.getLogger(MusicPlaybackEvents.class).error("Playback event processing failed", error);
                    }
                }), 64).subscribe();
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

    @Override public void close() { subscription.dispose(); }
}
