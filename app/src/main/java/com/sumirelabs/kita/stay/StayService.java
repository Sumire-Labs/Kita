package com.sumirelabs.kita.stay;

import com.sumirelabs.kita.discord.WorkExecutor;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.LoggerFactory;

public final class StayService implements AutoCloseable {
    interface Voice {
        void operate(long guild, Runnable action);
        void connect(long guild, long channel);
        void disconnect(long guild);
        void refresh(long guild, StaySession.View view);
        default long maintain(long guild, long channel) { return channel; }
    }
    private final Map<Long, StaySession> sessions = new ConcurrentHashMap<>();
    private final java.util.Set<Long> expiring = ConcurrentHashMap.newKeySet();
    private final Voice voice;
    private final Clock clock;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

    public StayService(java.util.function.LongFunction<net.dv8tion.jda.api.entities.Guild> guilds,
                       com.sumirelabs.kita.music.MusicService music, WorkExecutor worker) {
        this(new StayVoice(guilds, music), Clock.systemUTC());
        timer.scheduleWithFixedDelay(() -> sessions.forEach((guild, session) -> {
            synchronized (session) {
                if (!session.due(clock.instant())) return;
            }
            if (!expiring.add(guild)) return;
            if (!worker.submit(() -> {
                try { expire(guild); }
                finally { expiring.remove(guild); }
            })) expiring.remove(guild);
        }), 1, 1, TimeUnit.SECONDS);
        timer.scheduleWithFixedDelay(() -> sessions.forEach((guild, session) -> {
            synchronized (session) { if (!session.view().active()) return; }
            if (!expiring.add(guild)) return;
            if (!worker.submit(() -> {
                try { maintain(guild); }
                finally { expiring.remove(guild); }
            })) expiring.remove(guild);
        }), 15, 15, TimeUnit.SECONDS);
    }
    StayService(Voice voice, Clock clock) { this.voice = voice; this.clock = clock; }

    private void operate(long guild, Consumer<StaySession> action) {
        var session = sessions.computeIfAbsent(guild, ignored -> new StaySession());
        voice.operate(guild, () -> session.operate(() -> action.accept(session)));
    }
    StaySession.View toggle(long guild, long channel) {
        operate(guild, session -> {
            if (session.view().active()) {
                requireChannel(session, channel);
                voice.disconnect(guild);
                session.stop();
                voice.refresh(guild, session.view());
            } else {
                voice.connect(guild, channel);
                session.start(channel);
            }
        });
        return view(guild);
    }
    StaySession.View view(long guild) {
        var session = sessions.computeIfAbsent(guild, ignored -> new StaySession());
        synchronized (session) { return session.view(); }
    }
    void change(long guild, long channel, String token, Duration duration, boolean stop) {
        operate(guild, session -> {
            session.require(token);
            requireChannel(session, channel);
            if (stop) { voice.disconnect(guild); session.stop(); }
            else session.limit(clock.instant(), duration);
            voice.refresh(guild, session.view());
        });
    }
    void panel(long guild, String token, long channel, long message) {
        operate(guild, session -> { session.require(token); session.panel(channel, message); });
    }
    void require(long guild, long channel, String token) {
        var session = sessions.computeIfAbsent(guild, ignored -> new StaySession());
        synchronized (session) { session.require(token); requireChannel(session, channel); }
    }
    private static void requireChannel(StaySession session, long channel) {
        if (session.view().voiceChannel() != channel) throw new IllegalArgumentException("Kitaと同じボイスチャンネルに参加してください。");
    }
    void expire(long guild) {
        try {
            operate(guild, session -> {
                // Recheck inside the operation: the timer may have been extended or cancelled meanwhile.
                if (!session.due(clock.instant())) return;
                voice.disconnect(guild);
                session.stop();
                voice.refresh(guild, session.view());
            });
        } catch (RuntimeException error) {
            LoggerFactory.getLogger(StayService.class).warn("Stay expiry failed in guild {}; will retry", guild, error);
        }
    }
    void maintain(long guild) {
        try {
            operate(guild, session -> {
                if (!session.view().active() || session.due(clock.instant())) return;
                long channel = voice.maintain(guild, session.view().voiceChannel());
                if (channel != session.view().voiceChannel()) {
                    session.move(channel);
                    voice.refresh(guild, session.view());
                }
            });
        } catch (RuntimeException error) {
            LoggerFactory.getLogger(StayService.class).debug("Stay reconnect failed in guild {}; will retry", guild);
        }
    }
    void forget(long guild) { sessions.remove(guild); }
    @Override public void close() { timer.shutdownNow(); }
}
