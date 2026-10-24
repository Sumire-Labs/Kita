package com.sumirelabs.kita.music;

import com.sumirelabs.kita.discord.Events;
import com.sumirelabs.kita.discord.SerialActions;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.protocol.v4.VoiceState;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.VoiceServerUpdateEvent;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.object.entity.channel.AudioChannel;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/** Bridges Discord voice credentials to Lavalink without opening a second voice connection. */
final class VoiceConnections implements AutoCloseable {
    private static final class Connection {
        final SerialActions operations = new SerialActions();
        final VoiceHandshake handshake = new VoiceHandshake();
        long requested, connected;
        Sinks.One<Void> ready = Sinks.one();
        synchronized Sinks.One<Void> prepare(long channel) {
            if (requested != channel || connected != channel) {
                if (requested != channel) ready.tryEmitError(new IllegalArgumentException("接続先が変更されました。"));
                requested = channel; ready = Sinks.one();
            }
            if (connected == channel) ready.tryEmitEmpty();
            return ready;
        }
        synchronized void complete(long channel) { connected = channel; if (requested == channel) ready.tryEmitEmpty(); }
        synchronized void disconnected() { connected = 0; }
    }
    private final Map<Long, Connection> connections = new ConcurrentHashMap<>();
    private final Disposable.Composite subscriptions = Disposables.composite();
    private final LavalinkClient lavalink;
    VoiceConnections(GatewayDiscordClient gateway, LavalinkClient lavalink) {
        this.lavalink = lavalink;
        subscriptions.add(Events.listen(gateway, VoiceStateUpdateEvent.class, event -> {
            var voice = event.getCurrent();
            if (!voice.getUserId().equals(gateway.getSelfId())) return Mono.empty();
            long guild = voice.getGuildId().asLong(); var connection = connections.computeIfAbsent(guild, ignored -> new Connection());
            return connection.operations.submit(() -> {
                String channel = voice.getChannelId().map(id -> id.asString()).orElse(null);
                var update = connection.handshake.state(voice.getSessionId(), channel);
                if (channel == null) {
                    connection.disconnected(); var link = lavalink.getLinkIfCached(guild);
                    return link == null ? Mono.empty() : link.destroy().then();
                }
                return apply(guild, connection, update);
            });
        }));
        subscriptions.add(Events.listen(gateway, VoiceServerUpdateEvent.class, event -> {
            long guild = event.getGuildId().asLong(); var connection = connections.computeIfAbsent(guild, ignored -> new Connection());
            return connection.operations.submit(() -> {
                if (event.getEndpoint() == null) connection.disconnected();
                return apply(guild, connection, connection.handshake.server(event.getToken(), event.getEndpoint()));
            });
        }));
    }
    Mono<Void> connect(AudioChannel channel) {
        return Mono.defer(() -> {
            long guild = channel.getGuildId().asLong(), target = channel.getId().asLong();
            var connection = connections.computeIfAbsent(guild, ignored -> new Connection());
            var ready = connection.prepare(target);
            return channel.sendConnectVoiceState(false, false).then(ready.asMono()).timeout(Duration.ofSeconds(20));
        });
    }
    private Mono<Void> apply(long guild, Connection connection, VoiceState state) {
        if (state == null) return Mono.empty();
        return lavalink.getOrCreateLink(guild).createOrUpdatePlayer().setVoiceState(state).timeout(Duration.ofSeconds(15))
                .doOnSuccess(ignored -> connection.complete(Long.parseUnsignedLong(state.getChannelId())))
                .doOnError(error -> connection.handshake.retry()).then();
    }
    void forget(long guild) { var connection = connections.remove(guild); if (connection != null) connection.operations.close(); }
    @Override public void close() { subscriptions.dispose(); connections.keySet().forEach(this::forget); }
}
