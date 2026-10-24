package com.sumirelabs.kita.eventlog;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.AuditLogEntryCreateEvent;
import discord4j.common.util.Snowflake;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class EventLogAuditListener {
    private final EventLogService service;
    public EventLogAuditListener(EventLogService service) { this.service = service; }
    public Mono<Void> audit(AuditLogEntryCreateEvent event) {
        long guild = event.getGuildId().asLong(); var entry = event.getAuditLogEntry();
        var actions = AuditLogEntries.actions(entry);
        if (actions.isEmpty() || entry.getResponsibleUserId().map(id -> id.asLong() == service.self()).orElse(false)) return Mono.empty();
        return service.submit(guild, () -> service.config(guild).filter(config -> actions.stream().anyMatch(action -> config.action(action) && config.target(action) != 0))
                .flatMap(config -> {
            var actor = bot(event.getClient(), entry.getResponsibleUserId().map(id -> id.asLong()).orElse(0L));
            var target = actions.stream().anyMatch(action -> java.util.Set.of(LogAction.Category.MEMBERS, LogAction.Category.MODERATION, LogAction.Category.VOICE)
                    .contains(action.category())) ? bot(event.getClient(), entry.getTargetId().map(id -> id.asLong()).orElse(0L)) : Mono.just(false);
            return Mono.zip(actor, target).flatMap(users -> {
                long source = actions.stream().mapToLong(action -> AuditLogEntries.sourceChannel(entry, action)).filter(id -> id != 0).findFirst().orElse(0);
                Mono<EventLogMessageListener.Location> location = source != 0 ? EventLogMessageListener.location(event.getClient(), source)
                        .onErrorResume(error -> Mono.empty()).defaultIfEmpty(new EventLogMessageListener.Location(0, AuditLogEntries.oldParent(entry)))
                        : Mono.just(new EventLogMessageListener.Location(0, 0));
                return location.flatMap(place -> Flux.fromIterable(actions).concatMap(action -> service.emit(AuditLogEntries.entry(guild, entry, action,
                        users.getT2(), users.getT1(), place.parent(), place.category()))).then());
            });
        }));
    }
    private static Mono<Boolean> bot(GatewayDiscordClient client, long user) {
        if (user == 0) return Mono.just(false);
        return client.getUserById(Snowflake.of(user)).map(value -> value.isBot()).onErrorReturn(true).defaultIfEmpty(true);
    }
}
