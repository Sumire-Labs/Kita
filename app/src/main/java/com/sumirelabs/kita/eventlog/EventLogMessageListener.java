package com.sumirelabs.kita.eventlog;

import com.sumirelabs.kita.discord.Access;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.MessageUpdateEvent;
import discord4j.core.event.domain.message.MessageDeleteEvent;
import discord4j.core.event.domain.message.MessageBulkDeleteEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.core.object.entity.channel.ThreadChannel;
import discord4j.core.retriever.EntityRetrievalStrategy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class EventLogMessageListener {
    record Location(long parent, long category) {}
    private final EventLogService service;
    public EventLogMessageListener(EventLogService service) { this.service = service; }
    public Mono<Void> create(MessageCreateEvent event) {
        var message = event.getMessage();
        if (message.getGuildId().isEmpty()) return Mono.empty();
        long guild = message.getGuildId().orElseThrow().asLong();
        return service.submit(guild, () -> service.config(guild).filter(EventLogConfig::enabled).flatMap(config ->
                location(message).filter(place -> config.captures(message.getChannelId().asLong(), place.parent, place.category))
                        .flatMap(place -> service.repository().save(MessageSnapshot.of(message, guild, place.parent, place.category,
                                config, System.currentTimeMillis(), service.self()), config.snapshotLimit()))
                        .doOnNext(saved -> { if (!saved) org.slf4j.LoggerFactory.getLogger(getClass()).warn("EventLog snapshot limit reached in guild {}", guild); }).then()));
    }
    public Mono<Void> update(MessageUpdateEvent event) {
        if (event.getGuildId().isEmpty() || !event.isContentChanged() && event.isEmbedsChanged()) return Mono.empty();
        long guild = event.getGuildId().orElseThrow().asLong(), id = event.getMessageId().asLong();
        return service.submit(guild, () -> service.config(guild).filter(config -> config.action(LogAction.MESSAGE_EDIT))
                .flatMap(config -> event.getClient().withRetrievalStrategy(EntityRetrievalStrategy.REST).getMessageById(event.getChannelId(), event.getMessageId())
                        .flatMap(message -> location(message).filter(place -> config.source(message.getChannelId().asLong(), place.parent, place.category))
                                .flatMap(place -> {
                                    var current = MessageSnapshot.of(message, guild, place.parent, place.category, config, System.currentTimeMillis(), service.self());
                                    if (!config.user(current.author(), current.bot(), current.webhook(), service.self())) return Mono.empty();
                                    return service.repository().message(guild, id, System.currentTimeMillis()).map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty())
                                            .flatMap(previous -> {
                                                var old = previous.orElse(null);
                                                if (old != null && old.sameContent(current)) return Mono.empty();
                                                var log = MessageLogEntries.edit(old, current);
                                                return (config.captures(current.channel(), place.parent, place.category)
                                                        ? service.repository().save(current, config.snapshotLimit()).then() : Mono.<Void>empty()).then(service.emit(log));
                                            });
                                }))).then());
    }
    public Mono<Void> deleted(MessageDeleteEvent event) {
        if (event.getGuildId().isEmpty()) return Mono.empty();
        long guild = event.getGuildId().orElseThrow().asLong(), id = event.getMessageId().asLong(), channel = event.getChannelId().asLong();
        return service.submit(guild, () -> service.config(guild).filter(EventLogConfig::enabled).flatMap(config ->
                location(event.getClient(), channel).filter(place -> config.source(channel, place.parent, place.category)).flatMap(place ->
                        service.repository().message(guild, id, System.currentTimeMillis()).map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty())
                                .flatMap(previous -> service.emit(MessageLogEntries.deleted(guild, channel, place.parent, place.category, id, previous.orElse(null)))
                                        .then(service.repository().save(MessageSnapshot.tombstone(guild, channel, id,
                                                System.currentTimeMillis() + config.hours() * 3_600_000L), config.snapshotLimit())).then()))));
    }
    public Mono<Void> bulk(MessageBulkDeleteEvent event) {
        long guild = event.getGuildId().asLong(), channel = event.getChannelId().asLong();
        var ids = event.getMessageIds().stream().map(value -> value.asLong()).limit(100).toList();
        return service.submit(guild, () -> service.config(guild).filter(EventLogConfig::enabled).flatMap(config ->
                location(event.getClient(), channel).filter(place -> config.source(channel, place.parent, place.category)).flatMap(place ->
                        service.repository().messages(guild, ids, System.currentTimeMillis()).collectList()
                                .flatMap(snapshots -> service.emit(MessageLogEntries.bulk(guild, channel, place.parent, place.category, ids, snapshots, config, service.self())))
                                .thenMany(Flux.fromIterable(ids).concatMap(id -> service.repository().save(MessageSnapshot.tombstone(guild, channel, id,
                                        System.currentTimeMillis() + config.hours() * 3_600_000L), config.snapshotLimit()))).then())));
    }
    private static Mono<Location> location(Message message) { return location(message.getClient(), message.getChannelId().asLong()); }
    static Mono<Location> location(discord4j.core.GatewayDiscordClient client, long id) {
        return client.getChannelById(Access.id(id)).ofType(discord4j.core.object.entity.channel.GuildChannel.class).flatMap(channel -> {
            if (channel instanceof ThreadChannel thread) return thread.getParent().map(parent ->
                    new Location(parent.getId().asLong(), category(parent)));
            return Mono.just(new Location(0, category(channel)));
        });
    }
    static long category(discord4j.core.object.entity.channel.GuildChannel channel) {
        return channel instanceof discord4j.core.object.entity.channel.CategorizableChannel value ? value.getCategoryId().map(id -> id.asLong()).orElse(0L) : 0;
    }
}
