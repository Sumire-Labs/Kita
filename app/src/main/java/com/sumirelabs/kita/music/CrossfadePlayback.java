package com.sumirelabs.kita.music;

import com.sumirelabs.kita.common.CrossfadeState;
import com.sumirelabs.kita.settings.SettingsRepository;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.player.Track;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

final class CrossfadePlayback implements AutoCloseable {
    private final CrossfadeApi api;
    private final SettingsRepository settings;
    private final Map<Long, MusicSession> sessions;
    private MusicPlaybackEvents.Starter starter;
    private Disposable timer;
    CrossfadePlayback(LavalinkClient client, Map<Long, MusicSession> sessions, SettingsRepository settings) {
        this(new CrossfadeApi(client), sessions, settings);
    }
    CrossfadePlayback(CrossfadeApi api, Map<Long, MusicSession> sessions, SettingsRepository settings) { this.api = api; this.sessions = sessions; this.settings = settings; }
    void start(MusicPlaybackEvents.Starter starter) {
        this.starter = starter;
        timer = Flux.interval(Duration.ofMillis(500)).onBackpressureDrop().concatMap(tick -> Flux.fromIterable(sessions.entrySet())
                .filter(entry -> entry.getValue().crossfadeActive).flatMap(entry -> entry.getValue().operations.submit(() -> sync(entry.getKey(), entry.getValue()))
                        .onErrorResume(error -> Mono.empty()), 8).then()).subscribe();
    }
    Mono<Void> configure(long guild, MusicSession session) {
        return settings == null ? Mono.empty() : settings.get(guild).doOnNext(config -> {
            synchronized (session) { session.crossfadeSeconds = MusicSettings.seconds(config); }
        }).then();
    }
    boolean eligible(MusicSession session, Track track) {
        return session.crossfadeSeconds > 0 && track != null && track.getInfo().getLength() >= 40 && track.getInfo().isSeekable() && !track.getInfo().isStream()
                && session.queue.loop() != PlaybackQueue.Loop.TRACK;
    }
    Mono<Void> play(long guild, MusicSession session, Track track, String id) {
        return api.request(guild, "", "POST", Map.of("id", id, "encoded", track.getEncoded(), "seconds", session.crossfadeSeconds))
                .doOnNext(state -> { synchronized (session) {
                    session.crossfadeActive = true; session.crossfadeToken = id; session.crossfadeNext = null; session.crossfadeNextToken = "";
                    session.crossfadeState = state; session.crossfadeFailures = 0;
                } }).then().onErrorResume(error -> api.stop(guild, id).onErrorResume(ignored -> Mono.empty()).then(Mono.error(error)));
    }
    Mono<Void> stop(long guild, MusicSession session) {
        String id = session.crossfadeActive ? session.playbackId : "";
        return api.stop(guild, id).doOnSuccess(ignored -> { synchronized (session) { reset(session); } });
    }
    Mono<Void> sync(long guild, MusicSession session) {
        if (!session.crossfadeActive) return Mono.empty();
        String id = session.playbackId;
        return api.request(guild, "/" + id, "GET", null).flatMap(state -> {
            synchronized (session) {
                if (!session.crossfadeActive || !session.playbackId.equals(state.id())) return Mono.empty();
                session.crossfadeState = state; session.crossfadeFailures = 0;
                if (state.token().equals(session.crossfadeNextToken)) {
                    var next = session.queue.upcoming();
                    if (next.isEmpty() || next.getFirst() != session.crossfadeNext) return Mono.error(new IllegalStateException("Crossfade queue changed"));
                    session.queue.next(false); session.crossfadeToken = state.token(); session.crossfadeNext = null; session.crossfadeNextToken = "";
                }
                if (!state.error().isBlank()) session.playbackError = MusicPlaybackEvents.FAILURE;
            }
            if (!session.playbackError.isEmpty()) return Mono.empty();
            if (state.done()) {
                var snapshot = session.queue.snapshot(); var next = session.queue.next(true);
                return starter.start(guild, next, session).doOnError(error -> session.queue.restore(snapshot));
            }
            return prepare(guild, session, state);
        }).onErrorResume(error -> {
            synchronized (session) {
                session.crossfadeFailures++;
                if (session.crossfadeFailures >= 3) session.playbackError = "クロスフェードとの接続に失敗しました。再生ボタンかスキップで再試行してください。";
            }
            return Mono.empty();
        });
    }
    private Mono<Void> prepare(long guild, MusicSession session, CrossfadeState state) {
        Track next; String token;
        synchronized (session) {
            if (session.queue.loop() == PlaybackQueue.Loop.TRACK) {
                if (session.crossfadeNext == null || state.fading()) return Mono.empty();
                return api.request(guild, "/" + state.id() + "/pending", "DELETE", null).doOnNext(result -> {
                    synchronized (session) {
                        if (result.next().isBlank() && result.token().equals(session.crossfadeToken)) {
                            session.crossfadeNext = null; session.crossfadeNextToken = "";
                        }
                    }
                }).then();
            }
            var current = session.queue.current();
            if (current == null || session.crossfadeNext != null || state.fading()
                    || current.getInfo().getLength() - state.position() > session.crossfadeSeconds * 1000L + 20_000) return Mono.empty();
            var tracks = session.queue.upcoming();
            if (tracks.isEmpty()) return Mono.empty(); next = tracks.getFirst();
            if (next.getInfo().getLength() < 40 || !next.getInfo().isSeekable() || next.getInfo().isStream()) return Mono.empty();
            token = UUID.randomUUID().toString();
            // Reserve before HTTP: a timeout does not prove the audio node rejected the request.
            session.crossfadeNext = next; session.crossfadeNextToken = token;
        }
        return api.request(guild, "/" + state.id(), "PUT", Map.of("token", token, "expected", state.token(), "encoded", next.getEncoded()))
                .then().onErrorResume(error -> Mono.empty());
    }
    static void reset(MusicSession session) {
        session.crossfadeActive = false; session.crossfadeNext = null; session.crossfadeNextToken = ""; session.crossfadeState = null;
    }
    @Override public void close() { if (timer != null) timer.dispose(); }
}
