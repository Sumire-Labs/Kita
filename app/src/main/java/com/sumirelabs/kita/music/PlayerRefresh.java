package com.sumirelabs.kita.music;

import com.sumirelabs.kita.discord.Access;
import com.sumirelabs.kita.discord.Ui;
import discord4j.core.GatewayDiscordClient;
import discord4j.rest.http.client.ClientException;
import java.time.Duration;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class PlayerRefresh implements AutoCloseable {
    private final Disposable timer;
    public PlayerRefresh(MusicService music, GatewayDiscordClient client) {
        timer = Flux.interval(Duration.ofSeconds(15)).onBackpressureDrop()
                .concatMap(tick -> Flux.fromIterable(music.sessions().entrySet()).flatMap(entry -> {
                    var session = entry.getValue();
                    return music.voiceOperation(entry.getKey(), () -> Mono.defer(() -> {
                        var panel = session.beginPanelRefresh();
                        if (panel == null) return Mono.empty();
                        if (!session.isCurrentPanel(panel.messageId())) {
                            session.finishPanelRefresh(panel.messageId(), false); return Mono.empty();
                        }
                        return client.getMessageById(Access.id(panel.channelId()), Access.id(panel.messageId()))
                                .flatMap(message -> message.edit(Ui.edit(PlayerPanel.render(music, entry.getKey())))).then()
                                .doOnError(error -> session.finishPanelRefresh(panel.messageId(),
                                        error instanceof ClientException response && response.getStatus().code() == 404))
                                .doFinally(signal -> session.finishPanelRefresh(panel.messageId(), false));
                    })).onErrorResume(error -> Mono.empty());
                }, 16)).subscribe();
    }
    @Override public void close() { timer.dispose(); }
}
