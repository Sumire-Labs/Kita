package com.sumirelabs.kita.audio.crossfade;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.io.MessageInput;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sumirelabs.kita.common.CrossfadeState;
import dev.arbjerg.lavalink.api.*;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import kotlinx.serialization.json.Json;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public final class CrossfadeRegistry extends PluginEventHandler {
    private record Key(String session, long guild) {}
    private record Entry(String id, IPlayer player, CrossfadeTrack track) {}
    private final Map<Key, Entry> entries = new HashMap<>();
    private final AudioPlayerManager manager;
    private final CrossfadeSource source;
    private final Map<String, ISocketContext> contexts = new HashMap<>();
    public CrossfadeRegistry(AudioPlayerManager manager, CrossfadeSource source) {
        this.manager = manager; this.source = source;
    }
    public synchronized CrossfadeState start(String session, long guild, String id, String encoded, int seconds) throws Exception {
        if (!id.matches("[a-zA-Z0-9-]{1,64}") || seconds < 1 || seconds > 12) throw bad("Invalid crossfade configuration");
        var key = new Key(session, guild); var old = entries.get(key);
        if (old != null && old.id.equals(id)) return status(session, guild, id);
        if (old == null && entries.size() >= 32) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Crossfade capacity reached");
        var context = contexts.get(session);
        if (context == null || !context.getPlayers().containsKey(guild)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var first = decode(encoded);
        if (first.getDuration() < 40 || first.getInfo().isStream || !first.isSeekable()) throw bad("Track cannot be crossfaded");
        var player = context.getPlayers().get(guild);
        var mixed = new CrossfadeTrack(source, first, encoded, id, seconds);
        mixed.setUserData(Json.Default.parseToJsonElement("{\"kitaPlaybackId\":\"" + id + "\"}"));
        try { player.play(mixed); }
        catch (RuntimeException failure) { mixed.stream.close(); throw failure; }
        if (old != null) old.track.stream.close();
        entries.put(key, new Entry(id, player, mixed)); return status(session, guild, id);
    }
    public synchronized CrossfadeState status(String session, long guild, String id) {
        var entry = entry(session, guild, id); long timeline = entry.track.getPosition();
        var stream = entry.track.stream; var segment = stream.audible(timeline);
        return new CrossfadeState(id, segment.token(), Math.max(0, timeline - segment.start() / 48), stream.nextToken(timeline), stream.fading(timeline),
                stream.done() && entry.player.getTrack() != entry.track, stream.error());
    }
    public synchronized CrossfadeState prepare(String session, long guild, String id, String token, String expected, String encoded) throws Exception {
        if (!token.matches("[a-zA-Z0-9-]{1,64}")) throw bad("Invalid token");
        var entry = entry(session, guild, id);
        if (!entry.track.stream.prepare(decode(encoded), token, expected)) throw new ResponseStatusException(HttpStatus.CONFLICT);
        return status(session, guild, id);
    }
    public synchronized CrossfadeState cancelPending(String session, long guild, String id) {
        var entry = entry(session, guild, id);
        entry.track.stream.cancelBeforeFade(entry.track.getPosition()); return status(session, guild, id);
    }
    public synchronized void stop(String session, long guild, String id) {
        var key = new Key(session, guild); var entry = entries.get(key);
        if (entry == null || !entry.id.equals(id)) return;
        entries.remove(key); if (entry.player.getTrack() == entry.track) entry.player.stop(); entry.track.stream.close();
    }
    private Entry entry(String session, long guild, String id) {
        var entry = entries.get(new Key(session, guild));
        if (entry == null || !entry.id.equals(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return entry;
    }
    private AudioTrack decode(String encoded) throws Exception {
        if (encoded.length() > 32_000) throw bad("Track payload too large");
        var track = manager.decodeTrack(new MessageInput(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))).decodedTrack;
        if (track == null || track instanceof CrossfadeTrack) throw bad("Unsupported track"); return track;
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    @Override public synchronized void onDestroyPlayer(ISocketContext context, IPlayer player) {
        var entry = entries.remove(new Key(context.getSessionId(), player.getGuildId())); if (entry != null) entry.track.stream.close();
    }
    @Override public synchronized void onSocketContextDestroyed(ISocketContext context) {
        contexts.remove(context.getSessionId());
        entries.entrySet().removeIf(entry -> { if (!entry.getKey().session.equals(context.getSessionId())) return false; entry.getValue().track.stream.close(); return true; });
    }
    @Override public synchronized void onWebSocketOpen(ISocketContext context, boolean resumed) { contexts.put(context.getSessionId(), context); }
    @Override public void onNewPlayer(ISocketContext context, IPlayer player) {
        player.getAudioPlayer().addListener(new com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter() {
            @Override public void onTrackEnd(com.sedmelluq.discord.lavaplayer.player.AudioPlayer source, AudioTrack track,
                                             com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason reason) {
                if (reason != com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason.REPLACED
                        && reason != com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason.STOPPED) return;
                synchronized (CrossfadeRegistry.this) {
                    var key = new Key(context.getSessionId(), player.getGuildId()); var entry = entries.get(key);
                    if (entry != null && entry.track == track) { entries.remove(key); entry.track.stream.close(); }
                }
            }
        });
    }
}
