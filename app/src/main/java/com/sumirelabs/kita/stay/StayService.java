package com.sumirelabs.kita.stay;

import com.sumirelabs.kita.discord.SerialActions;
import com.sumirelabs.kita.music.MusicService;
import discord4j.core.GatewayDiscordClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class StayService implements AutoCloseable {
    interface Voice {
        <T> Mono<T> operate(long guild, Supplier<Mono<T>> action);
        Mono<Void> connect(long guild, long channel);
        Mono<Void> disconnect(long guild);
        Mono<Void> refresh(long guild, StaySession.View view);
        Mono<Long> maintain(long guild, long channel);
    }
    private final Map<Long, StaySession> sessions = new ConcurrentHashMap<>();
    private final Voice voice;
    private final Clock clock;
    private Disposable timer;

    public StayService(GatewayDiscordClient client, MusicService music) {
        this(new StayVoice(client, music), Clock.systemUTC());
        timer = Flux.interval(Duration.ofSeconds(1)).onBackpressureDrop().concatMap(tick ->
                Flux.fromIterable(sessions.keySet()).flatMap(guild -> expire(guild)
                        .then(tick % 15 == 0 ? maintain(guild) : Mono.empty())
                        .onErrorResume(error -> Mono.empty()), 16)).subscribe();
    }
    StayService(Voice voice, Clock clock) { this.voice = voice; this.clock = clock; }
    private <T> Mono<T> operate(long guild, Function<StaySession, Mono<T>> action) {
        return voice.operate(guild, () -> action.apply(sessions.computeIfAbsent(guild, ignored -> new StaySession())));
    }
    Mono<StaySession.View> toggle(long guild, long channel) {
        return operate(guild, session -> {
            if (session.view().active()) {
                requireChannel(session, channel);
                return voice.disconnect(guild).then(Mono.fromRunnable(session::stop))
                        .then(Mono.defer(() -> voice.refresh(guild, session.view()))).then(Mono.fromSupplier(session::view));
            }
            return voice.connect(guild, channel).then(Mono.fromRunnable(() -> session.start(channel))).then(Mono.fromSupplier(session::view));
        });
    }
    StaySession.View view(long guild) { return sessions.computeIfAbsent(guild, ignored -> new StaySession()).view(); }
    Mono<Void> change(long guild, long channel, String token, Duration duration, boolean stop) {
        return operate(guild, session -> {
            session.require(token); requireChannel(session, channel);
            return (stop ? voice.disconnect(guild).then(Mono.fromRunnable(session::stop))
                    : Mono.<Void>fromRunnable(() -> session.limit(clock.instant(), duration)))
                    .then(Mono.defer(() -> voice.refresh(guild, session.view())));
        });
    }
    Mono<Void> panel(long guild, String token, long channel, long message) {
        return operate(guild, session -> Mono.fromRunnable(() -> { session.require(token); session.panel(channel, message); }));
    }
    void require(long guild, long channel, String token) {
        var session = sessions.computeIfAbsent(guild, ignored -> new StaySession());
        synchronized (session) { session.require(token); requireChannel(session, channel); }
    }
    private static void requireChannel(StaySession session, long channel) {
        if (session.view().voiceChannel() != channel) throw new IllegalArgumentException("Kitaと同じボイスチャンネルに参加してください。");
    }
    Mono<Void> expire(long guild) {
        return operate(guild, session -> {
            if (!session.due(clock.instant())) return Mono.empty();
            return voice.disconnect(guild).then(Mono.fromRunnable(session::stop)).then(Mono.defer(() -> voice.refresh(guild, session.view())));
        });
    }
    Mono<Void> maintain(long guild) {
        return operate(guild, session -> {
            if (!session.view().active() || session.due(clock.instant())) return Mono.empty();
            return voice.maintain(guild, session.view().voiceChannel()).flatMap(channel -> {
                if (channel == session.view().voiceChannel()) return Mono.empty();
                session.move(channel); return voice.refresh(guild, session.view());
            });
        });
    }
    public void forget(long guild) { sessions.remove(guild); }
    @Override public void close() { if (timer != null) timer.dispose(); if (voice instanceof AutoCloseable resource) {
        try { resource.close(); } catch (Exception ignored) { }
    } }
}
