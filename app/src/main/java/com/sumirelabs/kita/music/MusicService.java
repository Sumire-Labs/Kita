package com.sumirelabs.kita.music;

import com.sumirelabs.kita.common.PresetFiles;
import com.sumirelabs.kita.discord.WorkExecutor;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.NodeOptions;
import dev.arbjerg.lavalink.client.player.LavalinkPlayer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MusicService implements AutoCloseable {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private final LavalinkClient client;
    private final Map<Long, MusicSession> sessions = new ConcurrentHashMap<>();
    private final MusicPresets presets;
    private final MusicLoader loader;
    private final MusicPlaybackEvents playbackEvents;
    public MusicService(long userId, String uri, String password, Path presetDirectory, WorkExecutor worker) throws Exception {
        this(userId, uri, password, presetDirectory, worker, "off");
    }
    public MusicService(long userId, String uri, String password, Path presetDirectory, WorkExecutor worker, String defaultPreset) throws Exception {
        presets = new MusicPresets(PresetFiles.scan(presetDirectory), defaultPreset);
        client = new LavalinkClient(userId);
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
        synchronized (session) {
            var snapshot = session.queue.snapshot();
            try {
                session.queue.add(tracks);
                if (session.queue.current() == null || !session.playbackError.isEmpty()) start(guildId, session.queue.next(false), session);
            } catch (RuntimeException error) { session.queue.restore(snapshot); throw error; }
        }
        var first = tracks.getFirst().getInfo();
        return new QueueAddition(tracks.size(), first.getTitle(), first.getUri(), MusicTrackDisplay.artwork(first));
    }

    private void start(long guildId, dev.arbjerg.lavalink.client.player.Track track, MusicSession session) {
        var update = client.getOrCreateLink(guildId).createOrUpdatePlayer().setVolume(session.volume).setPaused(false)
                .setFilters(MusicPresets.filters(session.preset));
        var previousId = session.playbackId;
        var previousError = session.playbackError;
        var playbackId = java.util.UUID.randomUUID().toString();
        if (track == null) update.stopTrack();
        else {
            var playing = track.makeClone();
            playing.setUserData(Map.of("kitaPlaybackId", playbackId));
            update.setTrack(playing);
        }
        session.playbackId = playbackId;
        session.playbackError = "";
        try { update.block(TIMEOUT); }
        catch (RuntimeException error) { session.playbackId = previousId; session.playbackError = previousError; throw error; }
    }

    public void action(long guildId, String action, long value) {
        var session = session(guildId);
        synchronized (session) {
            var snapshot = session.queue.snapshot();
            try {
            switch (action) {
                case "skip" -> start(guildId, session.queue.next(false), session);
                case "back" -> start(guildId, session.queue.previous(), session);
                case "stop" -> { start(guildId, null, session); session.queue.clear(); session.voiceChannel = 0; }
                case "loop" -> session.queue.cycleLoop();
                case "pause" -> {
                    if (!session.playbackError.isEmpty() && session.queue.current() != null) {
                        start(guildId, session.queue.current(), session);
                        break;
                    }
                    var player = player(guildId);
                    if (player == null || player.getTrack() == null) throw new IllegalArgumentException("再生中の曲はありません。");
                    player.setPaused(!player.getPaused()).block(TIMEOUT);
                }
                case "volume" -> {
                    if (value < 0 || value > 100) throw new IllegalArgumentException("音量は0〜100です。");
                    client.getOrCreateLink(guildId).createOrUpdatePlayer().setVolume((int) value).block(TIMEOUT);
                    session.volume = (int) value;
                }
                case "seek" -> {
                    var player = player(guildId);
                    if (player == null || player.getTrack() == null || !player.getTrack().getInfo().isSeekable()) {
                        throw new IllegalArgumentException("この曲はシークできません。");
                    }
                    player.setPosition(Math.clamp(value, 0, player.getTrack().getInfo().getLength())).block(TIMEOUT);
                }
                default -> throw new IllegalArgumentException("不明な操作です。");
            }
            } catch (RuntimeException error) { session.queue.restore(snapshot); throw error; }
        }
    }

    public void preset(long guildId, String id) {
        if (!presets.valid(id)) throw new IllegalArgumentException("不明なプリセットです。");
        var session = session(guildId);
        synchronized (session) {
            var filters = MusicPresets.filters(id);
            client.getOrCreateLink(guildId).createOrUpdatePlayer().setFilters(filters).block(TIMEOUT);
            session.preset = id;
        }
    }

    public void forget(long guildId) {
        sessions.remove(guildId);
        var link = client.getLinkIfCached(guildId);
        if (link != null) link.destroy().subscribe();
    }
    public Map<Long, MusicSession> sessions() { return Map.copyOf(sessions); }
    @Override public void close() { playbackEvents.close(); client.close(); }
}
