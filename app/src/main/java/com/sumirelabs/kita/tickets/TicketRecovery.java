package com.sumirelabs.kita.tickets;

import com.sumirelabs.kita.discord.Access;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.rest.http.client.ClientException;
import java.time.Duration;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class TicketRecovery implements AutoCloseable {
    private final Disposable timer;
    public TicketRecovery(TicketRepository repository, GatewayDiscordClient client) {
        timer = Flux.interval(Duration.ofSeconds(60)).onBackpressureDrop().concatMap(tick -> repository.pending().flatMap(record ->
                client.getGuildById(Access.id(record.guildId())).flatMap(guild -> {
                    if (record.status().equals("opening")) {
                        String topic = "kita:ticket:" + record.guildId() + ":" + record.ownerId();
                        return guild.getChannels().ofType(TextChannel.class).filter(channel -> channel.getTopic().map(topic::equals).orElse(false)).next()
                                .flatMap(channel -> repository.activate(record.guildId(), record.ownerId(), channel.getId().asLong()).thenReturn(true))
                                .switchIfEmpty(repository.release(record.guildId(), record.ownerId())).then();
                    }
                    return client.getChannelById(Access.id(record.channelId())).ofType(TextChannel.class)
                            .flatMap(channel -> channel.getName().startsWith("closed-") ? repository.close(record.guildId(), record.channelId())
                                    : TicketPermissions.change(channel, record, false).then(repository.abortClose(record.guildId(), record.channelId())))
                            .onErrorResume(ClientException.class, error -> error.getStatus().code() == 404
                                    ? repository.close(record.guildId(), record.channelId()) : Mono.error(error));
                }).onErrorResume(error -> Mono.empty()), 8)).subscribe();
    }
    @Override public void close() { timer.dispose(); }
}
