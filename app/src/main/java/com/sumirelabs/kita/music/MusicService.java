package com.sumirelabs.kita.music;

import com.sumirelabs.kita.common.PresetFiles;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.NodeOptions;
import dev.arbjerg.lavalink.client.player.LavalinkPlayer;
import dev.arbjerg.lavalink.client.player.Track;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

public final class MusicService implements AutoCloseable {
    private final LavalinkClient client;
    private final Map<Long, MusicSession> sessions = new ConcurrentHashMap<>();
    private final MusicPresets presets;
    private final MusicLoader loader;
    private final MusicPlaybackEvents playbackEvents;
    private final MusicUpdates updates;
    private VoiceConnections voice;

    public MusicService(long userId, String uri, String password, Path directory, String defaultPreset) throws Exception {
        presets = new MusicPresets(PresetFiles.scan(directory), defaultPreset);
        client = new LavalinkClient(userId); updates = new MusicUpdates(client); loader = new MusicLoader(client);
        client.addNode(new NodeOptions.Builder().setName("main").setServerUri(uri).setPassword(password).build());
        playbackEvents = new MusicPlaybackEvents(client, sessions, updates::start);
    }
    public LavalinkClient client() { return client; }
    public void installVoice(discord4j.core.GatewayDiscordClient gateway) { voice = new VoiceConnections(gateway, client); }
    public Mono<Void> connect(discord4j.core.object.entity.channel.AudioChannel channel) { return voice.connect(channel); }
    public MusicSession session(long guild) { return sessions.computeIfAbsent(guild, ignored -> presets.newSession()); }
    public List<PresetFiles.Preset> presets() { return presets.entries(); }
    public LavalinkPlayer player(long guild) {
        var link = client.getLinkIfCached(guild); return link == null ? null : link.getCachedPlayer();
    }
    public <T> Mono<T> voiceOperation(long guild, Supplier<Mono<T>> action) { return session(guild).operations.submit(action); }

    public Mono<QueueAddition> enqueue(long guild, String query) {
        return loader.load(guild, query).flatMap(tracks -> voiceOperation(guild, () -> {
            if (tracks.isEmpty()) return Mono.error(new IllegalArgumentException("再生可能な音源が見つかりませんでした。"));
            var session = session(guild); var snapshot = session.queue.snapshot();
            Track next; boolean needsStart;
            synchronized (session) {
                session.queue.add(tracks);
                needsStart = session.queue.current() == null || !session.playbackError.isEmpty();
                next = needsStart ? session.queue.next(false) : null;
            }
            var first = tracks.getFirst().getInfo();
            return (needsStart ? updates.start(guild, next, session) : Mono.<Void>empty())
                    .doOnError(error -> { synchronized (session) { session.queue.restore(snapshot); } })
                    .thenReturn(new QueueAddition(tracks.size(), first.getTitle(), first.getUri(), MusicTrackDisplay.artwork(first)));
        }));
    }
    public Mono<Void> action(long guild, String action, long value) {
        return voiceOperation(guild, () -> actionNow(guild, action, value));
    }
    Mono<Void> actionNow(long guild, String action, long value) {
        var session = session(guild); var snapshot = session.queue.snapshot();
        Mono<Void> request;
        switch (action) {
            case "skip", "back" -> {
                Track next;
                synchronized (session) { next = action.equals("back") ? session.queue.previous() : session.queue.next(false); }
                request = updates.start(guild, next, session);
            }
            case "stop" -> request = updates.start(guild, null, session).doOnSuccess(ignored -> session.stopped());
            case "loop-track", "loop-queue" -> {
                synchronized (session) { session.queue.toggleLoop(action.equals("loop-track") ? PlaybackQueue.Loop.TRACK : PlaybackQueue.Loop.QUEUE); }
                request = Mono.empty();
            }
            case "pause", "volume", "seek" -> request = updates.action(guild, session, action, value);
            default -> { return Mono.error(new IllegalArgumentException("不明な操作です。")); }
        }
        return request.doOnError(error -> { synchronized (session) { session.queue.restore(snapshot); } });
    }
    public Mono<Void> preset(long guild, String id) {
        return voiceOperation(guild, () -> presetNow(guild, id));
    }
    Mono<Void> presetNow(long guild, String id) {
        return presets.valid(id) ? updates.preset(guild, session(guild), id) : Mono.error(new IllegalArgumentException("不明なプリセットです。"));
    }
    public Mono<Void> forget(long guild) {
        return Mono.defer(() -> {
            var removed = sessions.remove(guild); if (removed != null) removed.operations.close();
            if (voice != null) voice.forget(guild);
            var link = client.getLinkIfCached(guild); return link == null ? Mono.empty() : link.destroy().then();
        });
    }
    public void staying(long guild, long channel) {
        var session = session(guild);
        synchronized (session) { session.staying = true; session.voiceChannel = channel; }
    }
    public boolean staying(long guild) { var session = session(guild); synchronized (session) { return session.staying; } }
    public Mono<Void> endStay(long guild) {
        return Mono.defer(() -> {
            var session = session(guild);
            synchronized (session) {
                session.staying = false; session.voiceChannel = 0;
                session.queue.clear(); session.playbackId = ""; session.playbackError = "";
            }
            var link = client.getLinkIfCached(guild);
            return link == null ? Mono.empty() : link.destroy().timeout(java.time.Duration.ofSeconds(20)).then();
        });
    }
    public Map<Long, MusicSession> sessions() { return Map.copyOf(sessions); }
    @Override public void close() {
        playbackEvents.close(); if (voice != null) voice.close();
        sessions.values().forEach(session -> session.operations.close()); client.close();
    }
}
