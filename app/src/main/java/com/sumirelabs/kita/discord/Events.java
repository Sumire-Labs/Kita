package com.sumirelabs.kita.discord;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import java.util.function.Function;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

public final class Events {
    private Events() {}
    public static <E extends Event> Disposable listen(GatewayDiscordClient client, Class<E> type, Function<E, ? extends Mono<?>> handler) {
        return client.on(type).flatMap(event -> Mono.defer(() -> handler.apply(event)).onErrorResume(error -> {
            org.slf4j.LoggerFactory.getLogger(Events.class).warn("{} handling failed: {}", type.getSimpleName(), error.getClass().getSimpleName());
            return Mono.empty();
        }), 128).subscribe(ignored -> {}, error -> org.slf4j.LoggerFactory.getLogger(Events.class)
                .warn("{} event stream ended: {}", type.getSimpleName(), error.getClass().getSimpleName()));
    }
}
