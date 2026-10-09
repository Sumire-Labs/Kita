package com.sumirelabs.kita.music;

import com.sumirelabs.kita.common.PresetFiles;
import com.sumirelabs.kita.discord.WorkExecutor;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.NodeOptions;
import dev.arbjerg.lavalink.client.player.LavalinkPlayer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MusicService implements AutoCloseable {
    private final LavalinkClient client;
    private final Map<Long, MusicSession> sessions = new ConcurrentHashMap<>();
    private final MusicPresets presets;
    private final MusicLoader loader;
    private final MusicPlaybackEvents playbackEvents;
    private final MusicUpdates updates;
    public MusicService(long userId, String uri, String password, Path presetDirectory, WorkExecutor worker) throws Exception {
        this(userId, uri, password, presetDirectory, worker, "off");
    }
    public MusicService(long userId, String uri, String password, Path presetDirectory, WorkExecutor worker, String defaultPreset) throws Exception {
        presets = new MusicPresets(PresetFiles.scan(presetDirectory), defaultPreset);
        client = new LavalinkClient(userId);
        updates = new MusicUpdates(client);
        loader = new MusicLoader(client);
        client.addNode(new NodeOptions.Builder().setName("main").setServerUri(uri).setPassword(password).build());
        playbackEvents = new MusicPlaybackEvents(client, sessions, this::start);
    }

    public LavalinkClient client() { return client; }
    public MusicSession session(long guildId) { return sessions.computeIfAbsent(guildId, ignored -> presets.newSession()); }
    public List<PresetFiles.Preset> presets() { return presets.entries(); }
    public LavalinkPlayer player(long guildId) {
        var link = client.getLinkIfCached(guildId);
        return link == null ? null : link.getCachedPlayer();
    }

    public QueueAddition enqueue(long guildId, String query) throws Exception {
        return add(guildId, loader.load(guildId, query));
    }

    private QueueAddition add(long guildId, List<dev.arbjerg.lavalink.client.player.Track> tracks) {
        if (tracks.isEmpty()) throw new IllegalArgumentException("再生可能な音源が見つかりませんでした。");
        var session = session(guildId);
        session.operate(() -> {
            var snapshot = session.queue.snapshot();
            try {
                boolean needsStart;
                dev.arbjerg.lavalink.client.player.Track next;
                synchronized (session) {
                    session.queue.add(tracks);
                    needsStart = session.queue.current() == null || !session.playbackError.isEmpty();
                    next = needsStart ? session.queue.next(false) : null;
                }
                if (needsStart) start(guildId, next, session);
            } catch (RuntimeException error) {
                synchronized (session) { session.queue.restore(snapshot); }
                throw error;
            }
        });
        var first = tracks.getFirst().getInfo();
        return new QueueAddition(tracks.size(), first.getTitle(), first.getUri(), MusicTrackDisplay.artwork(first));
    }

    private void start(long guildId, dev.arbjerg.lavalink.client.player.Track track, MusicSession session) {
        updates.start(guildId, track, session);
    }

    public void action(long guildId, String action, long value) {
        var session = session(guildId);
        session.operate(() -> {
            var snapshot = session.queue.snapshot();
            try {
            switch (action) {
                case "skip", "back" -> {
                    dev.arbjerg.lavalink.client.player.Track next;
                    synchronized (session) { next = action.equals("back") ? session.queue.previous() : session.queue.next(false); }
                    start(guildId, next, session);
                }
                case "stop" -> {
                    start(guildId, null, session);
                    session.stopped();
                }
                case "loop-track", "loop-queue" -> {
                    synchronized (session) {
                        session.queue.toggleLoop(action.equals("loop-track") ? PlaybackQueue.Loop.TRACK : PlaybackQueue.Loop.QUEUE);
                    }
                }
                case "pause", "volume", "seek" -> updates.action(guildId, session, action, value);
                default -> throw new IllegalArgumentException("不明な操作です。");
            }
            } catch (RuntimeException error) {
                synchronized (session) { session.queue.restore(snapshot); }
                throw error;
            }
        });
    }

    public void preset(long guildId, String id) {
        if (!presets.valid(id)) throw new IllegalArgumentException("不明なプリセットです。");
        var session = session(guildId);
        session.operate(() -> updates.preset(guildId, session, id));
    }

    public void forget(long guildId) {
        sessions.remove(guildId);
        var link = client.getLinkIfCached(guildId);
        if (link != null) link.destroy().subscribe();
    }
    public void voiceOperation(long guildId, Runnable action) { session(guildId).operate(action); }
    public void staying(long guildId, long channelId) {
        var session = session(guildId);
        synchronized (session) { session.staying = true; session.voiceChannel = channelId; }
    }
    public boolean staying(long guildId) {
        var session = session(guildId);
        synchronized (session) { return session.staying; }
    }
    public void endStay(long guildId) {
        var session = session(guildId);
        synchronized (session) {
            session.staying = false; session.voiceChannel = 0;
            session.queue.clear(); session.playbackId = ""; session.playbackError = "";
        }
        var link = client.getLinkIfCached(guildId);
        if (link != null) {
            try { link.destroy().block(java.time.Duration.ofSeconds(20)); }
            catch (RuntimeException error) {
                org.slf4j.LoggerFactory.getLogger(MusicService.class).warn("Could not destroy voice player in guild {}", guildId);
            }
        }
    }
    public Map<Long, MusicSession> sessions() { return Map.copyOf(sessions); }
    @Override public void close() { playbackEvents.close(); client.close(); }
}
